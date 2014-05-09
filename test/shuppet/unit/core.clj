(ns shuppet.unit.core
  (:require  [slingshot.slingshot :refer [try+ throw+]]
             [cluppet.core :as cluppet]
             [shuppet.sqs :as sqs])
  (:use [shuppet.core]
        [midje.util]
        [midje.sweet]))

(testable-privates shuppet.core env-config?)

(fact-group :unit

            (fact "can tell when environment config"
                  (env-config? "(def $var \"value\")") => truthy
                  (env-config? "(def var \"value\")") => falsey)


            (fact "sqs message is sent when elb is created while applying a config"
                  (apply-config ..config.. "poke" ..app..) => [{:action :CreateLoadBalancer
                                                                 :elb-name ..elb-name..}]
                  (provided
                   (get-config ..config.. "poke" ..app..) => ..evaluated-config..
                   (cluppet/apply-config ..evaluated-config..) =>  [{:action :CreateLoadBalancer
                                                                     :elb-name ..elb-name..}]
                   (sqs/announce-elb ..elb-name.. "poke" ) => ..anything.. :times 1)))
