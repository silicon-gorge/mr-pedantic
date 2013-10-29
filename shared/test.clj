(def override-sg-ingress
  [(sg-rule "tcp" 80 8080 '("198.0.2.0/24" "198.51.100.0/24"))])

(def override-sg-egress
  [(sg-rule "tcp" 80 8090 ["198.0.2.0/24" "198.51.100.0/24"])
   (sg-rule "udp" 80 8090 ["198.0.2.0/24" "198.51.100.0/32"])])

(def elb-sg (str app-name "-lb"))

{:SecurityGroups [{:GroupName app-name
                   :GroupDescription (str "Security group for application " app-name)
                   :VpcId vpc-id
                   :Ingress sg-ingress
                   :Egress sg-egress}
                  {:GroupName elb-sg
                   :GroupDescription (str "Security group for load balancer " app-name)
                   :VpcId vpc-id
                   :Ingress override-sg-ingress
                   :Egress override-sg-egress }]

 :LoadBalancer {:LoadBalancerName app-name
                :Listeners [elb-8080->8080
                            (elb-listener 80 8080 "http")]
                :SecurityGroups [elb-sg]
                :Subnets elb-subnets
                :Scheme "internal"
                :HealthCheck elb-healthcheck-ping}

 :Campfire [cf-shuppet]}
