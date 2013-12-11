(ns shuppet.sqs
  (:require
   [environ.core :as e]
   [cheshire.core :refer [generate-string]]
   [shuppet
    [util :refer [map-to-query-string url-encode]]
    [signature :refer [v4-auth-headers]]]
   [clj-http.client :as client]))

(defn- send-message
  [queue-url message]
  (let [url (str queue-url  "/?" (map-to-query-string
                                  {"Version" "2012-11-05"
                                   "Action" "SendMessage"
                                   "MessageBody" message}))
        auth-headers (v4-auth-headers {:url url})]
    (client/get url
                {:headers auth-headers})))

(defn- elb-created-message
  "Create the message describing the creation of a load balancer."
  [elb-name]
  (generate-string {:Message (generate-string {:Event "autoscaling:ELB_LAUNCH"
                                               :LoadbalancerName elb-name})}))

(defn announce-elb [elb-name environment]
  (when-let [q-url (e/env (keyword (str "service-sqs-autoscale-announcements-" environment)))]
    (send-message q-url (elb-created-message elb-name))))
