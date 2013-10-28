(def override-sg-ingress
  [(group-record "tcp" 80 8080 '("198.0.2.0/24" "198.51.100.0/24"))])

(def override-sg-egress
  [(group-record "tcp" 80 8090 '("198.0.2.0/24" "198.51.100.0/24"))
   (group-record "udp" 80 8090 '("198.0.2.0/24" "198.51.100.0/32"))])


{:sg {:GroupName (str app-name "-sg")
      :GroupDescription (str "Security group for application " app-name)
      :VpcId @vpc-id
      :Ingress override-sg-ingress
      :Egress override-sg-egress}
 :elb {:LoadBalancerName app-name
       :Listeners [{:LoadBalancerPort "8080"
                    :InstancePort "8080"
                    :Protocol "http"
                    :InstanceProtocol "http"}
                   {:LoadBalancerPort "80"
                    :InstancePort "8080"
                    :Protocol "http"
                    :InstanceProtocol "http"}]
       :SecurityGroups [(str app-name "-sg")]
       :Subnets elb-subnets
       :Scheme "internal"
       :HealthCheck {:Target "HTTP:8080/1.x/ping"
                     :HealthyThreshold "2"
                     :UnhealthyThreshold "2"
                     :Interval "6"
                     :Timeout "5"}}
 :campfire [campfire-room]}
