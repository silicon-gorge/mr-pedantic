(ns shuppet.securitygroups
  (:require
   [shuppet.aws :refer [ec2-request]]
   [clj-http.client :as client]
   [clojure.tools.logging :as log]
   [clojure.xml :as xml]
   [clojure.zip :as zip]
   [clojure.data.zip.xml :refer [xml1-> attr xml-> text text= attr=]]))


(defn- xml-to-map [xml-string]
  (zip/xml-zip (xml/parse (java.io.ByteArrayInputStream. (.getBytes xml-string)))))

(defn- remove-nil [params]
  (apply dissoc
         params
         (for [[k v] params :when (nil? v)] k)))

(defn- process-list-of-maps [func opts]
  (loop [count (count opts)
         index (dec count)
         result (into {}
                      (map #(func count  %)
                           (nth opts index))) ]
    (if (= (dec count) 0)
      result
      (recur (dec count)
             (dec index)
             (into result
                   (map #(func (dec count) %)
                        (nth opts (dec index))))))))

(defn- process-list [func i opts]
  (loop [count (count opts)
         index (dec count)
         result (into {}
                      (func i
                            count
                            (nth opts index))) ]
    (if (= (dec count) 0)
      result
      (recur (dec count)
             (dec index)
             (into result
                   (func i
                         (dec count)
                         (nth opts (dec index))))))))



(defn- create-params [opts]
  (remove-nil {"GroupName" (get opts :GroupName)
               "GroupDescription" (get opts :GroupDescription)
               "VpcId" (get opts :VpcId)}))


(defn create
  "Creates a security group and returns the id"
  [opts]
  (let [cr-params (create-params opts)]
    (if-let [response (ec2-request (merge cr-params {"Action" "CreateSecurityGroup"}))]
      (do
        (first (xml-> (xml-to-map response) :groupId text))))))

(defn compare-sg [current-config opts]
  (prn "lets compare the configs here  TODO" current-config))

(defn process [action params]
  (condp = (keyword action)
    :CreateSecurityGroup (create params)
    (ec2-request (merge params {"Action" (name action)}))))

(defn- sg-id [opts]
  (if-let [id (get opts :GroupId)]
    {"GroupId" id}
    {"GroupName" (get opts :GroupName)}))

(defn- build-ip-range-params [i1 i2 vals]
  {(str "IpPermissions." i1 ".IpRanges." i2 ".CidrIp") vals})

(defn- build-network-params [index [k v]]
  (if (= (name k) "IpRanges")
    (process-list build-ip-range-params index v)
    {(str "IpPermissions." index "." (name k)) v}))


(defn- apply-ingress [sg-id ingress]
  (let [params (process-list-of-maps build-network-params ingress)]
    (process "AuthorizeSecurityGroupIngress" (merge params {"GroupId" sg-id}))))

(defn- apply-egress [sg-id outgress]
  (let [params (process-list-of-maps build-network-params outgress) ]
    (process "AuthorizeSecurityGroupEgress" (merge params {"GroupId" sg-id}))))

(defn- configure-network [sg-id opts]
  (when-let [ingress (get opts :Ingress)]
    (apply-ingress sg-id ingress))
  (when-let [egress (get opts :Egress)]
    (apply-egress sg-id egress)))

(defn- build-sg [opts]
  (if-let [sg-id (create opts)]
    (configure-network sg-id opts)
    (prn "security group already exists")))

(defn ensure [opts]
;describe security group
;if not present create and apply ingress/outgress
;if present compare with the config
;delete if needed
  (if-let [response (process :DescribeSecurityGroups (sg-id opts))]
    (compare-sg (xml-to-map response) opts)
    (build-sg opts)))


(defn sg []
  {:GroupName "test-sg5"
   :GroupDescription "test description"
   :VpcId "vpc-7bc88713"
   :Ingress [{:IpProtocol "tcp"
              :FromPort "80"
              :ToPort "80"
              :IpRanges ["192.0.2.0/24" "198.51.100.0/24" ]
              },
             {:IpProtocol "udp"
              :FromPort "80"
              :ToPort "8080"
              :IpRanges ["192.0.2.0/24" "198.51.100.0/24" ]
              }]
   :Egress [{:IpProtocol "tcp"
              :FromPort "80"
              :ToPort "80"
              :IpRanges ["192.0.2.0/24" "198.51.100.0/24" ]
              },
             {:IpProtocol "udp"
              :FromPort "80"
              :ToPort "8080"
              :IpRanges ["192.0.2.0/24" "198.51.100.0/24" ]
              }]})

;(ensure (sg))
