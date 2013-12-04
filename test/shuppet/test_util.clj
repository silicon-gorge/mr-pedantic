(ns shuppet.test-util
  (:require [clojure.test :refer [deftest]]
            [environ.core :refer [env]]
            [clj-http.client :as client]))

(defn url+ [& suffix] (apply str
                             (format (env :service-url) (env :service-port))
                             suffix))

(defn http-get [url & [params]]
  (client/get (url+  url) (merge {:throw-exceptions false} params)))

(defn http-post [url & [params]]
  (client/post (url+  url) (merge {:throw-exceptions false} params)))

(defmacro lazy-fact-group [& body]
  `(deftest ~'_ ~@body))
