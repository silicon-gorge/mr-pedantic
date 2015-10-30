(ns pedantic.environments-test
  (:require [midje.sweet :refer :all]
            [ninjakoala.ttlr :as ttlr]
            [pedantic
             [environments :refer :all]
             [lister :as lister]]))

(fact "that getting environment names does what we expect"
      (environment-names) => #{"env1" "env2"}
      (provided
       (environments) => {:env1 {:name "env1"} :env2 {:name "env2"}}))

(fact "that getting a single environment does what we expect when the environment exists"
      (environment "env2") => ..env2..
      (provided
       (environments) => {:env1 ..env1..
                          :env2 ..env2..}))

(fact "that we get nil back when an environment doesn't exist"
      (environment "unknown") => nil
      (provided
       (environments) => {:env1 ..env1..
                          :env2 ..env2..}))

(fact "that updating environments creates the right map and removes anything which shouldn't be considered"
      (update-environments) => {:env1 {:name "env1" :metadata {:pedantic {:enabled true}}} :env3 {:name "env3" :metadata {:pedantic {:enabled true}}}}
      (provided
       (lister/environments) => ["env1" "env2" "env3"]
       (lister/environment "env1") => {:name "env1" :metadata {:pedantic {:enabled true}}}
       (lister/environment "env2") => {:name "env2"}
       (lister/environment "env3") => {:name "env3" :metadata {:pedantic {:enabled true}}}))

(fact "that we get nil for an account ID if the environment doesn't exist"
      (account-id "unknown") => nil
      (provided
       (environment "unknown") => nil))

(fact "that get the correct account ID back for an environment which exists"
      (account-id "env") => "account-id"
      (provided
       (environment "env") => {:metadata {:account-id "account-id"}}))

(fact "that we get nil for an account name if the environment doesn't exist"
      (account-name "unknown") => nil
      (provided
       (environment "unknown") => nil))

(fact "that we get the correct account name back for an environment which exists"
      (account-name "env") => "account-name"
      (provided
       (environment "env") => {:metadata {:account-name "account-name"}}))

(fact "that we get nil for an autoscaling queue if the environment doesn't exist"
      (autoscaling-queue "unknown") => nil
      (provided
       (environment "unknown") => nil))

(fact "that we get the correct autoscaling queue back for an environment which exists"
      (autoscaling-queue "env") => "queue-url"
      (provided
       (environment "env") => {:metadata {:autoscaling-queue "queue-url"}}))

(fact "that we get nil for an environment start delay when the environment doesn't exist"
      (start-delay "env") => nil
      (provided
       (environment "env") => nil))

(fact "that we get the default value for an environment start delay when the environment has no delay set"
      (start-delay "env") => 1
      (provided
       (environment "env") => {}))

(fact "that we get the specified start delay value when the environment has one set"
      (start-delay "env") => 20
      (provided
       (environment "env") => {:metadata {:pedantic {:delay 20}}}))

(fact "that we're healthy if there are some environments"
      (healthy?) => truthy
      (provided
       (environments) => {:env {}}))

(fact "that we aren't healthy if there aren't any environments"
      (healthy?) => falsey
      (provided
       (environments) => {}))
