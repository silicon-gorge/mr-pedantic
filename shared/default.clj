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

{:SecurityGroups [{:GroupName elb-sg-name
                   :GroupDescription (str "Security group for load balancer " $app-name)
                   :VpcId $vpc-id
                   :Ingress [{:IpRanges $private-ips
                              :IpProtocol "tcp"
                              :FromPort 8080
                              :ToPort 8080}]
                   :Egress $sg-egress-all}
                  {:GroupName $app-name
                   :GroupDescription (str "Security group for application " $app-name)
                   :VpcId $vpc-id
                   :Ingress [{:IpRanges elb-sg-name
                              :IpProtocol "tcp"
                              :FromPort 8080
                              :ToPort 8080}]
                   :Egress $sg-egress-all}]

 :LoadBalancer {:LoadBalancerName $app-name
                :Listeners [elb-8080->8080]
                :SecurityGroups [elb-sg-name $sg-ssh]
                :Subnets $elb-subnets-be
                :Scheme "internal"
                :HealthCheck elb-healthcheck-ping}

 :Role {:RoleName $app-name
        :Policies  [{:PolicyName "full-access"
                     :PolicyDocument [{:Action "*",
                                       :Resource "*"}]}]}
 }
