(def service-name "test")
(def service-description "test description")

(ensure-sg {:GroupName (lower-case (str service-name "-sg"))
            :GroupDescription service-description
            :VpcId "vpc-7bc88713"
            :Ingress [(group-record "tcp" 80 8080 '("192.0.2.0/24" "198.51.100.0/24"))
                      (group-record "udp" 80 8080 '("192.0.2.0/24" "198.51.100.0/32"))]
            :Egress  [(group-record "tcp" 80 8090 '("192.0.2.0/24" "198.51.100.0/24"))
                      (group-record "udp" 80 8090 '("192.0.2.0/24" "198.51.100.0/32"))]})
