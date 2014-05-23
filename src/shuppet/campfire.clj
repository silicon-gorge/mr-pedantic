(ns shuppet.campfire
  (:require
   [slingshot.slingshot :refer [try+ throw+]]
   [environ.core :as env]
   [clj-campfire.core :as cf]))

(def ^:private cf-settings
  {:api-token (env/env :service-campfire-api-token)
   :ssl true
   :sub-domain  (env/env :service-campfire-sub-domain)})

(defn- room
  "Sets up the room for sending messages"
  [room-name]
  (cf/room-by-name cf-settings room-name))

(defn build-messages [{:keys [report env app]}]
  (cond-> []
          env (conj (str "Environment: " env))
          app (conj (str "App: " app))
          report (into (map :message report))))

(defn info [report]
  (when (and (seq (:report report)) (not (env/env :service-campfire-off)))
    (doseq [message (build-messages report)]
      (cf/message (room (env/env :service-campfire-default-info-room)) message))))

(defn- error-messages [{:keys [env app title url message status]}]
  (remove nil? [(when title (str title))
                (when app (str "Application: " app))
                (when env (str "Environment: " env))
                (when url (str "Requested-URL: " url))
                (when status (str "Status: " status))
                (when message (str "Message: " message))]))

(defn error
  "Sends error to the error rooms"
  [m]
  (when-not (env/env :service-campfire-off)
    (dorun (map (fn [error-room]
                  (dorun (map
                          #(cf/message (room error-room) %)
                          (error-messages m))))
                 (env/env :service-campfire-default-info-room)))))
