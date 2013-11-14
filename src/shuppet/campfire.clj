(ns shuppet.campfire
  (:require
   [environ.core :refer [env]]
   [clj-campfire.core :as cf]))

(def ^:const api-token (env :service-campfire-api-token))
(def ^:const sub-domain (env :service-campfire-sub-domain))
(def ^:dynamic *info-rooms* nil)
(def ^:dynamic *error-rooms* nil)

(def ^:private cf-settings
  {:api-token api-token,
   :ssl true,
   :sub-domain sub-domain})

(defn- room
  "Sets up the room for sending messages"
  [room-name]
  (cf/room-by-name cf-settings room-name))

(defn info
  "Sends message to the info rooms"
  [message]
  (when-not (env :service-campfire-off)
    (dorun (map #(cf/message (room %) message) *info-rooms*))))

(defn- error-messages [{:keys [env app-name title url message status]}]
  (remove nil? [(when title (str title))
                    (when app-name (str "Application: " app-name))
                    (when env (str "Environment: " env))
                    (when url (str "Requested-URL: " url))
                    (when status (str "Status: " status))
                    (when message (str "Message: " message))]))

(defn error
  "Sends error to the specified rooms"
  ([m error-rooms]
      (when-not (env :service-campfire-off)
        (dorun (map (fn [error-room]
                      (dorun (map
                              #(cf/message (room error-room) %)
                              (error-messages m))))
                    error-rooms))))
  ([m]
     (error m *error-rooms*)))
