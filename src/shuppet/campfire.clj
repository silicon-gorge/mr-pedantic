(ns shuppet.campfire
  (:require [slingshot.slingshot :refer [try+ throw+]]
            [environ.core :as env]
            [clj-campfire.core :as cf]))

(def ^:private cf-settings
  {:api-token (env/env :service-campfire-api-token)
   :ssl true
   :sub-domain  (env/env :service-campfire-sub-domain)})

(defn room
  "Sets up the room for sending messages"
  [room-name]
  (cf/room-by-name cf-settings room-name))

(defn build-messages
  [{:keys [report env app]}]
  (cond-> []
          env (conj (str "Environment: " env))
          app (conj (str "App: " app))
          report (into (map :message report))))

(defn info
  [report]
  (when (and (seq (:report report)) (not (env/env :service-campfire-off)))
    (doseq [message (build-messages report)]
      (cf/message (room (env/env :service-campfire-default-info-room)) message))))

(defn- ignore?
  [error-map]
  (>= (or (:status error-map) 0) 500))

(defn error
  "Sends error to campfire room"
  [error-map]
  (when-not (or (env/env :service-campfire-off) (ignore? error-map))
    (doseq [error-message error-map]
      (cf/message (room (env/env :service-campfire-default-info-room)) (str (first error-message) " " (second error-message))))))
