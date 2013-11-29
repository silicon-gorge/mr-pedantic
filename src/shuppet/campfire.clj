(ns shuppet.campfire
  (:require
   [shuppet.util :refer [to-vec]]
   [slingshot.slingshot :refer [try+ throw+]]
   [environ.core :refer [env]]
   [clj-campfire.core :as cf]))

(def ^:private api-token (env :service-campfire-api-token))
(def ^:private sub-domain (env :service-campfire-sub-domain))
(def ^:dynamic *info-rooms* nil)
(def ^:dynamic *error-rooms* nil)

(defn default-error-rooms []
  [(env :service-campfire-default-info-room) (env :service-campfire-default-error-room)])

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

(defn- error-messages [{:keys [environment app-name title url message status]}]
  (remove nil? [(when title (str title))
                (when app-name (str "Application: " app-name))
                (when environment (str "Environment: " environment))
                (when url (str "Requested-URL: " url))
                (when status (str "Status: " status))
                (when message (str "Message: " message))]))

(defn error
  "Sends error to the error rooms"
  [m]
  (when-not (env :service-campfire-off)
    (dorun (map (fn [error-room]
                  (dorun (map
                          #(cf/message (room error-room) %)
                          (error-messages m))))
                (or *error-rooms* (default-error-rooms))))))

(defmacro with-messages
  [{:keys [environment app-name config]} & body]
  `(let [info-rooms# (conj (to-vec (get-in ~config [:Campfire :Info]))
                           (env :service-campfire-default-info-room))
         error-rooms# (flatten (conj
                                (to-vec (get-in ~config [:Campfire :Error]))
                                (env :service-campfire-default-error-room)
                                info-rooms#))]
     (binding [*info-rooms* info-rooms#
               *error-rooms* error-rooms#]
       (try+
        ~@body
        (catch [:type :shuppet.util/aws] e#
          (error (merge {:env ~environment :app-name ~app-name} e#))
          (throw+ e#))
        (catch clojure.lang.Compiler$CompilerException e#
          (error {:environment ~environment
                  :app-name ~app-name
                  :title "I cannot read this config"
                  :message (.getMessage e#)})
          (throw+ e#))))))
