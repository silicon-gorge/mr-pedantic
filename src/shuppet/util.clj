(ns shuppet.util
  (:require
   [clj-time.local :refer [local-now to-local-date-time]]
   [clj-time.format :as format]
   [clojure.string :refer [join upper-case split]]
   [slingshot.slingshot :refer [try+ throw+]]
   [clojure.data.zip.xml :refer [xml1-> text]]))

(def ^:const new-line "\n")

(def rfc2616-format (format/formatter "EEE, dd MMM yyyy HH:mm:ss 'GMT'"))
(defn rfc2616-time
  []
  (format/unparse rfc2616-format (local-now)))

(def v4-format (format/formatter "yyyyMMdd"))
(defn v4-date
  [time]
  (format/unparse v4-format (to-local-date-time time)))

(def ISO8601-format (format/formatter "yyyyMMdd'T'HHmmss'Z'"))
(defn ISO8601-time
  []
  (format/unparse ISO8601-format (local-now)))

(defn current-time
  []
  (.toString (local-now)))

(def ^:const hmac-sha256-algorithm  "HmacSHA256")
(def ^:const hmac-sha1-algorithm  "HmacSHA1")


(defn url-encode
  "The java.net.URLEncoder class encodes for application/x-www-form-urlencoded. (RFC 3986 encoding)"
  [s]
  (-> (java.net.URLEncoder/encode s "UTF-8")
      (.replace "+" "%20")
      (.replace "*" "%2A")
      (.replace "%7E" "~")))

(defn url-decode
  [s]
  (java.net.URLDecoder/decode s "UTF-8"))

(defn values-to-uppercase
  [m]
  (into {} (map (fn [[k v]]
                  [k (upper-case v)])
                m)))

(defn keys-as-string
  [m]
  (into {}
        (for [[k v] m]
          [(name k) v])))

(defn keys-as-keyword
  [m]
  (into {}
        (for [[k v] m]
          [(keyword k) v])))

(defn children-to-map
  "transform children of an xml element to a map"
  [children]
  (apply hash-map (flatten
                   (map (fn [item]
                          [(:tag item) (join (:content item))])
                        children))))

(defn filter-children
  "select xml elements by element name"
  [children key]
  (filter #(= (:tag %) key)
          children))

(defn children-to-maps
  "transform children of an xml element to a list of maps"
  [children]
  (map #(children-to-map (:content %))
       children))

(defn- str-val
  [val]
  (if (coll? val)
    val
    (str val)))

(defn without-nils
  "Remove all keys from a map that have nil/empty values."
  [m]
  (into {} (filter (comp not empty? str-val val) m)))

(defn in?
  "true if seq contains element"
  [seq element]
  (some #(= element %) seq))

(defn compare-config
  "Returns a list of two vectors
   First vector is what is present in the remote config , which are not present in the local config
   and the second vector is those present in the local config, which are not applied to the aws config yet"
  [local remote]
  (list (vec (filter #(not (in? (set local) %)) (set remote)))
        (vec (filter #(not (in? (set remote) %)) (set local)))))

(defn values-tostring
  [m]
  (into {} (map (fn [[k v]] [k (str v)]) m)))

(defn- get-message
  [body content-type]
  (if (= :json content-type)
    (:message body)
    (str (or (xml1-> body :Message text)
             (xml1-> body :Error :Message text)
             (xml1-> body :Errors :Error :Message text)))))

(defn- get-code
  [body content-type]
  (if (= :json content-type)
    (:__type body)
    (str (or (xml1-> body :Code text)
             (xml1-> body :Error :Code text)
             (xml1-> body :Errors :Error :Code text)))))

(defn throw-aws-exception
  ([title action url status body content-type]
      (throw+ {:type ::aws
               :title (str title " request failed while performing the action '" action "'")
               :url url
               :status status
               :message (get-message body content-type)
               :code (get-code body content-type)}))
  ([title action url status body]
     (throw-aws-exception title action url status body :xml)))

(defn sg-rule
  "Creates a Ingress/Egress config for a security group
   http://docs.aws.amazon.com/AWSEC2/latest/APIReference/ApiReference-query-AuthorizeSecurityGroupEgress.html"
  ([protocol from-port to-port ip-ranges]
     (let [record (without-nils {:IpProtocol (str protocol)
                                 :FromPort (str from-port)
                                 :ToPort (str to-port)
                                 :IpRanges ip-ranges})]
       (if (coll? ip-ranges)
         (map #(merge record {:IpRanges %}) ip-ranges)
         record)))
  ([protocol ip-ranges]
     (sg-rule protocol nil nil ip-ranges))
  ([{:keys [IpProtocol FromPort ToPort IpRanges]}]
     (sg-rule IpProtocol FromPort ToPort IpRanges)))

(defn join-policies
  [policy-docs]
  {:Statement (vec (map #(first (:Statement %)) policy-docs))})

(defn- to-vec
  [item]
  (if (string? item) [item] (vec item)))

(defn- create-policy-statement
  ([sid effect principal action resource conditions]
     (let [effect (if (empty? effect) "Allow" effect)]
       (without-nils {:Effect effect
                      :Sid sid
                      :Principal principal
                      :Action (to-vec action)
                      :Resource (to-vec resource)
                      :Conditions conditions}))))

(defn query-string-to-map
  [query]
  (when query (->> (split query #"&")
                   (map #(split % #"="))
                   (map (fn [[k v]] [(keyword (url-encode k)) (when v (url-encode v))]))
                   (into (sorted-map)))))

(defn map-to-query-string
  ([params use-empty-str]
     (join "&"
           (map (fn [[k v]] (if (empty? (str v))
                              (str (name k) (when use-empty-str "="))
                              (str (name k) "=" (str v))))
                params)))
  ([params]
     (map-to-query-string params false)))

(defn create-policy
  "http://docs.aws.amazon.com/IAM/latest/UserGuide/PoliciesOverview.html"
  ([sid effect principal action resource conditions]
     {:Statement [(create-policy-statement sid effect principal action resource conditions)]})
  ([sid action resource]
     (create-policy sid nil nil action resource nil))
  ([action resource]
     (create-policy nil action resource))
  ([{:keys [Sid Effect Principal Action Resource Conditions]}]
     (create-policy Sid Effect Principal Action Resource Conditions)))

(defn- create-grant
  [display-name permission keyword value]
  {:DisplayName display-name
   :Permission permission
   keyword value})

(defn create-acl
  [display-name {:keys [Permission ID URI EmailAddress]}]
  (let [id-grants (map #(create-grant display-name Permission :ID %) (to-vec ID))
        uri-grants (map #(create-grant display-name Permission :URI %) (to-vec URI))
        email-grants (map #(create-grant display-name Permission :EmailAddress %) (to-vec EmailAddress))]
    (vec (distinct (concat id-grants uri-grants email-grants)))))
