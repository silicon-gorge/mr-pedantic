(def elb-8080->8080  {:LoadBalancerPort 8080
                      :InstancePort 8080
                      :Protocol "http"
                      :InstanceProtocol "http"})
(def elb-healthcheck-ping {:Target "HTTP:8080/1.x/ping"
                           :HealthyThreshold 2
                           :UnhealthyThreshold 2
                           :Interval 6
                           :Timeout 5})
(def elb-sg-name (str $app-name "-lb"))

{:SecurityGroups [{:GroupName $app-name
                   :GroupDescription (str "Security group for application " $app-name)
                   :VpcId $vpc-id
                   :Ingress [{:IpRanges ["sg-14fe1a7b" "Brislabs-SSH"]
                             :IpProtocol "tcp"
                             :FromPort 80
                             :ToPort 8080}]
                   :Egress $sg-egress}
                  {:GroupName elb-sg-name
                   :GroupDescription (str "Security group for load balancer " $app-name)
                   :VpcId $vpc-id
                   :Ingress $sg-ingress
                   :Egress [{:IpRanges ["198.0.2.0/24" "198.51.100.0/24"]
                             :IpProtocol "tcp"
                             :FromPort 80
                             :ToPort 8080}
                            {:IpRanges "198.100.2.0/24"
                             :IpProtocol "udp"
                             :FromPort 80
                             :ToPort 8080}
                            ]}]

 :LoadBalancer {:LoadBalancerName $app-name
                :Listeners [elb-8080->8080]
                :SecurityGroups [elb-sg-name]
                :Subnets $elb-subnets
                :Scheme "internal"
                :HealthCheck elb-healthcheck-ping}

 :Campfire [$cf-shuppet]}
