(def $vpc-id "vpc-7bc88713")
(def $cf-shuppet "Shuppet-test")
(def $sg-ingress [{:IpRanges "10.216.221.0/24"
                   :IpProtocol "tcp"
                   :FromPort 80
                   :ToPort 8080}
                  {:IpRanges  "10.83.1.0/24"
                   :IpProtocol "tcp"
                   :FromPort 80
                   :ToPort 8080}])
(def $sg-egress [{:IpRanges "0.0.0.0/0"
                  :IpProtocol -1}])
(def $elb-subnets ["subnet-24df904c" "subnet-bdc08fd5"])
(def $private-ips ["10.0.0.0/8" "172.16.0.0/12" "192.168.0.0/16"])
(def $sg-egress-all [{:IpRanges "0.0.0.0/0"
                      :IpProtocol -1}])
(def $sg-ssh "test-ssh")

{:SecurityGroups [{:GroupName $sg-ssh
                   :GroupDescription "test SSH"
                   :VpcId $vpc-id
                   :Ingress [{:IpRanges $private-ips
                               :IpProtocol "tcp"
                               :FromPort 22
                               :ToPort 22}]
                   :Egress $sg-egress-all}]}
