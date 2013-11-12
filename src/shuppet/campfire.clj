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
  (dorun (map #(cf/message (room %) message) *info-rooms*)))

(defn- error-message [ env app-name title url message status]
  [(str title)
   (str "Application: " app-name)
   (str "Environment: " env)
   (str "Requested-URL: " url)
   (str "Status: " status)
   (str "Message: " message)])

(defn error
  "Sends error to the specified rooms"
  [env app-name {:keys [title url status message]}]
  (dorun (map (fn [error-room]
                (dorun (map
                        #(cf/message (room error-room) %)
                        (error-message env app-name title url message status))))
              *error-rooms*)))
