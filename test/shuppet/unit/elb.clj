(ns shuppet.unit.elb
  (:require [cheshire.core :as json]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
)
  (:import (java.io ByteArrayInputStream))
  (:use [shuppet.elb]
        [midje.sweet]))

(def subnet1 "subnet-24df904c")
(def subnet2 "subnet-bdc08fd5")
(def config {:LoadBalancerName "testelb"
             :Listeners [{:LoadBalancerPort 8080
                          :InstancePort 8080
                          :Protocol "http"
                          :InstanceProtocol "http"}]
             :Subnets [subnet1 subnet2]
             :Scheme "internal"
             :HealthCheck {:Target "HTTP:8080/1.x/ping"
                           :HealthyThreshold 2
                           :UnhealthyThreshold "2"
                           :Interval 6
                           :Timeout 5}})

(def xml (->  (slurp "test/shuppet/unit/resources/DescribeLoadBalancersResponse.xml")
              (.getBytes)
              (ByteArrayInputStream.)
              (xml/parse)
              (zip/xml-zip)))

(fact-group :unit

            (fact "correctly convert to aws format"
                  (let [converted-config { "LoadBalancerName" "testelb"
                                           "Listeners.member.1.LoadBalancerPort" "8080"
                                           "Listeners.member.1.InstancePort"   "8080"
                                           "Listeners.member.1.Protocol" "http"
                                           "Listeners.member.1.InstanceProtocol" "http"
                                           "Subnets.member.1" subnet1
                                           "Subnets.member.2" subnet2
                                           "Scheme" "internal"
                                           "HealthCheck.Target" "HTTP:8080/1.x/ping"
                                           "HealthCheck.HealthyThreshold" "2"
                                           "HealthCheck.UnhealthyThreshold" "2"
                                           "HealthCheck.Interval" "6"
                                           "HealthCheck.Timeout" "5"}]

                    (to-aws-format config) => converted-config))

            (fact "same text value returns nil"
                  (check-string-value xml :Scheme "internal") => nil)

            (fact "different text value fails"
                  (check-string-value xml :Scheme "wrong") =>  (throws clojure.lang.ExceptionInfo))

            (fact "missing text value fails"
                  (check-string-value xml :missing "value") =>  (throws clojure.lang.ExceptionInfo)))
