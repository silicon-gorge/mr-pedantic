(def elb-8080->8080  {:LoadBalancerPort 8080
                      :InstancePort 8080
                      :Protocol "http"
                      :InstanceProtocol "http"})
(def elb-healthcheck-ping {:Target "HTTP:8080/1.x/ping"
                           :HealthyThreshold 2
                           :UnhealthyThreshold 4
                           :Interval 12
                           :Timeout 5})
(def elb-sg-name (str $app-name "-lb"))

{:SecurityGroups [{:GroupName $app-name
                   :GroupDescription (str "Security group for application " $app-name)
                   :VpcId $vpc-id
                   :Ingress $sg-ingress
                   :Egress $sg-egress}
                  {:GroupName elb-sg-name
                   :GroupDescription (str "Security group for load balancer " $app-name)
                   :VpcId $vpc-id
                   :Ingress $sg-ingress
                   :Egress $sg-egress}]

 :LoadBalancer {:LoadBalancerName $app-name
                :Listeners [elb-8080->8080]
                :SecurityGroups [elb-sg-name "Brislabs-HTTPS" "Brislabs-SSH" "Brislabs-8080" "Brislabs-HTTP"]
                :Subnets $elb-subnets
                :Scheme "internal"
                :HealthCheck elb-healthcheck-ping}
}
