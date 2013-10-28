(def vpc-id "vpc-7bc88713")
(def campfire-room "Shuppet-test")

(def sg-ingress [(group-record "tcp" 80 8080 ["10.216.221.0/24" "10.83.1.0/24"])])
(def sg-egress  [(group-record "-1" ["0.0.0.0/0"])])

(def elb-subnets ["subnet-24df904c" "subnet-bdc08fd5"])
