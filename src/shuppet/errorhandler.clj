(ns shuppet.errorhandler
  (:require [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [shuppet.campfire :as cf]
            [shuppet.util :refer [without-nils]])
  (:use [slingshot.slingshot :only [try+]])
  (:import [java.io StringWriter PrintWriter]))

(defn- error-message [^Exception e]
  (or
   (and (boolean (Boolean. (env :service-production)))
        (.getMessage e))
   (let [sw (StringWriter.)]
     (.printStackTrace e (PrintWriter. sw))
     (str sw))))

(defn error-response
  ([^Exception e]
     (error-response (error-message e) 500))
  ([message status]
     (error-response nil message status))
  ([url message status]
     (error-response nil url message status))
  ([title url message status]
     (let [body (without-nils {:title title
                               :request-url url
                               :message message
                               :status (str status)
                               :type "error"})]
       (log/error (if (empty? title) message title) body)
       {:status status
        :headers {"Content-Type" "application/json"}
        :body body})))

(defn- id-error-response
  [^Exception e id]
  (assoc-in (error-response e) [:body :log-id] id))

(defn- cf-message [title env name url message status]
  (str "*******ALERT*******"
       "\n"
       "Application: " name
       "\n"
       "Title: " title
       "\n"
       "Environment: " env
       "\n"
       "Message: " message
       "\n"
       "Requested-URL: " url
       "\n"
       "Status: " status))

(defn- add-name [message app-name]
  (if (empty? app-name)
    message
    (str message " for application '" app-name "'")))

(defn wrap-error-handling
  "A middleware function to add catch and log uncaught exceptions, then return a nice xml  response to the client"
  [handler]
  (fn [request]
    (try+
     (handler request)
     (catch [:type :shuppet.util/aws] {:keys [title env name url message status cf-rooms]}
       (cf/send-message (cf-message title env name url message status) cf-rooms)
       (error-response (add-name title name) url message status))
     (catch Throwable e
       (id-error-response e (log/error e request))))))
