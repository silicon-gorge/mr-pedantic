(ns pedantic.subnets-test
  (:require [amazonica.aws.ec2 :as ec2]
            [midje.sweet :refer :all]
            [pedantic.subnets :refer :all]))

(fact "that getting the availability zone for a subnet works"
      (availability-zone "subnet-id") => "availability-zone"
      (provided
       (ec2/describe-subnets anything :subnet-ids ["subnet-id"]) => {:subnets [{:availability-zone "availability-zone"}]}))
