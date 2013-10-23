(ns shuppet.securitygroups
  (:require
   [shuppet.aws :refer [ec2-request]]
   [clj-http.client :as client]
   [clojure.tools.logging :as log]
   [clojure.xml :as xml]
   [clojure.zip :as zip]
   [clojure.data.zip.xml :refer [xml1-> text xml->]]))


(defn- xml-to-map [xml-string]
  (zip/xml-zip (xml/parse (java.io.ByteArrayInputStream. (.getBytes xml-string)))))

(defn- remove-nil [params]
  (apply dissoc
         params
         (for [[k v] params :when (nil? v)] k)))

(defn- in?
  "true if seq contains element"
  [seq element]
  (some #(= element %) seq))

(defn list-to-member [prefix list]
  (apply hash-map (flatten (map #(do [(str prefix ".member." %1) %2]) (iterate inc 1) list))))

(defn- build-network-params [index [k v]]
  (if (= (name k) "IpRanges")
    {(str "IpPermissions." index ".IpRanges.1.CidrIp") v}
    {(str "IpPermissions." index "." (name k)) v}))

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

(defn- nested-level [opts val]
  (assoc opts :IpRanges val))

(defn- first-level [sub-key opts]
  (map #(nested-level opts %1) (get opts sub-key)))

(defn- expand [opts key sub-key]
  (flatten (map #(first-level sub-key %1) (get opts key))))

(defn- compare-config
  "Returns a list of two vectors
   First vector is what is present in the remote config , which are not present in the local config
   and the second vector is those present in the local config, which are not applied to the aws config yet"
  [local remote]
  (concat (list (vec (filter #(not (in? (set local) %)) (set remote))))
          (list (vec (filter #(not (in? (set remote) %)) (set local))))))

(defn- create-params [opts]
  (remove-nil {"GroupName" (get opts :GroupName)
               "GroupDescription" (get opts :GroupDescription)
               "VpcId" (get opts :VpcId)}))

(defn- create
  "Creates a security group and returns the id"
  [opts]
  (let [params (create-params opts)]
    (if-let [response (ec2-request (merge params {"Action" "CreateSecurityGroup"}))]
      (do
        (xml1-> (xml-to-map response) :groupId text)))))

(defn- process [action params]
  (condp = (keyword action)
    :CreateSecurityGroup (create params)
    (ec2-request (merge params {"Action" (name action)}))))

(defn- network-action [sg-id opts action]
  (let [params (process-list-of-maps build-network-params opts)]
    (process action (merge params {"GroupId" sg-id}))))

(defn- configure-network [sg-id opts]
  (when-let [ingress (expand opts :Ingress :IpRanges)]
    (network-action sg-id ingress :AuthorizeSecurityGroupIngress))
  (when-let [egress (expand opts  :Egress :IpRanges)]
    (network-action sg-id egress :AuthorizeSecurityGroupEgress)))

(defn- delete-sg [sg-name]
  (let [response (xml-to-map (process :DescribeSecurityGroups {"Filter.1.Name" "group-name"
                                                               "Filter.1.Value" sg-name} ))]
    (if-let [sg-id (xml1-> response :securityGroupInfo :item :groupId text) ]
      (process :DeleteSecurityGroup {"GroupId" sg-id}))))

(defn- build-sg [opts]
  (delete-sg (get opts :GroupName))
  (if-let [sg-id (create opts)]
    (configure-network sg-id opts)
    (prn "security group already exists")))

(defn- filter-params [opts]
  {"Filter.1.Name" "group-name"
   "Filter.1.Value" (get opts :GroupName)
   "Filter.2.Name" "vpc-id"
   "Filter.2.Value" (get opts :VpcId)
   "Filter.3.Name" "description"
   "Filter.3.Value" (get opts :GroupDescription)})

(defn- network-config [params]
  (remove-nil {:IpProtocol (xml1-> params :ipProtocol text)
               :FromPort (xml1-> params :fromPort text)
               :ToPort (xml1-> params :toPort text)
               :IpRanges (vec (xml-> params :ipRanges :item :cidrIp text))}))

(defn- build-config [opts]
  {:Ingress (vec (map network-config (xml-> opts :securityGroupInfo :item :ipPermissions :item)))
   :Egress (vec (map network-config (xml-> opts :securityGroupInfo :item :ipPermissionsEgress :item)))})

(defn- balance-ingress [sg-id opts]
  (let [revoke-config (nth opts 0)
        add-config (nth opts 1)]
    (when-not (empty? add-config)
      (network-action sg-id add-config :AuthorizeSecurityGroupIngress))
    (when-not (empty? revoke-config)
      (network-action sg-id revoke-config :RevokeSecurityGroupIngress))))

(defn- balance-egress [sg-id opts]
  (let [revoke-config (nth opts 0)
        add-config (nth opts 1)]
    (when-not (empty? add-config)
      (network-action sg-id add-config :AuthorizeSecurityGroupEgress))
    (when-not (empty? revoke-config)
      (network-action sg-id revoke-config :RevokeSecurityGroupEgress))))

(defn- compare-sg [sg-id aws local]
  (let [remote (build-config aws)
        ingress (compare-config  (expand local :Ingress :IpRanges) (expand remote :Ingress :IpRanges))
        egress  (compare-config  (expand local :Egress :IpRanges) (expand remote :Egress :IpRanges))]
    (balance-ingress sg-id ingress)
    (balance-egress sg-id egress)))

(defn ensure
  "Describe the security security group
   if not present create and apply ingress/outgress
   if present compare with the local config and apply changes if needed"
  [opts]
  (let [response (xml-to-map (process :DescribeSecurityGroups (filter-params opts)))]
    (if-let [sg-id (first (xml-> response :securityGroupInfo :item :groupId text)) ]
      (compare-sg sg-id response opts)
      (build-sg opts))))


(defn sg []
  {:GroupName "test-sg6"
   :GroupDescription "test description"
   :VpcId "vpc-7bc88713"
   :Ingress [{:IpProtocol "tcp"
              :FromPort "80"
              :ToPort "80"
              :IpRanges ["192.212.2.0/24" "193.182.100.0/24" ]
              },
             {:IpProtocol "udp"
              :FromPort "80"
              :ToPort "8080"
              :IpRanges ["198.51.100.0/24", "192.0.2.0/24" ]
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
