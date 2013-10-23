(ns shuppet.unit.elb
  (:require [cheshire.core :as json]
            [clojure.xml :as xml]
            [clojure.zip :as zip :refer [children]]
            [clojure.data.zip.xml :refer [xml1-> xml->]])
  (:import (java.io ByteArrayInputStream))
  (:use [shuppet.elb]
        [midje.sweet]))

(def subnet1 "subnet-24df904c")
(def subnet2 "subnet-bdc08fd5")

(def config {:LoadBalancerName "elb-for-test"
             :Listeners [{:LoadBalancerPort 8080
                          :InstancePort 8080
                          :Protocol "http"
                          :InstanceProtocol "http"}
                         {:LoadBalancerPort 80
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

(def to-aws-format @#'shuppet.elb/to-aws-format)
(def check-string-value @#'shuppet.elb/check-string-value)

(def xml (->  (slurp "test/shuppet/unit/resources/DescribeLoadBalancersResponse.xml")
              (.getBytes)
              (ByteArrayInputStream.)
              (xml/parse)
              (zip/xml-zip)))

(fact-group :unit

            (fact "correctly convert to aws format"
                  (let [converted-config { "LoadBalancerName" "elb-for-test"
                                           "Listeners.member.1.LoadBalancerPort" "8080"
                                           "Listeners.member.1.InstancePort"   "8080"
                                           "Listeners.member.1.Protocol" "http"
                                           "Listeners.member.1.InstanceProtocol" "http"
                                           "Listeners.member.2.LoadBalancerPort" "80"
                                           "Listeners.member.2.InstancePort"   "8080"
                                           "Listeners.member.2.Protocol" "http"
                                           "Listeners.member.2.InstanceProtocol" "http"
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
                  (check-string-value xml :missing "value") =>  (throws clojure.lang.ExceptionInfo))

            (fact "config is created when missing"
                  (ensure-config config) => ..response..
                  (provided
                   (find-elb anything) => nil
                   (create-elb config) => ..response..))

            (fact "error when fixed value changed"
                  (ensure-config config) => (throws clojure.lang.ExceptionInfo)
                  (provided
                   (find-elb anything) => xml))

            (fact "map is built from children"
                  (children-to-map (xml-> xml  :DescribeLoadBalancersResult :LoadBalancerDescriptions :member :HealthCheck children))
                  => {:Interval "6"
                      :Target "HTTP:8080/1.x/ping"
                      :HealthyThreshold "2"
                      :Timeout "5"
                      :UnhealthyThreshold "2"})

            (fact "list of maps is built from children"
                  (-> (xml-> xml :DescribeLoadBalancersResult :LoadBalancerDescriptions :member :ListenerDescriptions :member children)
                      (filter-children :Listener)
                      (children-to-maps))
                  => [{:InstancePort "8080"
                       :InstanceProtocol "HTTP"
                       :LoadBalancerPort "8080"
                       :Protocol "HTTP"}
                      {:InstancePort "8080"
                       :InstanceProtocol "HTTP"
                       :LoadBalancerPort "80"
                       :Protocol "HTTP"}]))
