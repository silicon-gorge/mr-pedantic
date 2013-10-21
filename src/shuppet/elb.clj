(ns shuppet.elb
  (:require
   [shuppet.aws :refer [elb-request]]
   [clj-http.client :as client]
   [clojure.tools.logging :as log]
   [clojure.xml :as xml]
   [clojure.zip :as zip]
   [clojure.data.zip.xml :refer [xml1-> attr xml-> text text= attr=]]))

(defn map-to-dot [prefix m]
  (map (fn [[k v]] [(str prefix "." k) v])
       m))

(defn to-member [prefix i]
  (str prefix ".member." i))

(defn list-to-member [prefix list]
  (flatten (map (fn [i v]
                  (cond
                   (map? v) (map-to-dot (to-member prefix i) v)
                   :else [(to-member prefix i) v]))
                (iterate inc 1)
                list)))

(defn to-aws-format [config]
  (apply hash-map (flatten (map (fn [[k v]]
                                  (cond (sequential? v) (list-to-member k v)
                                        (map? v) (map-to-dot k v)
                                        :else [k v])
                                  )
                                config))))

(defn create-elb [config]
                                        ;create security group first for elb


  (let [elb (dissoc config "HealthCheck")
        health-check (select-keys config ["LoadBalancerName" "HealthCheck"])])


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
