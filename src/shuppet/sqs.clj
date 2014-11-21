(ns shuppet.sqs
  (:require [environ.core :refer [env]]
            [cheshire.core :refer [generate-string]]
            [cluppet.signature :refer [get-signed-request]]
            [clj-http.client :as client]))

(def ^:private sqs-enabled?
  (Boolean/valueOf (env :aws-sqs-enabled)))

(defn- send-message
  [queue-url message]
  (when sqs-enabled?
    (let [request (get-signed-request "sqs" {:url queue-url
                                             :params {:Action "SendMessage"
                                                      :MessageBody message}})]
      (client/get (request :url)
                  {:headers (request :headers)}))))

(defn- elb-created-message
  "Create the message describing the creation of a load balancer."
  [elb-name]
  (generate-string (sorted-map :Message (generate-string (sorted-map :Event "autoscaling:ELB_LAUNCH"
                                                                     :LoadbalancerName elb-name)))))

(defn- announcements-queue-url
  [environment]
  (env (keyword (str "aws-sqs-autoscale-announcements-" environment))))

(defn announce-elb
  [elb-name environment]
  (when-let [q-url (announcements-queue-url environment)]
    (send-message q-url (elb-created-message elb-name))))
