(ns pedantic.core-test
  (:require [cluppet.core :as cluppet]
            [midje
             [sweet :refer :all]
             [util :refer :all]]
            [pedantic
             [campfire :as cf]
             [core :refer :all]
             [git :as git]
             [sqs :as sqs]
             [util :as util]]))

(testable-privates pedantic.core env-config?)

(fact "that we can detect environment config"
      (env-config? "(def $var \"value\")") => truthy
      (env-config? "(def var \"value\")") => falsey)

(fact "that an SQS message is sent when an ELB is created while applying a config"
      (apply-config ..config.. "poke" ..app..) => {:application ..app..
                                                   :environment "poke"
                                                   :report [{:action :CreateLoadBalancer
                                                             :elb-name ..elb-name..}]}
      (provided
       (get-config ..config.. "poke" ..app..) => ..evaluated-config..
       (cluppet/apply-config ..evaluated-config..) =>  [{:action :CreateLoadBalancer
                                                         :elb-name ..elb-name..}]
       (cf/info {:application ..app.. :environment "poke" :report [{:action :CreateLoadBalancer
                                                                    :elb-name ..elb-name..}]}) => nil
       (sqs/announce-elb ..elb-name.. "poke" ) => ..anything..))

(fact "that default role policies are added to app config and are validated"
      (get-config ..str-env.. "poke" ..app..)
      => {:Role {:RoleName ..name..
                 :Policies [..app-policy..
                            ..default-policy..]}}
      (provided
       (cluppet/evaluate-string ..str-env..)
       => {:DefaultRolePolicies [..default-policy..]}
       (git/get-data ..app..)
       => ..str-app..
       (cluppet/evaluate-string [..str-env.. ..str-app..] anything)
       =>  {:Role {:RoleName ..name..
                   :Policies [..app-policy..]}}))

(fact "that env config is loaded when there is just an env string"
      (get-config nil "poke" nil)
      => ..env-config..
      (provided
       (git/get-data "poke")
       => ..str-env..
       (cluppet/evaluate-string ..str-env..)
       => ..env-config...))

(fact "that env and all apps are configured"
      (configure-apps ..env..) => [..env-report.. ..app-report..]
      (provided
       (apply-config ..env..) => ..env-report..
       (git/get-data ..env..) => ..env-config..
       (filtered-apply-config ..env-config.. ..env.. ..app..) => ..app-report..
       (app-names ..env..) => [..app..]))

(fact "that an error is caught and added to report"
      (apply-config ..env-config.. ..env.. ..app..) => (contains {:application ..app..
                                                                  :environment ..env..
                                                                  :message anything
                                                                  :stacktrace anything})
      (provided
       (get-config ..env-config.. ..env.. ..app..) => ..app-config..
       (cluppet/apply-config ..app-config.. ) =throws=>  (NullPointerException.)))

(fact "that an app can be excluded"
      (stop-schedule-temporarily "env" "app" nil) => anything
      (filtered-apply-config ..env-str.. "env" "app") => (contains {:excluded true})
      (filtered-apply-config ..env-str.. "env" "anotherapp") => ..report..
      (provided
       (apply-config ..env-str.. "env" "anotherapp") => ..report..)
      (restart-app-schedule "env" "app") => anything
      (filtered-apply-config ..env-str.. "env" "app") => ..report..
      (provided
       (apply-config ..env-str..  "env" "app") => ..report..))

(fact "that any errors are caught"
      (configure-apps ..env..) =>  (contains {:environment ..env..
                                              :message anything
                                              :stacktrace anything})
      (provided
       (apply-config ..env..) =throws=>  (NullPointerException.)))
