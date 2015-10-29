(ns pedantic.cluppet-core-test
  (:require [midje.sweet :refer :all]
            [pedantic
             [cluppet-core :refer :all]
             [elb :as elb]
             [iam :as iam]
             [securitygroups :as sg]]
            [slingshot.slingshot :refer [try+ throw+]]))

(fact "that a string is correctly evaluated"
      (evaluate-string ["(def var-val \"ok\") var-val"]) => "ok")

(fact "that long running code times out"
      (evaluate-string "(Thread/sleep 600000000)")
      => (throws java.util.concurrent.TimeoutException))

(fact "that global variables are accessible"
      (evaluate-string ["{:env $env :app-name $app-name :number $number}"] {:$env "env"
                                                                            :$app-name "app-name"
                                                                            :$number 1})
      => {:env "env"
          :app-name "app-name"
          :number 1})

(fact "that an error during execution is handled and rethrown"
      (evaluate-string ["(throw (java.lang.RuntimeException. \"Busted\"))"]) => (throws clojure.lang.ExceptionInfo))

(fact "that badly-formed config is handled and rethrown"
      (evaluate-string ["{"] {}) => (throws clojure.lang.ExceptionInfo))

(fact "that invalid configuration is handled and rethrown"
      (evaluate-string ["(println something)"]) => (throws clojure.lang.ExceptionInfo))

(fact "that applying configuration does what expect"
      (apply-config "application" "environment" ..config..) => []
      (provided
       (sg/ensure-security-groups ..config.. "application") => nil
       (elb/ensure-elbs ..config.. "application") => nil
       (iam/ensure-iam ..config..) => nil))
