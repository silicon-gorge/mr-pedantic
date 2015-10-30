(ns pedantic.core-test
  (:require [midje
             [sweet :refer :all]
             [util :refer :all]]
            [pedantic
             [cluppet-core :as cluppet]
             [core :refer :all]
             [git :as git]
             [hubot :as hubot]
             [lister :as lister]
             [sqs :as sqs]
             [util :as util]]))

(testable-privates pedantic.core env-config?)

(fact "that we can detect environment config"
      (env-config? "(def $var \"value\")") => truthy
      (env-config? "(def var \"value\")") => falsey)

(fact "that an SQS message is sent when an ELB is created while applying a config"
      (apply-config ..config.. "env" ..app..) => {:application ..app..
                                                  :environment "env"
                                                  :report [{:action :elb/create-load-balancer
                                                            :elb-name ..elb-name..}]}
      (provided
       (lister/application ..app..) => {:pedantic ..app-info..}
       (is-enabled? ..app-info.. "env") => true
       (get-config ..config.. "env" ..app..) => ..evaluated-config..
       (cluppet/apply-config ..app.. "env" ..evaluated-config..)
       => [{:action :elb/create-load-balancer
            :elb-name ..elb-name..}]
       (hubot/info {:application ..app.. :environment "env" :report [{:action :elb/create-load-balancer
                                                                      :elb-name ..elb-name..}]})
       => nil
       (sqs/announce-elb ..elb-name.. "env") => ..anything..))

(fact "that default role policies are added to app config and are validated"
      (get-config ..str-env.. "env" ..app..)
      => {:Role {:RoleName ..name..
                 :Policies [..app-policy..
                            ..default-policy..]}}
      (provided
       (cluppet/evaluate-string ..str-env..)
       => {:DefaultRolePolicies [..default-policy..]}
       (git/get-data ..app..)
       => ..str-app..
       (cluppet/evaluate-string [..str-env.. ..str-app..] anything)
       => {:Role {:RoleName ..name..
                  :Policies [..app-policy..]}}))

(fact "that env config is loaded when there is just an env string"
      (get-config nil "env" nil)
      => ..env-config..
      (provided
       (git/get-data "env")
       => ..str-env..
       (cluppet/evaluate-string ..str-env..)
       => ..env-config...))

(fact "that an error is caught and added to report"
      (apply-config ..env-config.. ..env.. ..app..) => (contains {:application ..app..
                                                                  :environment ..env..
                                                                  :message anything
                                                                  :stacktrace anything})
      (provided
       (lister/application ..app..) => {:pedantic ..app-info..}
       (is-enabled? ..app-info.. ..env..) => true
       (get-config ..env-config.. ..env.. ..app..) => ..app-config..
       (cluppet/apply-config ..app.. ..env.. ..app-config.. ) =throws=> (NullPointerException.)))

(fact "that an app can be excluded"
      (stop-schedule-temporarily "env" "app" nil) => anything
      (is-stopped? "env" "app") => true
      (is-stopped? "env" "anotherapp") => false
      (restart-app-schedule "env" "app") => anything
      (is-stopped? "env" "app") => false)

(fact "that an application isn't enabled when the config has it listed as disabled"
      (is-enabled? {:enabled false} "env") => falsey)

(fact "that an application is enabled when the config doesn't have a value for enabled"
      (is-enabled? {} "env") => truthy)

(fact "that an application is enabled when the config has it listed as enabled"
      (is-enabled? {:enabled true} "env") => truthy)

(fact "that an application is enabled for any environment when it doesn't list environments"
      (is-enabled? {:environments nil} "anything") => truthy
      (is-enabled? {:environments nil} "anythingelse") => truthy
      (is-enabled? {:environments []} "anything") => truthy
      (is-enabled? {:environments []} "anythingelse") => truthy)

(fact "that an application isn't enabled when it lists environments and the specified one isn't in that list"
      (is-enabled? {:environments ["env"]} "anything") => falsey)

(fact "that an application is enabled when it lists the specified environment"
      (is-enabled? {:environments ["env"]} "env") => truthy)

(fact "that any errors are caught"
      (configure-apps ..env..) => (contains {:environment ..env..
                                             :message anything
                                             :stacktrace anything})
      (provided
       (apply-config ..env..) =throws=> (NullPointerException.)))

(fact "that env and all apps are configured"
      (configure-apps ..env..) => [..env-report.. ..app-report..]
      (provided
       (apply-config ..env..) => ..env-report..
       (git/get-data ..env..) => ..env-config..
       (update-configs ..env-config.. ..env..) => [..app-report..]))
