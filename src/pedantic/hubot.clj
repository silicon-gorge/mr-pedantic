(ns pedantic.hubot
  (:require [cemerick.url :refer [url]]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]))

(def ^:private hubot-on?
  (Boolean/valueOf (env :hubot-on)))

(def ^:private info-room
  (env :hubot-info-room))

(def ^:private error-room
  (env :hubot-error-room))

(def ^:private timeout
  5000)

(def ^:private hubot-url
  (url (env :hubot-url)))

(defn- build-messages
  "Turn the report into multiple lines of text"
  [{:keys [application environment report]}]
  (cond-> []
          environment (conj (str "Environment: " environment))
          application (conj (str "App: " application))
          report (into (map :message report))))

(defn- ignore?
  "Should we ignore the error?"
  [error-map]
  (>= (or (:status error-map) 0) 500))

(def speak-url
  (str (url hubot-url "hubot" "say")))

(defn- speak
  [room message]
  (let [content (json/generate-string {:room room :message message})
        post-body {:content-type :json :body content :socket-timeout timeout}]
    (try
      (http/post speak-url post-body)
      nil
      (catch Exception e
        (log/warn e "Failed while making Hubot talk")))))

(defn- speak-error
  [message]
  (speak error-room message))

(defn- speak-info
  [message]
  (speak info-room message))

(defn info
  "Sends an info message to Hubot"
  [report]
  (when (and hubot-on? (seq (:report report)))
    (doseq [message (build-messages report)]
      (speak-info message))))

(defn error
  "Sends an error message to Hubot"
  [error-map]
  (when (and hubot-on? (not (ignore? error-map)))
    (doseq [error-message error-map]
      (speak-error (str (first error-message) " " (second error-message))))))
