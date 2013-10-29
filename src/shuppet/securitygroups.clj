(ns shuppet.securitygroups
  (:require
   [shuppet.aws :refer [ec2-request]]
   [shuppet.util :refer :all]
   [clojure.tools.logging :as log]
   [clojure.data.zip.xml :refer [xml1-> text xml->]]
   [slingshot.slingshot :refer [try+ throw+]]))

(defn- create-params [opts]
  (without-nils {"GroupName" (:GroupName opts)
                 "GroupDescription" (:GroupDescription opts)
                 "VpcId" (:VpcId opts)}))

(defn- create
  "Creates a security group and returns the id"
  [opts]
  (let [params (create-params opts)]
    (if-let [response (ec2-request (merge params {"Action" "CreateSecurityGroup"}))]
      (xml1-> response :groupId text))))

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
  (into {} (map #(network-query-params index %) opts)))

(defn- network-action
  [sg-id opts action]
  (let [params (into {} (map #(build-network-params %1 %2) (iterate inc 1) opts))]
    (process action (merge params {"GroupId" sg-id}))))

(defn- check-default-egress [opts]
  (let [egress (:Egress opts)]
    (filter #(and (not= (:IpRanges %) "0.0.0.0/0")
                  (not= (str (:IpPermission %)) "-1")) egress) ))

(defn- configure-network
  "Applies all the ingress rules to the new security group.
   For egress rules, we get rid of 0.0.0.0/0 for ALL protocols from the config map,
   which will be created by default when you create a new security group.
   If we get a custom egress rules other than the default one, then we revoke the default egress rule
   and apply the custom ones"
  [sg-id opts]
  (when-let [ingress (:Ingress opts)]
    (network-action sg-id ingress :AuthorizeSecurityGroupIngress))
  (let [egress (check-default-egress opts)]
    (when-not (empty? egress)
      (network-action sg-id egress :AuthorizeSecurityGroupEgress)
      (network-action sg-id (list {:IpRanges "0.0.0.0/0" :IpProtocol "-1"}) :RevokeSecurityGroupEgress))))

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

(defn- ensure-ingress
  [sg-id opts]
  (let [revoke-config (nth opts 0)
        add-config (nth opts 1)]
    (when-not (empty? add-config)
      (network-action sg-id add-config :AuthorizeSecurityGroupIngress)
      (log/info "Added new ingress rule for security group: " sg-id))
    (when-not (empty? revoke-config)
      (network-action sg-id revoke-config :RevokeSecurityGroupIngress)
      (log/info "Revoked ingress rule for security group: " sg-id))))

(defn- ensure-egress
  [sg-id opts]
  (let [revoke-config (nth opts 0)
        add-config (nth opts 1)]
    (when-not (empty? add-config)
      (network-action sg-id add-config :AuthorizeSecurityGroupEgress)
      (log/info "Added new egress rule for security group: " sg-id))
    (when-not (empty? revoke-config)
      (network-action sg-id revoke-config :RevokeSecurityGroupEgress)
      (log/info "Revoked egress rule for security group: " sg-id))))

(defn- compare-sg
  [sg-id aws local]
  (let [remote (build-config aws)
        ingress (compare-config  (:Ingress local) (:Ingress remote))
        egress  (compare-config (:Egress local) (:Egress remote))]
    (ensure-ingress sg-id ingress)
    (ensure-egress sg-id egress)
    (log/info "Succesfully validated the security group " sg-id " with the shuppet configuration")))

(defn- delete-sg
  [sg-name]
  (let [response (process :DescribeSecurityGroups {"Filter.1.Name" "group-name"
                                                   "Filter.1.Value" sg-name})]
    (if-let [sg-id (xml1-> response :securityGroupInfo :item :groupId text)]
      (do
        (process :DeleteSecurityGroup {"GroupId" sg-id})
        (log/info  "Succesfully deleted security group " sg-name "with id: " sg-id)))))

(defn- build-sg
  [opts]
  (delete-sg (:GroupName opts))
  (if-let [sg-id (create opts)]
    (try+
     (do
       (configure-network sg-id opts)
       (log/info "Succesfully created and configured a security group with the config " opts))
     (catch map? error
       (delete-sg (:GroupName opts))
       (throw+ error)))
    (log/error "Security group already exists for the config " opts)))

(defn- filter-params
  [opts]
  {"Filter.1.Name" "group-name"
   "Filter.1.Value" (:GroupName opts)
   "Filter.2.Name" "vpc-id"
   "Filter.2.Value" (:VpcId opts)
   "Filter.3.Name" "description"
   "Filter.3.Value" (:GroupDescription opts)})

(defn ensure-sg
  "Get details of the security group, if one exists
   if not present create and apply ingress/outgress
   if present compare with the local config and apply changes if needed"
  [opts]
  (let [opts (-> opts
                 (assoc :Ingress (flatten (:Ingress opts)))
                 (assoc :Egress (flatten (:Egress opts))))
        response (process :DescribeSecurityGroups (filter-params opts))]
    (if-let [sg-id (xml1-> response :securityGroupInfo :item :groupId text) ]
      (compare-sg sg-id response opts)
      (build-sg opts))))

(defn ensure-sgs [sg-opts]
  (doseq [opts sg-opts]
    (ensure-sg opts)))
