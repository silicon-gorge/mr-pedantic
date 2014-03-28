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

            (fact "don't apply config in prod for tooling services"
                  (apply-config "prod" "ditto") => nil
                  (apply-config "prod" "other") => ..response..
                  (provided
                   (cluppet/apply-config anything anything anything) => ..response.. :times 1)
                  (apply-config "poke" "ditto") => ..response..
                  (provided
                   (cluppet/apply-config anything anything anything) => ..response.. :times 1))

            (fact "sqs message is sent when elb is created while applying a config"
                  (apply-config "poke" ..app..) => [{:action :CreateLoadBalancer
                                              :elb-name ..elb-name..}]
                  (provided
                   (cluppet/apply-config anything "poke" ..app..) =>  [{:action :CreateLoadBalancer
                                                                :elb-name ..elb-name..}]
                   (sqs/announce-elb ..elb-name.. "poke" ) => ..anything.. :times 1))

            (fact "sqs message is sent when elb is created while updating all configs"
                  (update-configs anything "poke") => (list {:app ..app.. :report [{:action :CreateLoadBalancer
                                                 :elb-name ..elb-name..}]})
                  (provided
                   (app-names "poke") => [..app..]
                   (cluppet/apply-config anything "poke" ..app..) =>  [{:action :CreateLoadBalancer
                                                                :elb-name ..elb-name..}]
                   (sqs/announce-elb ..elb-name.. "poke" ) => ..anything.. :times 1)))
