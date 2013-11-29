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
;Here we define a configuration as a Clojure map, it must be at the end of the file to be used by Shuppet.

{
;Elastic load balancer doc http://docs.aws.amazon.com/ElasticLoadBalancing/latest/APIReference/Welcome.html
 :LoadBalancer {:LoadBalancerName $app-name
                :Listeners [elb-8080->8080]
                :SecurityGroups [$sg-http-8080]
                :Subnets $elb-subnets-be
                :Scheme "internal"
                :HealthCheck elb-healthcheck-ping}
;IAM role doc http://docs.aws.amazon.com/IAM/latest/APIReference/Welcome.html
 :Role {:RoleName $app-name
        ; This tool http://awspolicygen.s3.amazonaws.com/policygen.html helps to create policy documents
        }
 }
