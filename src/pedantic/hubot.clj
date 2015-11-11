(ns pedantic.hubot
  (:require [cemerick.url :refer [url]]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.string :as str]
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

(defn- build-message
  "Turn the report into multiple lines of text"
  [{:keys [application environment report]}]
  (let [plural? (not= 1 (count report))
        plural-bit (if plural? "Some Pedantic changes have" "A Pedantic change has")
        messages (str/join "\n" (map :message report))]
    (format "%s been made for *%s* in *%s*\n>>>\n%s" plural-bit application environment messages)))

(defn- ignore?
  "Should we ignore the error?"
  [error-map]
  (>= (or (:status error-map) 0) 500))

(def speak-url
  (str (url hubot-url "hubot" "say")))

(defn- speak
  [room message]
  (let [content (json/generate-string {:room room :message message})
        options {:body content
                 :content-type :json
                 :socket-timeout timeout
                 :throw-exceptions false}]
    (try
      (http/post speak-url options)
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
    (let [message (build-message report)]
      (speak-info message))))

(defn error
  "Sends an error message to Hubot"
  [{:keys [application code environment message status title] :as error}]
  (when (and hubot-on? (not (ignore? error)))
    (speak-error (format "An error occurred while synchronizing configuration for *%s* in *%s*\n>>>\nStatus %d - %s\n%s - %s" application environment status code title message))))
