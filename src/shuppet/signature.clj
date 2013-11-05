(ns shuppet.signature
  (:require
   [shuppet.util :refer :all]
   [clojure.string :refer [join lower-case upper-case]]
   [clojure.tools.logging :as log]
   [environ.core :refer [env]])
  (:import
   [java.net URL]
   [java.net URLEncoder]
   [javax.crypto Mac]
   [javax.crypto.spec SecretKeySpec]
   [org.apache.commons.codec.binary Base64]))

(def ^:const aws-access-key-id (env :service-aws-access-key-id))
(def ^:const aws-access-key-secret (env :service-aws-secret-access-key))

(defn- aws-key
  []
  (if (empty? aws-access-key-id)
    (System/getenv "AWS_ACCESS_KEY_ID")
    aws-access-key-id))

(defn- aws-secret
  []
  (if (empty? aws-access-key-secret)
    (System/getenv "AWS_SECRET_KEY")
    aws-access-key-secret))

(defn- v2-auth-params
  []
  {"SignatureVersion" "2"
   "AWSAccessKeyId" (aws-key)
   "Timestamp" (current-time)
   "SignatureMethod" hmac-sha256-algorithm})

(defn- to-bytes
  [str]
  (.getBytes str "UTF-8"))

(defn- base64
  [b]
  (Base64/encodeBase64String b))

(defn- get-mac-sha256
  []
  (let [signing-key (SecretKeySpec. (to-bytes (aws-secret)) hmac-sha256-algorithm)
        mac (Mac/getInstance hmac-sha256-algorithm)]
    (.init mac signing-key)
    mac))

(defn- get-mac-sha1
  []
  (let [signing-key (SecretKeySpec. (to-bytes (aws-secret)) hmac-sha1-algorithm)
        mac (Mac/getInstance hmac-sha1-algorithm)]
    (.init mac signing-key)
    mac))

(def ^:private mac-sha256 (delay (get-mac-sha256)))
(def ^:private mac-sha1 (delay (get-mac-sha1)))

(defn- calculate-hmac
  [data sha]
  (try
    (let [raw-mac (.doFinal sha (to-bytes data))]
      (base64 raw-mac))
    (catch Exception e
      (log/error e "Failed to generate HMAC"))))

(defn- get-path
  [url]
  (let [path (.getPath url)]
    (if (empty? path)
      "/"
      path)))

(defn- build-query-string
  [params]
  (join "&"
        (map (fn [[k v]] (str (url-encode (name k)) "="
                             (url-encode (str v))))
             params)))

(defn- v2-url-to-sign
  [method host path query-params]
  (str (upper-case method)
       new-line
       host
       new-line
       path
       new-line
       (build-query-string query-params)))

(defn- generate-v2-signature
  [method uri query-params]
  (let [query-params (into (sorted-map) query-params)
        url (URL. uri)
        host (lower-case (.getHost url))
        path (get-path url)
        data (v2-url-to-sign method host path query-params)]
    (calculate-hmac data @mac-sha256)))

(defn s3-header
  "Gets the S3 Authorisation header for the given url"
  [url]
  {"Authorization" (str "AWS " (aws-key) ":" (calculate-hmac url @mac-sha1))})

(defn v2-url
  "Builds a v2 signed url, which can be used with the aws api"
  ([method uri opts]
     (let [query-params (merge opts (v2-auth-params))
           signature (generate-v2-signature (name method) uri query-params)
           query-string (build-query-string (into (sorted-map) (merge {"Signature" signature} query-params)))]
       (str uri "?" query-string)))
  ([uri params]
     (v2-url :GET uri params)))
