(ns shuppet.campfire
  (:require
   [environ.core :refer [env]]
   [clj-campfire.core :as cf]))

(def ^:const api-token (env :service-campfire-api-token))
(def ^:const sub-domain (env :service-campfire-sub-domain))
(declare ^:dynamic *rooms*)

(def ^:private cf-settings
  {:api-token api-token,
   :ssl true,
   :sub-domain sub-domain})

(defn- room
  "Sets up the room for sending messages"
  [room-name]
  (cf/room-by-name cf-settings room-name))

(defn send-message
  "Sends the message to the specified rooms"
  [message]
  (dorun (map #(cf/message (room %1) message) *rooms*)))
