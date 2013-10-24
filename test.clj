(defn- sg-ingress-egress
  []
  (condp = (get default-config :Environment)
    :dev {:Ingress (flatten [(group-record "tcp" 80 8080 '("192.0.2.0/24" "192.51.100.0/24"))
                             (group-record "udp" 80 8080 '("192.0.2.0/24" "192.51.100.0/32"))])
          }
    :prod {:Ingress (flatten [(group-record "tcp" 80 8080 '("198.0.2.0/24" "198.51.100.0/24"))
                              (group-record "udp" 80 8080 '("198.0.2.0/24" "198.51.100.0/32"))])
           :Egress  (flatten [(group-record "tcp" 80 8090 '("198.0.2.0/24" "198.51.100.0/24"))
                              (group-record "udp" 80 8090 '("198.0.2.0/24" "198.51.100.0/32"))])}))

(defn- sg-params
  []
  (merge (ingress-egress (sg-ingress-egress) (get default-config :SecurityGroups))
           {:GroupName (str (get default-config :Application) "-sg")
            :GroupDescription (str "Security group for the application " (get default-config :Application))
            :VpcId (get default-config :VpcId)}))

(if (true? (get default-config :PrintJson))
  (prn (json-str (merge {:SecurityGroups (sg-params)}
                        {:Environment (get default-config :Environment)})))
  (ensure-sg (sg-params)))
