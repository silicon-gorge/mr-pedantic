(ns shuppet.unit.util
  (:require [cheshire.core :as json]
            [clojure.xml :as xml]
            [clojure.zip :as zip :refer [children]]
            [clojure.data.zip.xml :refer [xml1-> xml->]])
  (:import (java.io ByteArrayInputStream))
  (:use [shuppet.util]
        [midje.sweet]))

(def xml (->  (slurp "test/shuppet/unit/resources/DescribeLoadBalancersResponse.xml")
              (.getBytes)
              (ByteArrayInputStream.)
              (xml/parse)
              (zip/xml-zip)))

(fact-group :unit

            (fact "values are converted to string"
                  (values-tostring {:k1 1 :k2 2 :k3 "value"})
                  => {:k1 "1" :k2 "2" :k3 "value"})

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
                       :Protocol "HTTP"}])

            (future-fact "config is correctly compared"))
