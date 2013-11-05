(ns shuppet.s3
  (:require
   [shuppet.signature :refer [s3-header]]
   [shuppet.util :refer :all]
   [environ.core :refer [env]]
   [slingshot.slingshot :refer [try+ throw+]]
   [clojure.string :refer [split join upper-case trim lower-case]]
   [clj-http.client :as client]
   [clojure.data.zip.xml :refer [xml1-> text]]
   [clojure.xml :as xml]
   [clojure.zip :as zip]
   [clojure.data.json :refer [write-str read-str]]
   [clojure.data.xml :refer [element sexp-as-element emit-str]]
   [clojure.walk :refer [keywordize-keys]]
   [clojure.tools.logging :as log])
  (:import
   [java.net URL]))

;Need to supply this when we use temporary IAM roles
(def ^:dynamic *session-token* nil)

(defn xml-to-map [xml-string]
  (zip/xml-zip (xml/parse (java.io.ByteArrayInputStream. (.getBytes xml-string)))))

(def ^:const s3-url (env :service-aws-s3-url))
(def ^:const s3-valid-locations #{:eu :eu-west-1 :eu-west-2 :ap-southeast-1
                                  :ap-southeast-2 :ap-northeast-1 :sa-east-1})

(def location ;todo if the bucket name contains dots in it
  (let [host (-> s3-url
                 URL.
                 .getHost)
        location (if (empty? (re-find #"^s3.*.amazonaws.com" host))
                   (second (split host #"[.]"))
                   (first (split host #"[.]")))
        s3-location (if (= "s3" location) "" (lower-case (subs location 3)))]

    (if (contains? s3-valid-locations (keyword s3-location))
      s3-location
      "")))

(def create-bucket-body
  (emit-str (sexp-as-element
             [:CreateBucketConfiguration {}
              [:LocationConstraint location]])))

(def s3-sub-resources #{:versioning :location :acl :torrent
                        :lifecycle :versionId :logging :notification
                        :partNumber :policy :requestPayment :uploadId
                        :uploads :versions :website})

(defn- s3-path
  [host path]
  (if (empty? (re-find #"^s3.*.amazonaws.com" host))
    (str "/" (first (split host #".s3")) path)
    path))

(defn- query-string-to-map
  [query]
  (->> (split query #"&")
       (map #(split % #"="))
       (map (fn [[k v]] [(keyword k) v]))
       (into {})))

(defn- build-query-string
  [params]
  (join "&"
        (map (fn [[k v]] (if (empty? v)
                          (str (name k))
                          (str (name k) "=" v)))
             params)))

(defn- add-query
  [path q-map]
  (let [opts  (into (sorted-map) (map #(if (contains? q-map %) {% (% q-map)}) s3-sub-resources))]
    (if (empty? opts)
      path
      (str path "?" (build-query-string opts)))))

(defn- canon-path
  [url]
  (let [url (URL. url)
        path (s3-path (.getHost url) (.getPath url))
        q-string (.getQuery url)
        path (if (empty? q-string) path
                 (add-query path (query-string-to-map q-string)))]
    (if (empty? path) "/" path)))

(defn- url-to-sign
  [method c-md5 c-type c-headers c-path]
  (str (upper-case (name method)) new-line
       c-md5 new-line
       c-type new-line
       new-line
       c-headers
       c-path))

(defn- get-amz-headers
  [headers]
  (apply dissoc headers (keep #(-> % key name (.startsWith "x-amz-") (if nil (key %))) headers)))

(defn- amz-headers-str
  [headers]
  (let [amz-headers (get-amz-headers headers)
        headers-str (join new-line (map #(str (lower-case (name (key %))) ":" (trim (val %))) amz-headers))]
    (if (empty? headers-str) nil (str headers-str new-line))))

(defn- auth-header
  [{:keys [url method content-md5 content-type headers]}]
  (let [headers-str (amz-headers-str (without-nils headers))
        uri (url-to-sign method content-md5 content-type headers-str (canon-path url))]
    (s3-header uri)))

(defn- get-request
  [url headers]
  (let [response (client/get url {:headers  headers
                                  :throw-exceptions false} )
        status (:status response)
        body (:body response)
        content-type (get-in response [:headers "content-type"])]
    (condp = status
      200 body
      404 nil
      301 nil ;tocheck
      (throw-aws-exception "S3" "GET" url status (xml-to-map (:body response))))))

(defn- delete-request
  [url headers]
  (client/delete url {:headers  headers
                      :as :xml
                      :throw-exceptions false}))

(defn- put-request
  ([url headers body content-type]
     (let [type (keyword (second (split content-type  #"\/")))
           response (client/put url {:headers  headers
                                     :as type
                                     :content-type content-type
                                     :body body
                                     :throw-exceptions false})
           status (:status response)]
       (when (and (not= 204 status)
                  (not= 200 status))
         (throw-aws-exception "S3" "PUT" url status (xml-to-map (:body response))))))
  ([url headers body]
     (put-request url headers body "application/xml")))

(defn- request-header
  ([url date method content-type]
     (let [headers (without-nils {"x-amz-date" date
                                  "x-amz-security-token" *session-token*})]
       (merge headers (auth-header {:url url
                                    :headers headers
                                    :method method
                                    :content-type content-type}))))
  ([url date]
     (request-header url date :get nil))
  ([url date method]
     (request-header url date method "application/xml")))

(defn- process
  ([action url body]
     (let [date (rfc2616-time)]
       (condp = (keyword action)
         :CreateBucket  (put-request url (request-header url date :put) body "application/xml")
         :ListBucket (get-request url (request-header url date))
         :DeleteBucket (delete-request url (request-header url date :delete nil))
         :GetBucketPolicy (get-request url (request-header url date))
         :CreateBucketPolicy (put-request url (request-header url date :put "application/json") body "application/json")
         :DeleteBucketPolicy (delete-request url (request-header url date :delete nil))
         :GetBucketAcl (get-request url (request-header url date))
         :CreateBucketAcl (put-request url (request-header url date :put) body "application/xml"))))
  ([action url]
     (process action url nil)))

(defn- create-policy-stmt
  [opts]
  (join-policies (map create-policy opts)))

(defn- vec-to-string
  [[key val]]
  {key (if (and (vector? val) (= 1 (count val))) (first val) val)})

(defn- to-amazon-format
  [opts]
  (let [item (into {} (map vec-to-string opts))]
    (assoc item :Principal (into {} (map vec-to-string (:Principal item))))))

(defn- get-remote-policy
  [url]
  (let [p-response (process :GetBucketPolicy url)]
    (if-not (empty? p-response)
      (get (keywordize-keys (read-str p-response)) :Statement)
      [])))

(defn- create-id-grant
  [display-name permission id]
  (element :Grant {}
           (element :Grantee {:xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
                              :xsi:type "CanonicalUser"}
                    (element :ID {} id)
                    (element :DisplayName {} display-name))
           (element :Permission {} permission)))

(defn- create-uri-grant
  [permission uri]
  (element :Grant {}
           (element :Grantee {:xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
                              :xsi:type "Group"}
                    (element :URI {} uri))
           (element :Permission {} permission)))

(defn- create-email-grant
  [permission email]
  (element :Grant {}
           (element :Grantee {:xsi:type "AmazonCustomerByEmail"
                              :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"}
                    (element :EmailAddress {} email))
           (element :Permission {} permission)))

(defn- create-grants
  [{:keys [DisplayName Permission] :as opts}]
  (cond
   (not (empty? (:ID opts))) (create-id-grant DisplayName Permission (:ID opts))
   (not (empty? (:URI opts))) (create-uri-grant Permission (:URI opts))
   (not (empty? (:EmailAddress opts))) (create-email-grant Permission (:EmailAddress opts))))

(defn- owner-xml
  [{:keys [ID DisplayName]}]
  (element :Owner {}
           (element :ID {} ID)
           (element :DisplayName {} DisplayName)))

(defn- put-acl-body
  [owner acls]
  (element :AccessControlPolicy {:xmlns "http://s3.amazonaws.com/doc/2006-03-01/"}
           owner
           (element :AccessControlList {}
                    acls)))

(defn- put-acl
  [{:keys [Owner]} acls url]
  (let [owner (owner-xml Owner)
        acls-xml (map create-grants acls)
        body (put-acl-body owner acls-xml)]
    (process :CreateBucketAcl url (emit-str body))))

(defn- local-acls
  [{:keys [Owner AccessControlList]}]
  (reduce concat (map #(create-acl (:DisplayName Owner) %) AccessControlList)))

(defn- ensure-acl
  [{:keys [BucketName AccessControlPolicy]}]
  (Thread/sleep 1000);Bucket creation can be slow
  (let [url (str s3-url "/" BucketName "/?acl")
        get-response (process :GetBucketAcl url)
        local-config (local-acls AccessControlPolicy)]
    (if (empty? get-response) ;Not doing the comparison here as is not a requirement now.
      (put-acl AccessControlPolicy local-config url))))

(defn- ensure-policy
  [{:keys [BucketName Id Statement]}]
  (Thread/sleep 1000) ;Bucket creation can be slow
  (let [url (str s3-url  "/" BucketName "/?policy")
        l-config (create-policy-stmt Statement)
        remote (get-remote-policy url)
        local (vec (map to-amazon-format (get l-config :Statement)))
        [r l] (compare-config local remote)]
    (when-not (empty? l)
      (process :CreateBucketPolicy url (write-str (without-nils (merge l-config {:Id Id})))))
    (when-not (empty? r)
      (when (empty? l)
        (process :DeleteBucketPolicy url)))))

(defn- ensure-s3
  [{:keys [BucketName] :as opts}]
  (let [url (str s3-url  "/" BucketName)
        get-response (process :ListBucket url)]
    (when (empty? get-response)
      (process :CreateBucket url create-bucket-body))
    (when (:Statement opts)
      (ensure-policy opts))
    (when (:AccessControlPolicy opts)
      (ensure-acl opts))))

(defn ensure-s3s
  [{:keys [S3]}]
  (doseq [s3 S3]
    (ensure-s3 s3)))

(defn- delete-s3
  [{:keys [BucketName]}]
  (let [url (str s3-url "/" BucketName)]
    (process :DeleteBucket url)))

(defn delete-s3s
  [{:keys [S3]}]
  (doseq [s3 S3]
    (delete-s3 s3)))
