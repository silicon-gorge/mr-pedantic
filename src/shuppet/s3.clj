(ns shuppet.s3
  (:require
   [shuppet.aws :refer [iam-request iam-post-request]]
   [shuppet.util :refer :all]
   [slingshot.slingshot :refer [try+ throw+]]
   [clojure.string :refer [split join upper-case]])
  (:import
   [java.net URL]))

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
        path (add-query path (query-string-to-map (.getQuery url)))]
    (if (empty? path) "/" path)))

(defn- url-to-sign
  [method c-md5 c-type c-headers c-path]
  (str (upper-case (name method)) new-line
       c-md5 new-line
       c-type new-line
       (rfc2616-time) new-line
       c-headers
       c-path))

(defn- build-url
  [{:keys [url method content-md5 content-type headers]}]
  (let [method (if (empty? method) :get method )
        uri (url-to-sign method content-md5 content-type headers (canon-path url))]
    (prn uri)
    uri))



                                        ;(canon-path "http://john.s3sdfsdfsdf.amazonaws.com/?versioning=2&acl")

                                      ; (build-url {:url "http://johnsmith.s3.amazonaws.com/test/?acl&tt=yy"})
