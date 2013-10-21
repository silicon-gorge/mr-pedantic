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
  (let [elb-config (dissoc config "HealthCheck")
        health-check-config (select-keys config ["LoadBalancerName" "HealthCheck"])]
    (elb-request (merge {"Action" "CreateLoadBalancer"} (to-aws-format elb-config)))
    (elb-request (merge {"Action" "ConfigureHealthCheck"} (to-aws-format health-check-config)))))

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
