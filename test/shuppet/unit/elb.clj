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
                  (let [xml (ByteArrayInputStream. (.getBytes (slurp "test/shuppet/unit/resources/DescribeLoadBalancersResponse.xml")))
                        zipper (zip/xml-zip (xml/parse xml))]
                    (check-text-value zipper [:HealthCheck :Interval] "6") => nil))

            (fact "different text value fails"
                  (let [xml (ByteArrayInputStream. (.getBytes (slurp "test/shuppet/unit/resources/DescribeLoadBalancersResponse.xml")))
                        zipper (zip/xml-zip (xml/parse xml))]
                    (check-text-value zipper [:HealthCheck :Interval] "wrong") =>  (throws clojure.lang.ExceptionInfo)))

            (fact "missing text value fails"
                  (let [xml (ByteArrayInputStream. (.getBytes (slurp "test/shuppet/unit/resources/DescribeLoadBalancersResponse.xml")))
                        zipper (zip/xml-zip (xml/parse xml))]
                    (check-text-value zipper [:notthere] "value") =>  (throws clojure.lang.ExceptionInfo))))
