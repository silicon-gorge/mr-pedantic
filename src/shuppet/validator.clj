(ns shuppet.validator
  (:require [bouncer
             [core :as b]
             [validators :as v]]
            [clojure.set :refer [intersection]]
            [slingshot.slingshot :refer [throw+]]))

(defn- count<=2?
  [input]
  (<= (count input) 2))

(def ^:private app-validator
  {:SecurityGroups [[count<=2? :message "You can have a maximum of 2 security groups only."]]
   [:Role :RoleName] v/required})

(def ^:private env-validator
  {:LoadBalancer [[nil? :message "You cannot define an elb in env config."]]
   :Role [[nil? :message "You cannot define an iam role in env config."]]
   :S3 [[nil? :message "You cannot define an S3 bucket in env config."]]
   :DynamoDB [[nil? :message "You cannot define a Dynamo db in env config."]]})

(defn- validate
  [config validator]
  (if-let [result (first (b/validate
                          config
                          validator))]
    (throw+ {:type ::validator
             :details result})
    config))

(defn validate-env [config]
  (validate config env-validator))

(defn validate-app [config]
  (validate config app-validator))
