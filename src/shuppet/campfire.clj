(ns shuppet.campfire
  (:require [clj-campfire.core :as cf]
            [environ.core :refer [env]]))

(def ^:private cf-settings
  {:api-token (env :service-campfire-api-token)
   :ssl true
   :sub-domain  (env :service-campfire-sub-domain)})

(def ^:private campfire-on?
  (Boolean/valueOf (env :service-campfire-on)))

(def ^:private default-info-room
  (env :service-campfire-default-info-room))

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

(defn room
  "Gets the room ID by name"
  [room-name]
  (cf/room-by-name cf-settings room-name))

(defn info
  "Sends an info message to the Campfire room"
  [report]
  (when (and campfire-on? (seq (:report report)))
    (doseq [message (build-messages report)]
      (cf/message (room default-info-room) message))))

(defn error
  "Sends an error message to the Campfire room"
  [error-map]
  (when (and campfire-on? (not (ignore? error-map)))
    (doseq [error-message error-map]
      (cf/message (room default-info-room) (str (first error-message) " " (second error-message))))))
