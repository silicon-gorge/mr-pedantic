(ns shuppet.errorhandler
  (:require [nokia.ring-utils.logging :as log :only (error)]
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

(defn- cf-message [title env url message status]
  (str "*******ALERT*******"
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

(defn- ec2-message-title [action app-name]
  (let [message (str "EC2 failure while performing security group action '" action "'")]
    (if (empty?  app-name)
      message
      (str message " for  application '" app-name "'"))))

(defn wrap-error-handling
  "A middleware function to add catch and log uncaught exceptions, then return a nice xml  response to the client"
  [handler]
  (fn [request]
    (try+
     (handler request)
     (catch [:type :shuppet.aws/ec2] {:keys [env action name url message status]}
       (cf/send-message (cf-message (ec2-message-title action name) env url message status))
       (error-response (ec2-message-title action name) url message status))
     (catch Throwable e
       (id-error-response e (log/error e request))))))
