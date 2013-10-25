(ns shuppet.securitygroups
  (:require
   [shuppet.aws :refer [ec2-request]]
   [shuppet.util :refer :all]
   [clojure.tools.logging :as log]
   [clojure.data.zip.xml :refer [xml1-> text xml->]]))

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

(defn- process
  [action params]
  (condp = (keyword action)
    :CreateSecurityGroup (create params)
    (ec2-request (merge params {"Action" (name action)}))))

(defn- network-query-params
  [index [k v]]
  (if (= (name k) "IpRanges")
    {(str "IpPermissions." index ".IpRanges.1.CidrIp") v}
    {(str "IpPermissions." index "." (name k)) v}))

(defn- build-network-params
  [index opts]
  (into {} (map #(network-query-params index %1) opts)))

(defn- network-action
  [sg-id opts action]
  (let [params (into {} (map #(build-network-params %1 %2) (iterate inc 1) opts))]
    (process action (merge params {"GroupId" sg-id}))))

(defn- check-default-egress [opts]
  (let [egress (get opts :Egress)]
    (filter #(and (not= (get % :IpRanges) "0.0.0.0/0")
                  (not= (str (get % :IpPermission)) "-1")) egress) ))

(defn- configure-network
  "Applies all the ingress rules to the new security group.
   For egress rules, we get rid of 0.0.0.0/0 for ALL protocols from the config map,
   which will be created by default when you create a new security group.
   If we get a custom egress rules other than the default one, then we revoke the default egress rule
   and apply the custom ones"
  [sg-id opts]
  (when-let [ingress (get opts :Ingress)]
    (network-action sg-id ingress :AuthorizeSecurityGroupIngress))
  (let [egress (check-default-egress opts)]
    (when-not (empty? egress)
      (network-action sg-id egress :AuthorizeSecurityGroupEgress)
      (network-action sg-id {:IpRanges "0.0.0.0/0" :IpProtocol "-1"} :RevokeSecurityGroupEgress))))

(defn- delete-sg
  [sg-name]
  (let [response (process :DescribeSecurityGroups {"Filter.1.Name" "group-name"
                                                   "Filter.1.Value" sg-name})]
    (if-let [sg-id (xml1-> response :securityGroupInfo :item :groupId text)]
      (process :DeleteSecurityGroup {"GroupId" sg-id}))))

(defn- build-sg
  [opts]
  (delete-sg (get opts :GroupName))
  (if-let [sg-id (create opts)]
    (configure-network sg-id opts)
    (prn "security group already exists")))

(defn- filter-params
  [opts]
  {"Filter.1.Name" "group-name"
   "Filter.1.Value" (get opts :GroupName)
   "Filter.2.Name" "vpc-id"
   "Filter.2.Value" (get opts :VpcId)
   "Filter.3.Name" "description"
   "Filter.3.Value" (get opts :GroupDescription)})

(defn- network-config
  [params]
  (group-record (xml1-> params :ipProtocol text)
                (xml1-> params :fromPort text)
                (xml1-> params :toPort text)
                (vec (xml-> params :ipRanges :item :cidrIp text))))


(defn- build-config
  [opts]
  {:Ingress (flatten (map network-config (xml-> opts :securityGroupInfo :item :ipPermissions :item)))
   :Egress (flatten (map network-config (xml-> opts :securityGroupInfo :item :ipPermissionsEgress :item)))})

(defn- balance-ingress
  [sg-id opts]
  (let [revoke-config (nth opts 0)
        add-config (nth opts 1)]
    (when-not (empty? add-config)
      (network-action sg-id add-config :AuthorizeSecurityGroupIngress))
    (when-not (empty? revoke-config)
      (network-action sg-id revoke-config :RevokeSecurityGroupIngress))))

(defn- balance-egress
  [sg-id opts]
  (let [revoke-config (nth opts 0)
        add-config (nth opts 1)]
    (when-not (empty? add-config)
      (network-action sg-id add-config :AuthorizeSecurityGroupEgress))
    (when-not (empty? revoke-config)
      (network-action sg-id revoke-config :RevokeSecurityGroupEgress))))

(defn- compare-sg
  [sg-id aws local]
  (let [remote (build-config aws)
        ingress (compare-config  (get local :Ingress) (get remote :Ingress))
        egress  (compare-config (get local :Egress) (get remote :Egress))]
    (balance-ingress sg-id ingress)
    (balance-egress sg-id egress)))

(defn- apply-defaults
  [opts defaults key]
  (if (empty? (get opts key))
    (assoc opts key (get defaults key))
    opts))

(defn ingress-egress
  [opts defaults]
  (let [params (apply-defaults opts defaults :Ingress)]
    (apply-defaults params defaults :Egress)))

(defn ensure-sg
  "Get details of the security group, if one exists
   if not present create and apply ingress/outgress
   if present compare with the local config and apply changes if needed"
  [opts]
  (let [opts (-> opts
              (assoc :Ingress (flatten (:Ingress opts)))
              (assoc :Egress (flatten (:Egress opts))))
        response (process :DescribeSecurityGroups (filter-params opts))]
    (if-let [sg-id (first (xml-> response :securityGroupInfo :item :groupId text)) ]
      (compare-sg sg-id response opts)
      (build-sg opts))))
