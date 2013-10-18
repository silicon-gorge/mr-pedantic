(ns shuppet.loadbalancer
  (:require
   [shuppet.aws :refer [elb-request]]
   [clj-http.client :as client]
   [clojure.tools.logging :as log]
   [clojure.xml :as xml]
   [clojure.zip :as zip]
   [clojure.data.zip.xml :refer [xml1-> attr xml-> text text= attr=]]))

(def subnet1 "subnet-24df904c")
(def subnet2 "subnet-bdc08fd5")

(defn map-to-dot [prefix map]
  )

(defn list-to-member [prefix list]
  (apply hash-map (flatten (map #(do [(str prefix ".member." %1) %2]) (iterate inc 1) list))))

(def elb-config-modified
  { "LoadBalancerName" "nico-test-elb"
    "Listerners" [{"LoadBalancerPort" 8080
                   "InstancePort" 8080
                   "Protocol" "http"
                   "InstanceProtocol" "http"}]
    "Subnets" [subnet1 subnet2]
    "Scheme" "internal"
    "HealthCheck" {"Target" "HTTP:8080/1.x/ping"
                   "HealthyThreshold" 2
                   "UnhealthyThreshold" 2
                   "Interval" 6
                   "Timeout" 5}})


(defn to-aws-format [config]

  )


(def elb-config { "LoadBalancerName" "nico-test-elb"
                  "Listeners.member.1.LoadBalancerPort" 8080
                  "Listeners.member.1.InstancePort"  8080
                  "Listeners.member.1.Protocol" "http"
                  "Listeners.member.1.InstanceProtocol" "http"
                  "Subnets.member.1" subnet1
                  "Subnets.member.2" subnet2
                  "Scheme" "internal"
                  ;health
                  "HealthCheck.Target" "HTTP:8080/1.x/ping"
                  "HealthCheck.HealthyThreshold" 2
                  "HealthCheck.UnhealthyThreshold" 2
                  "HealthCheck.Interval" 6
                  "HealthCheck.Timeout" 5
                  "SecurityGroups.member.1" "nicoelbtest"
                                        ; "AvailabilityZones.member.1" "eu-west-1a"
                                        ;"AvailabilityZones.member.2" "eu-west-1b"

                  })

(defn create-elb [config]
  ;create security group first for elb
  (elb-request (merge {"Action" "CreateLoadBalancer"} (select-keys config
                                                             ["LoadBalancerName"
                                                              "Listeners.member.1.LoadBalancerPort"
                                                              "Listeners.member.1.InstancePort"
                                                              "Listeners.member.1.Protocol"
                                                              "Listeners.member.1.InstanceProtocol"
                                                              "Subnets.member.1"
                                                              "Subnets.member.2"
                                                              "SecurityGroups.member.1"
                                                              "Scheme" "internal"])))
  (elb-request (merge {"Action" "ConfigureHealthCheck"} (select-keys config
                                                                     ["LoadBalancerName"
                                                                      "HealthCheck.Target"
                                                                      "HealthCheck.HealthyThreshold"
                                                                      "HealthCheck.UnhealthyThreshold"
                                                                      "HealthCheck.Interval"
                                                                      "HealthCheck.Timeout"]))))


(defn find-elb [name]
  (elb-request {"Action" "DescribeLoadBalancers"
                "LoadBalancerNames.member.1" name}))

(defn- create-listeners []
  (elb-request {"Action" "CreateLoadBalancerListeners"
                "LoadBalancerName" "recommendations"
                "Listeners.member.1.LoadBalancerPort" 80
                "Listeners.member.1.InstancePort" 8080
                "Listeners.member.1.InstanceProtocol" "http"
                "Listeners.member.1.Protocol" "http"}))

(defn- delete-listerners []
  (elb-request {"Action" "DeleteLoadBalancerListeners"
                "LoadBalancerName" "recommendations"
                "LoadBalancerPorts.member.1" "80"}))

(defn- delete-elb []
  (elb-request {"Action" "DeleteLoadBalancer"
                "LoadBalancerName" "test-elb-nico"}))
