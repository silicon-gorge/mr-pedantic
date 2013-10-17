(ns shuppet.aws
  (:require
   [clj-http.client :as client]
   [environ.core :refer [env]]
   [shuppet.urlbuilder :as urlbuilder]
   [clojure.tools.logging :as log]))

(def ^:const ec2-url (or (env :service-aws-ec2-url) "https://ec2.eu-west-1.amazonaws.com"))
(def ^:const ec2-version (or (env :service-aws-ec2-api-version) "2013-10-01"))

(defn ec2-request [params]
  (let [url (urlbuilder/build-url ec2-url (merge {"Version" ec2-version} params))
        response (client/get url {:as :xml
                                  :throw-exceptions false})]
    (if (= 200 (get response :status))
      (get response :body)
      (log/info (str "EC2 request : " url "\n failed with response : " response)))))

(defn elb-request [params]
  (client/get (urlbuilder/build-url
               (env :service-aws-elb-url)
               (merge {"Version" (env  :service-aws-elb-version)}  params))
              {:as :xml
               :throw-exceptions false}))


#(elb-request {"Action" "CreateLoadBalancer"
              "LoadBalancerName" "test-elb-nico"
              "Listeners.member.1.LoadBalancerPort" 80
              "Listeners.member.1.InstancePort"  80
              "Listeners.member.1.Protocol" "http"
              "Listeners.member.1.InstanceProtocol" "http"
              "Subnets.member.1" "subnet-24df904c"
              "Subnets.member.2" "subnet-bdc08fd5"
              "Scheme" "internal"})

#(elb-request {"Action" "DescribeLoadBalancers"
               "LoadBalancerNames.member.1" "test-elb-nico"})

#(elb-request {"Action" "DeleteLoadBalancer"
              "LoadBalancerName" "test-elb-nico"})
