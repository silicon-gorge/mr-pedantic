(ns shuppet.unit.core
  (:require  [slingshot.slingshot :refer [try+ throw+]]
             [cluppet.core :as cluppet]
             [shuppet.sqs :as sqs]
             [shuppet.util :as util]
             [shuppet.git :as git])
  (:use [shuppet.core]
        [midje.util]
        [midje.sweet]))

(testable-privates shuppet.core env-config?)

(fact-group :unit

            (fact "can tell when environment config"
                  (env-config? "(def $var \"value\")") => truthy
                  (env-config? "(def var \"value\")") => falsey)


            (fact "sqs message is sent when elb is created while applying a config"
                  (apply-config ..config.. "poke" ..app..) => {:app ..app..
                                                               :env "poke"
                                                               :report [{:action :CreateLoadBalancer
                                                                         :elb-name ..elb-name..}]}
                  (provided
                   (get-config ..config.. "poke" ..app..) => ..evaluated-config..
                   (cluppet/apply-config ..evaluated-config..) =>  [{:action :CreateLoadBalancer
                                                                     :elb-name ..elb-name..}]
                   (sqs/announce-elb ..elb-name.. "poke" ) => ..anything.. :times 1))

            (fact "default role policies are added to app config and is validated"
                  (get-config ..str-env.. "poke" ..app..)
                  => {:Role {:RoleName ..name..
                             :Policies [..app-policy..
                                        ..default-policy..]}}
                  (provided
                   (cluppet/evaluate-string ..str-env..)
                   => {:DefaultRolePolicies [..default-policy..]}
                   (git/get-data "poke" ..app..)
                   => ..str-app..
                   (cluppet/evaluate-string [..str-env.. ..str-app..] anything)
                   =>  {:Role {:RoleName ..name..
                               :Policies [..app-policy..]}}))

            (fact "env and all apps are configured"
                  (configure-apps ..env..) => [..env-report.. ..app-report..]
                  (provided
                   (apply-config ..env..) => ..env-report..
                   (git/get-data ..env..) => ..env-config..
                   (filtered-apply-config ..env-config.. ..env.. ..app..) => ..app-report..
                   (app-names ..env..) => [..app..]))

            (fact "error is caught and added to report"
                  (apply-config ..env-config.. ..env.. ..app..) => (contains {:app ..app..
                                                                              :env ..env..
                                                                              :message anything
                                                                              :stacktrace anything})
                  (provided
                   (get-config ..env-config.. ..env.. ..app..) => ..app-config..
                   (cluppet/apply-config ..app-config.. ) =throws=>  (NullPointerException.)))


            (fact "an app can be excluded"
                  (stop-schedule-temporarily "env" "app" nil)
                  (filtered-apply-config ..env-config.. "env" "app") => (contains {:excluded true})
                  (filtered-apply-config ..env-config.. "env" "anotherapp") => ..report..
                  (provided
                   (apply-config ..env-config..  "env" "anotherapp") => ..report..))


            (fact "env error are caught"
                  (configure-apps ..env..) =>  (contains {:env ..env..
                                                          :message anything
                                                          :stacktrace anything})
                  (provided
                   (apply-config ..env..) =throws=>  (NullPointerException.))))
