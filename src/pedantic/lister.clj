(ns pedantic.lister
  (:require [cheshire.core :as json]
            [cemerick.url :refer [url]]
            [clj-http.client :as http]
            [environ.core :refer [env]]))

(def lister-url
  "The URL where Lister is running."
  (url (env :lister-baseurl)))

(defn applications-url
  "The URL where we can get information about the applications Lister knows
   about."
  []
  (str (url lister-url "applications")))

(defn application-url
  "The URL where we can get information about a specific application."
  [application-name]
  (str (url lister-url "applications" application-name)))

(defn environments-url
  "The URL where we can get information about the environments Lister knows about."
  []
  (str (url lister-url "environments")))

(defn environment-url
  "The URL where we can get information about a specific environment."
  [environment-name]
  (str (url lister-url "environments" environment-name)))

(defn application
  "Gets a particular application. Returns `nil` if the application doesn't
   exist."
  [application-name]
  (let [{:keys [body status]} (http/get (application-url application-name))]
    (if (= status 200)
      (:metadata (json/parse-string body true)))))

(defn applications
  "Gets all applications Lister knows about."
  []
  (let [{:keys [body status]} (http/get (applications-url))]
    (if (= status 200)
      (:applications (json/parse-string body true)))))

(defn environment
  "Gets a particular environment. Returns `nil` if the environment doesn't exist"
  [environment-name]
  (let [{:keys [body status]} (http/get (environment-url (name environment-name)))]
    (when (= status 200)
      (json/parse-string body true))))

(defn environments
  "Gets all environments Lister knows about."
  []
  (let [{:keys [body status]} (http/get (environments-url))]
    (when (= status 200)
      (apply sorted-set (:environments (json/parse-string body true))))))
