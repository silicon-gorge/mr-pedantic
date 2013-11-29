;Here we define a bunch of variables to make our config shorter and easier to understand
(def elb-8080->8080  {:LoadBalancerPort 8080
                      :InstancePort 8080
                      :Protocol "http"
                      :InstanceProtocol "http"})
(def elb-healthcheck-ping {:Target "HTTP:8080/1.x/ping"
                           :HealthyThreshold 2
                           :UnhealthyThreshold 4
                           :Interval 12
                           :Timeout 5})
(def elb-sg-name (str $app-name "-lb"));$app-name is made available by Shuppet
;Here we define a configuration as a Clojure map, it must be at the end of the file to be used by Shuppet.
;Security group doc http://docs.aws.amazon.com/AWSEC2/latest/APIReference/Welcome.html
{:SecurityGroups [;Define a security group for our elb
                  {:GroupName elb-sg-name
                   :GroupDescription (str "Security group for load balancer " $app-name)
                   :VpcId $vpc-id
                   :Ingress [{:IpRanges $private-ips
                              :IpProtocol "tcp"
                              :FromPort 8080
                              :ToPort 8080}]
                   :Egress $sg-egress-all}
                  ;Define a security group for our application so only our elb can talk to it
                  {:GroupName $app-name
                   :GroupDescription (str "Security group for application " $app-name)
                   :VpcId $vpc-id
                   :Ingress [{:IpRanges elb-sg-name
                              :IpProtocol "tcp"
                              :FromPort 8080
                              :ToPort 8080}]
                   :Egress $sg-egress-all}]
;Elastic load balancer doc http://docs.aws.amazon.com/ElasticLoadBalancing/latest/APIReference/Welcome.html
 :LoadBalancer {:LoadBalancerName $app-name
                :Listeners [elb-8080->8080]
                :SecurityGroups [elb-sg-name $sg-ssh]
                :Subnets $elb-subnets-be
                :Scheme "internal"
                :HealthCheck elb-healthcheck-ping}
;IAM role doc http://docs.aws.amazon.com/IAM/latest/APIReference/Welcome.html
 :Role {:RoleName $app-name
        ; This tool http://awspolicygen.s3.amazonaws.com/policygen.html helps to create policy documents
        }
 }
