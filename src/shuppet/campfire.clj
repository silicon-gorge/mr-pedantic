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

(defn- error-message [env app-name title url message status]
  (filter identity [(when title (str title))
                    (when app-name (str "Application: " app-name))
                    (when env (str "Environment: " env))
                    (when url (str "Requested-URL: " url))
                    (when status (str "Status: " status))
                    (when message (str "Message: " message))]))

(defn error
  "Sends error to the specified rooms"
  ([env app-name {:keys [title url status message]} rooms]
     (let [rooms (if rooms rooms *error-rooms*)]
       (dorun (map (fn [error-room]
                     (dorun (map
                             #(cf/message (room error-room) %)
                             (error-message env app-name title url message status))))
                   rooms))))
  ([env app-name details]
     (error env app-name details nil))
  ([details]
     (error nil nil details nil)))
