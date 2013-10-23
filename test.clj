(defn- ingress-egress []
  (condp = (keyword environment)
    :dev {:Ingress (flatten [(group-record "tcp" 80 8080 '("192.0.2.0/24" "192.51.100.0/24"))
                             (group-record "udp" 80 8080 '("192.0.2.0/24" "192.51.100.0/32"))])
          :Egress  (flatten [(group-record "tcp" 80 8090 '("192.0.2.0/24" "192.51.100.0/24"))
                             (group-record "udp" 80 8090 '("192.0.2.0/24" "192.51.100.0/32"))])}
    :prod {:Ingress (flatten [(group-record "tcp" 80 8080 '("198.0.2.0/24" "198.51.100.0/24"))
                              (group-record "udp" 80 8080 '("198.0.2.0/24" "198.51.100.0/32"))])
           :Egress  (flatten [(group-record "tcp" 80 8090 '("198.0.2.0/24" "198.51.100.0/24"))
                              (group-record "udp" 80 8090 '("198.0.2.0/24" "198.51.100.0/32"))])}))

(defn- sg-params []
  (merge (ingress-egress)
         {:GroupName (str application-name "-sg")
          :GroupDescription (str "Security group for the application " application-name)
          :VpcId vpc-id}))

(if (= action "print")
  (prn (json-str (merge (sg-params) {:Environment environment})))
  (ensure-sg (sg-params)))
