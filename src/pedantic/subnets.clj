(ns pedantic.subnets
  (:require [amazonica.aws.ec2 :as ec2]
            [pedantic
             [aws :as aws]
             [guard :refer [guarded]]]))

(defn availability-zone
  [subnet-id]
  (let [subnet (first (:subnets (guarded (ec2/describe-subnets (aws/config) :subnet-ids [subnet-id]))))]
    (:availability-zone subnet)))
