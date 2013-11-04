(ns shuppet.util
  (:require
   [clj-time.local :refer [local-now]]
   [clj-time.format :as format]
   [clojure.string :refer [join upper-case]]))

(def ^:const new-line "\n")

(def rfc2616-format (format/formatter "EEE, dd MMM yyyy HH:mm:ss 'GMT'"))
(defn rfc2616-time
  []
  (format/unparse rfc2616-format (local-now)))

(defn current-time
  []
  (.toString (local-now)))

(def ^:const hmac-sha256-algorithm  "HmacSHA256")


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

(defn values-to-uppercase [m]
  (into {} (map (fn [[k v]]
               [k (upper-case v)])
             m)))

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

(defn values-tostring [m]
  (into {} (map (fn [[k v]] [k (str v)]) m)))

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

(defn- create-policy-statement
  ([sid effect services actions resources condition]
     (let [effect (if (empty? effect) "Allow" effect)
           services (if (string? services) [services] (vec services))
           actions (if (string? actions) [actions] (vec actions))
           resources (if (string? resources) [resources] (vec resources))]
       (without-nils {:Effect effect
                      :Sid sid
                      :Principal (without-nils {:Service services})
                      :Action actions
                      :Resource resources
                      :Conditions condition}))))

(defn create-policy
  "http://docs.aws.amazon.com/IAM/latest/UserGuide/PoliciesOverview.html"
  ([sid effect services actions resources condition]
     {:Statement [(create-policy-statement sid effect services actions resources condition)]})
  ([sid actions resources]
     (create-policy sid nil nil actions resources nil))
  ([actions resources]
     (create-policy nil actions resources))
  ([{:keys [Sid Effect Service Action Resource Condition]}]
     (create-policy Sid Effect Service Action Resource Condition)))
