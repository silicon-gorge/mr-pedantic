(ns shuppet.securitygroups
  (:require
   [shuppet.aws :refer [ec2-request]]
   [shuppet.util :refer :all]
   [clojure.tools.logging :as log]
   [clojure.data.zip.xml :refer [xml1-> text xml->]]))

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
  (without-nils {"GroupName" (get opts :GroupName)
                 "GroupDescription" (get opts :GroupDescription)
                 "VpcId" (get opts :VpcId)}))

(defn- create
  "Creates a security group and returns the id"
  [opts]
  (let [params (create-params opts)]
    (if-let [response (ec2-request (merge params {"Action" "CreateSecurityGroup"}))]
      (do
        (xml1-> response :groupId text)))))

(defn- process [action params]
  (condp = (keyword action)
    :CreateSecurityGroup (create params)
    (ec2-request (merge params {"Action" (name action)}))))

(defn- network-action [sg-id opts action]
  (let [params (process-list-of-maps build-network-params opts)]
    (process action (merge params {"GroupId" sg-id}))))

(defn- configure-network [sg-id opts]
  (when-let [ingress (get opts :Ingress)]
    (network-action sg-id ingress :AuthorizeSecurityGroupIngress))
  (when-let [egress (get opts :Egress)]
    (network-action sg-id egress :AuthorizeSecurityGroupEgress)))

(defn- delete-sg [sg-name]
  (let [response (process :DescribeSecurityGroups {"Filter.1.Name" "group-name"
                                                   "Filter.1.Value" sg-name})]
    (if-let [sg-id (xml1-> response :securityGroupInfo :item :groupId text)]
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
  (without-nils {:IpProtocol (xml1-> params :ipProtocol text)
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
        ingress (compare-config  (get local :Ingress) (expand remote :Ingress :IpRanges))
        egress  (compare-config (get local :Egress) (expand remote :Egress :IpRanges))]
    (balance-ingress sg-id ingress)
    (balance-egress sg-id egress)))

(defn ensure-sg
  "Get details of the security group, if one exists
   if not present create and apply ingress/outgress
   if present compare with the local config and apply changes if needed"
  [opts]
  (let [response (process :DescribeSecurityGroups (filter-params opts))]
    (if-let [sg-id (first (xml-> response :securityGroupInfo :item :groupId text)) ]
      (compare-sg sg-id response opts)
      (build-sg opts))))
