(ns pedantic.sqs
  (:require [amazonica.aws.sqs :as sqs]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [pedantic
             [aws :as aws]
             [environments :as environments]
             [guard :refer [guarded]]]))

(def ^:private sqs-enabled*
  (Boolean/valueOf (env :aws-sqs-enabled)))

(defn sqs-enabled?
  []
  sqs-enabled*)

(defn send-message
  [queue-url message]
  (when (sqs-enabled?)
    (guarded (sqs/send-message (aws/config)
                               :queue-url queue-url
                               :delay-seconds 0
                               :message-body message)))
  nil)

(defn elb-created-message
  [elb-name]
  (let [message (sorted-map :Event "autoscaling:ELB_LAUNCH" :LoadbalancerName elb-name)
        message-string (json/generate-string message)]
    (json/generate-string (sorted-map :Message message-string))))

(defn announce-elb
  [elb-name environment]
  (when-let [queue-url (environments/autoscaling-queue environment)]
    (send-message queue-url (elb-created-message elb-name))
    nil))
