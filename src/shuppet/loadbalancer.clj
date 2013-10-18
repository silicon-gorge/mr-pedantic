(ns shuppet.loadbalancer
  (:require
   [shuppet.aws :refer [elb-request]]
   [clj-http.client :as client]
   [clojure.tools.logging :as log]
   [clojure.xml :as xml]
   [clojure.zip :as zip]
   [clojure.data.zip.xml :refer [xml1-> attr xml-> text text= attr=]]))



; get 200 when same config
#(elb-request {"Action" "CreateLoadBalancer"
              "LoadBalancerName" "recommendations"
              "Listeners.member.1.LoadBalancerPort" 8080
              "Listeners.member.1.InstancePort"  8080
              "Listeners.member.1.Protocol" "http"
              "Listeners.member.1.InstanceProtocol" "http"
              "Listeners.member.2.LoadBalancerPort" 80
              "Listeners.member.2.InstancePort"  8080
              "Listeners.member.2.Protocol" "http"
              "Listeners.member.2.InstanceProtocol" "http"
              "Subnets.member.1" "subnet-24df904c"
              "Subnets.member.2" "subnet-bdc08fd5"
              "Scheme" "internal"
              "SecurityGroups.member.1" "recommendations-lb"
              "AvailabilityZones.member.1" "eu-west-1a"
              "AvailabilityZones.member.2" "eu-west-1b"})


#(elb-request {"Action" "CreateLoadBalancerListeners"
               "LoadBalancerName" "recommendations"
               "Listeners.member.1.LoadBalancerPort" 80
               "Listeners.member.1.InstancePort" 8080
               "Listeners.member.1.InstanceProtocol" "http"
               "Listeners.member.1.Protocol" "http"})

#(elb-request {"Action" "DeleteLoadBalancerListeners"
              "LoadBalancerName" "recommendations"
              "LoadBalancerPorts.member.1" "80"})


#(elb-request {"Action" "DescribeLoadBalancers"
               "LoadBalancerNames.member.1" "recommendations"})

#(elb-request {"Action" "DeleteLoadBalancer"
              "LoadBalancerName" "test-elb-nico"})
