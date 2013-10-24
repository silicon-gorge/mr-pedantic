(def ^:const vpc-id "vpc-7bc88713")

(defn- default-params
  []
  {:VpcId vpc-id})

(defn- sg-ingress-egress
  []
  {:Ingress (vec (flatten [(group-record "tcp" 80 8080 '("10.216.221.0/24" "10.83.1.0/24"))]))
   :Egress  (vec (flatten [(group-record "-1"  '("0.0.0.0/0"))]))})


(merge
 {:SecurityGroups (sg-ingress-egress)}
 (default-params))
