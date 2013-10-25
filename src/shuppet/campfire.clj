(ns shuppet.campfire
  (:require
   [environ.core :refer [env]]
   [clj-campfire.core :as cf]))

(def ^:const api-token (or (env :service-campfire-api-token) "acec839becb8d253b2973f1614d46ce34e640da4"))
(def ^:const sub-domain (or (env :service-campfire-sub-domain) "nokia-entertainment"))

(def ^:dynamic *rooms* #{})

(defn set-rooms!
  [rooms]
  (alter-var-root #'*rooms* (fn [_] (set rooms))))

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
  ([message rooms]
     (map #(cf/message (room %1) message) rooms))
  ([message]
     (send-message message *rooms*)))

;(send-message "I win...again" #{"Shuppet-test"})
