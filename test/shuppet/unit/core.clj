(ns shuppet.unit.core
  (:require  [slingshot.slingshot :refer [try+ throw+]]
             [shuppet.core-shuppet :as shuppet]
             [shuppet.sqs :as sqs])
  (:use [shuppet.core]
        [midje.util]
        [midje.sweet])
   (:import [shuppet.core_shuppet LocalConfig]
            [shuppet.core_shuppet LocalAppNames]
            [shuppet.core OnixAppNames]
            [shuppet.core GitConfig]))

(testable-privates shuppet.core env-config?)
(testable-privates shuppet.core apply-config)
(testable-privates shuppet.core concurrent-config-update)


(fact-group :unit
            (fact "onix is used to get app names"
                  (with-ent-bindings nil
                    shuppet/*application-names*) => (fn [result] (instance? OnixAppNames result)))

            (fact "local app names are uesd"
                  (with-ent-bindings "local"
                    shuppet/*application-names*) => (fn [result] (instance? LocalAppNames result)))


            (fact "git is used to get config"
                  (with-ent-bindings nil
                    shuppet/*configuration*) => (fn [result] (instance? GitConfig result)))

            (fact "local config is used"
                  (with-ent-bindings "local"
                    shuppet/*configuration*) => (fn [result] (instance? LocalConfig result)))

            (fact "can tell when environment config"
                  (env-config? "(def $var \"value\")") => truthy
                  (env-config? "(def var \"value\")") => falsey)

            (fact "don't apply config in prod for tooling services"
                  (apply-config "prod" "ditto") => nil
                  (apply-config "prod" "other") => ..response..
                  (provided
                   (shuppet/apply-config anything anything) => ..response.. :times 1)
                  (apply-config "poke" "ditto") => ..response..
                  (provided
                   (shuppet/apply-config anything anything) => ..response.. :times 1))

            (fact "sqs message is sent when elb is created while applying a config"
                  (apply-config "poke" ..app..) => [{:action :CreateLoadBalancer
                                              :elb-name ..elb-name..}]
                  (provided
                   (shuppet/apply-config "poke" ..app..) =>  [{:action :CreateLoadBalancer
                                                                :elb-name ..elb-name..}]
                   (sqs/announce-elb ..elb-name.. "poke" ) => ..anything.. :times 1))

            (fact "sqs message is sent when elb is created while updating all configs"
                  (update-configs "poke") => (list {:app ..app.. :report [{:action :CreateLoadBalancer
                                                 :elb-name ..elb-name..}]})
                  (provided
                   (app-names "poke") => [..app..]
                   (shuppet/apply-config "poke" ..app..) =>  [{:action :CreateLoadBalancer
                                                                :elb-name ..elb-name..}]
                   (sqs/announce-elb ..elb-name.. "poke" ) => ..anything.. :times 1)))
