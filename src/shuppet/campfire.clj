(ns shuppet.campfire
  (:require
   [environ.core :refer [env]]
   [clj-campfire.core :as cf]))

(def ^:const ^:private api-token (or (env :service-campfire-api-token) "acec839becb8d253b2973f1614d46ce34e640da4"))
(def ^:const ^:private sub-domain (or (env :service-campfire-sub-domain) "nokia-entertainment"))

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
  [message rooms]
  (map #(cf/message (room %1) message) rooms))
