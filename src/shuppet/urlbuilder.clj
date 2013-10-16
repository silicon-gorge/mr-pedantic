(ns shuppet.urlbuilder
  (:require
   [clojure.string :refer [join lower-case upper-case]]
   [ring.util.codec :refer [url-encode]]
   [clojure.tools.logging :as log]
   [environ.core :refer [env]])
  (:import
   [java.net URL]
   [javax.crypto Mac]
   [javax.crypto.spec SecretKeySpec]
   [org.apache.commons.codec.binary Base64]
   [org.joda.time DateTime DateTimeZone]))


(def ^:const hmac-sha256-algorithm  "HmacSHA256")
(def ^:const new-line "\n")

(defn- current-time []
  (->>
   (DateTime. (DateTimeZone/UTC))
   (.toString)))

(def ^:const aws-access-key-id (env :service-aws-access-key-id))
(def ^:const aws-access-key-secret (env :service-aws-secret-access-key))

(defn- aws-key []
  (if (empty? aws-access-key-id)
    (System/getenv "AWS_ACCESS_KEY_ID")
    aws-access-key-id))

(defn- aws-secret []
  (if (empty? aws-access-key-secret)
    (System/getenv "AWS_SECRET_KEY")
    aws-access-key-secret))


(def ^:private auth-params
  {"SignatureVersion" "2"
   "AWSAccessKeyId" (aws-key)
   "Timestamp" (current-time)
   "SignatureMethod" hmac-sha256-algorithm})

(defn- bytes [str]
  (.getBytes str "UTF-8"))

(defn- base64 [b]
  (Base64/encodeBase64String b))

(defn- get-mac []
  (let [signing-key (SecretKeySpec. (bytes (aws-secret)) hmac-sha256-algorithm)
        mac (Mac/getInstance hmac-sha256-algorithm)]
    (.init mac signing-key)
    mac))

(defn- calculate-hmac [data]
  (try
    (let [mac (get-mac)
          raw-mac (.doFinal mac (bytes data))]
      (base64 raw-mac))
    (catch Exception e
      (log/error e "Failed to generate HMAC"))))

(defn- get-path [url]
  (let [path (.getPath url)]
    (if (empty? path)
      "/"
      path)))

(defn build-query-string [params]
  (join "&"
    (map (fn [[k v]] (str (url-encode (name k)) "="
                          (url-encode (str v))))
         params)))

(defn- url-to-sign [method host path query-params]
  (str (upper-case method)
       new-line
       host
       new-line
       path
       new-line
       (build-query-string query-params)))

(defn- generate-signature [method uri query-params]
  (let [query-params (into (sorted-map) query-params)
        url (URL. uri)
        host (lower-case (.getHost url))
        path (get-path url)
        data (url-to-sign method host path query-params)]
    (calculate-hmac data)))

(defn build-url
  "Builds a signed url, which can be used with the aws rest api"
  ([method uri params]
      (let [query-params (merge params auth-params)
            signature (generate-signature method uri query-params)
            query-string (build-query-string (merge {"Signature" signature} query-params))]
        (str uri "?" query-string)))
  ([uri params]
     (build-url "get" uri params)))

;(clj-http.client/get (build-url  "https://ec2.eu-west-1.amazonaws.com" {"Version" "2013-10-01" "Action" "CreateSecurityGroup" "GroupName" "test-sg" "GroupDescription" "test description" }) {:throw-exceptions false})
