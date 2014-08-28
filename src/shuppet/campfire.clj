(ns shuppet.campfire
  (:require [clj-campfire.core :as cf]
            [environ.core :refer [env]]))

(def ^:private cf-settings
  {:api-token (env :service-campfire-api-token)
   :ssl true
   :sub-domain  (env :service-campfire-sub-domain)})

(def ^:private campfire-off?
  (Boolean/valueOf (env :service-campfire-off)))

(def ^:private default-info-room
  (env :service-campfire-default-info-room))

(defn room
  "Sets up the room for sending messages"
  [room-name]
  (cf/room-by-name cf-settings room-name))

(defn build-messages
  ""
  [{:keys [app env report]}]
  (cond-> []
          env (conj (str "Environment: " env))
          app (conj (str "App: " app))
          report (into (map :message report))))

(defn info
  "Sends an info message to the Campfire room"
  [report]
  (when (and (seq (:report report)) (not campfire-off?))
    (doseq [message (build-messages report)]
      (cf/message (room default-info-room) message))))

(defn- ignore?
  "Should we ignore the error?"
  [error-map]
  (>= (or (:status error-map) 0) 500))

(defn error
  "Sends an error message to the Campfire room"
  [error-map]
  (when-not (or campfire-off? (ignore? error-map))
    (doseq [error-message error-map]
      (cf/message (room default-info-room) (str (first error-message) " " (second error-message))))))
