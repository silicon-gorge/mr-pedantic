(ns shuppet.signature
  (:require
   [shuppet.util :refer :all]
   [clojure.string :refer [join lower-case upper-case trim split]]
   [clojure.tools.logging :as log]
   [environ.core :refer [env]]
   [clj-http.client :as client])
  (:import
   [java.net URL]
   [java.net URLEncoder]
   [javax.crypto Mac]
   [javax.crypto.spec SecretKeySpec]
   [java.security MessageDigest]
   [org.apache.commons.codec.binary Base64]
   [org.apache.commons.codec.binary Hex]))

(def ^:const aws-access-key-id (env :service-aws-access-key-id))
(def ^:const aws-access-key-secret (env :service-aws-secret-access-key))

(def ^:const v4-algorithm "AWS4-HMAC-SHA256")
(def ^:const default-region "us-east-1")

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

(defn- hex
  [b]
  (Hex/encodeHexString b))

(defn- get-mac-sha256
  ([secret]
     (let [signing-key (SecretKeySpec. secret hmac-sha256-algorithm)
           mac (Mac/getInstance hmac-sha256-algorithm)]
       (.init mac signing-key)
       mac))
  ([]
     (get-mac-sha256 (to-bytes (aws-secret)))))

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
    (.doFinal sha (to-bytes data))
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
    (base64 (calculate-hmac data @mac-sha256))))

(defn s3-header
  "Gets the S3 Authorisation header for the given url"
  [url]
  {"Authorization" (str "AWS " (aws-key) ":" (base64 (calculate-hmac url @mac-sha1)))})

(defn- sha-256
  [str]
  (let [md (MessageDigest/getInstance "SHA-256")]
    (.update md (to-bytes str))
    (.digest md)))

(defn v2-url
  "Builds a v2 signed url, which can be used with the aws api"
  ([method uri opts]
     (let [query-params (merge opts (v2-auth-params))
           signature (generate-v2-signature (name method) uri query-params)
           query-string (build-query-string
                         (into (sorted-map)
                               (merge {"Signature" signature} query-params)))]
       (str uri "?" query-string)))
  ([uri params]
     (v2-url :GET uri params)))

(defn- parse-url
  [uri]
  (let [url (URL. uri)
        host (.getHost url)
        protocol (.getProtocol url)
        path (.getPath url)]
    {:uri  (str protocol "://" host "/")
     :host host
     :path (if (empty? path) "/" path)
     :query (.getQuery url)}))

(defn- v4-canon-headers
  [headers]
  (str (join new-line (map (fn [[k v]]
                             (str (lower-case (name k)) ":" (trim v))) headers)) new-line))

(defn- signed-headers
  [headers]
  (join ";"
        (map #(lower-case (name %)) (keys headers))))

(defn- v4-canon-request
  [method path query headers body]
  (let [body (if body body "")
        q-string (map-to-query-string (query-string-to-map query) true)]
    (->
     (str (upper-case (name method)) new-line
          path new-line
          q-string new-line
          (v4-canon-headers headers) new-line
          (signed-headers headers) new-line
          (hex (sha-256 body)))
     (sha-256)
     (hex))))

(defn- credential-scope
  [host time]
  (let [endpoint (split (subs host 0 (.indexOf  host ".amazonaws.com")) #"[.]")
        region (second endpoint)
        region (if region region default-region)]
    (str (v4-date time)
         "/"
         (lower-case region)
         "/"
         (lower-case (first endpoint))
         "/"
         "aws4_request")))

(defn- v4-string-to-sign
  [host time v4-request]
  (str v4-algorithm new-line
       time new-line
       (credential-scope host time) new-line
       v4-request))

(defn- signing-key
  [host time]
  (let [parts (split (credential-scope host time) #"/")]
    (->>
     (calculate-hmac (nth parts 0) (get-mac-sha256 (to-bytes (str "AWS4" aws-access-key-secret))))
     (get-mac-sha256)
     (calculate-hmac (nth parts 1))
     (get-mac-sha256)
     (calculate-hmac (nth parts 2))
     (get-mac-sha256)
     (calculate-hmac (nth parts 3)))))

(defn v4-auth-headers
  ([method uri headers body]
     (let [opts (parse-url uri)
           host (:host opts)
           time (ISO8601-time)
           headers (into (sorted-map) (merge (keys-as-keyword headers) {:x-amz-date time
                                                                        :host host}))
           string-to-sign (->>
                           (v4-canon-request method (:path opts) (:query opts) headers body)
                           (v4-string-to-sign host time))
           signature (hex (calculate-hmac string-to-sign (get-mac-sha256 (signing-key host time))))]
       (merge (keys-as-string headers) {"Authorization" (str v4-algorithm
                                                             " Credential="
                                                             (aws-key)
                                                             "/"
                                                             (credential-scope host time)
                                                             ", SignedHeaders="
                                                             (signed-headers headers)
                                                             ", Signature="
                                                             signature)})))
  ([method uri]
     (v4-auth-headers method uri nil nil))
  ([{:keys [method url headers body]}]
     (let [method (if method method :get)]
       (v4-auth-headers method url headers body))))
