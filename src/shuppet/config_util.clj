(ns shuppet.config-util
  (:require [shuppet.util :refer [without-nils]]))

(defn elb-listener
  "http://docs.aws.amazon.com/ElasticLoadBalancing/latest/APIReference/API_Listener.html"
  [elb-port instance-port protocol]
  (without-nils {:LoadBalancerPort elb-port
                 :InstancePort instance-port
                 :Protocol protocol
                 :InstanceProtocol protocol}))

(defn elb-healthcheck
  "http://docs.aws.amazon.com/ElasticLoadBalancing/latest/APIReference/API_HealthCheck.html"
  [target healthy-threshold unhealthy-threshold interval timeout]
  (without-nils {:Target target
                 :HealthyThreshold healthy-threshold
                 :UnhealthyThreshold unhealthy-threshold
                 :Interval interval
                 :Timeout timeout}))

(defn sg-rule
  "Creates a Ingress/Egress config for a security group"
  ([protocol from-port to-port ip-ranges]
      (let [record (without-nils {:IpProtocol (str protocol)
                                  :FromPort (str from-port)
                                  :ToPort (str to-port)})]
        (map #(merge record {:IpRanges %}) ip-ranges)))
  ([protocol ip-ranges]
     (sg-rule protocol nil nil ip-ranges)))
