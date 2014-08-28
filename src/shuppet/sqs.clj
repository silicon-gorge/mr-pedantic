(ns shuppet.sqs
  (:require [environ.core :as e]
            [cheshire.core :refer [generate-string]]
            [cluppet.signature :refer [get-signed-request]]
            [clj-http.client :as client]))

(defn- send-message
  [queue-url message]
  (when-not (e/env :service-sqs-disabled)
    (let [request (get-signed-request "sqs" {:url queue-url
                                             :params {:Action "SendMessage"
                                                      :MessageBody message}})]
      (client/get (request :url)
                  {:headers (request :headers)}))))

(defn- elb-created-message
  "Create the message describing the creation of a load balancer."
  [elb-name]
  (generate-string {:Message (generate-string {:Event "autoscaling:ELB_LAUNCH"
                                               :LoadbalancerName elb-name})}))

(defn announce-elb
  [elb-name environment]
  (when-let [q-url (e/env (keyword (str "service-sqs-autoscale-announcements-" environment)))]
    (send-message q-url (elb-created-message elb-name))))
