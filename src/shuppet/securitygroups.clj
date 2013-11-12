(ns shuppet.securitygroups
  (:require
   [shuppet
    [signature :refer [v2-url]]
    [util :refer :all]
    [campfire :as cf]]
   [environ.core :refer [env]]
   [clj-http.client :as client]
   [clojure.tools.logging :as log]
   [clojure.data.zip.xml :refer [xml1-> text xml->]]
   [clojure.xml :as xml]
   [clojure.zip :as zip]
   [slingshot.slingshot :refer [try+ throw+]]))

(def ^:const ^:private ec2-url (env :service-aws-ec2-url))
(def ^:const ^:private ec2-version (env :service-aws-ec2-api-version))

(defn- get-request
  [params]
  (let [url (v2-url ec2-url (merge {"Version" ec2-version} params))
        response (client/get url {:as :stream
                                  :throw-exceptions false})
        status (:status response)
        body (-> (:body response)
                 (xml/parse)
                 (zip/xml-zip))]
    (if (= 200 status)
      body
      (throw-aws-exception "EC2" (get params "Action") url status body))))

(defn- create-params [opts]
  (without-nils {"GroupName" (:GroupName opts)
                 "GroupDescription" (:GroupDescription opts)
                 "VpcId" (:VpcId opts)}))

(defn- create
  "Creates a security group and returns the id"
  [opts]
  (let [params (create-params opts)]
    (if-let [response (get-request (merge params {"Action" "CreateSecurityGroup"}))]
      (do
        (cf/info (str "I've created a new security group called '" (:GroupName opts) "'"))
        (xml1-> response :groupId text)))))

(defn- process
  [action params]
  (condp = (keyword action)
    :CreateSecurityGroup (create params)
    (get-request (merge params {"Action" (name action)}))))

(defn sg-id
  "Gets the sg-id for the for the given sg-name"
  [name]
  (xml1-> (process :DescribeSecurityGroups {"Filter.1.Name" "group-name"
                                            "Filter.1.Value" name})
          :securityGroupInfo :item :groupId text))

(defn- ip-ranges-param [index v]
  (if (empty? (re-find #"[a-z]*" v))
    {(str "IpPermissions." index ".IpRanges.1.CidrIp") v}
    {(str "IpPermissions." index ".Groups.1.GroupId") v}))

(defn- network-query-params
  [index [k v]]
  (if (= (name k) "IpRanges")
    (ip-ranges-param index v)
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
    (do
      (network-action sg-id ingress :AuthorizeSecurityGroupIngress)
      (cf/info (str "I've added the ingress rules '" (vec ingress) "' for the security group '" (:GroupName opts) "'"))))
  (let [egress (check-default-egress opts)]
    (when-not (empty? egress)
      (network-action sg-id egress :AuthorizeSecurityGroupEgress)
      (cf/info (str "I've added the egress rules '" (vec egress) "' for the security group '" (:GroupName opts) "'"))
      (network-action sg-id (list {:IpRanges "0.0.0.0/0" :IpProtocol "-1"}) :RevokeSecurityGroupEgress))))

(defn- network-config
  [params]
  (let [ip-ranges (vec (xml-> params :ipRanges :item :cidrIp text))
        ip-ranges (if (empty? ip-ranges) (xml-> params :groups :item :groupId text) ip-ranges)]
    (sg-rule (xml1-> params :ipProtocol text)
             (xml1-> params :fromPort text)
             (xml1-> params :toPort text)
             ip-ranges)))

(defn- build-config
  [opts]
  {:Ingress (flatten (map network-config (xml-> opts :securityGroupInfo :item :ipPermissions :item)))
   :Egress (flatten (map network-config (xml-> opts :securityGroupInfo :item :ipPermissionsEgress :item)))})

(defn- ensure-ingress
  [sg-name sg-id [revoke-config add-config]]
  (when-not (empty? add-config)
    (network-action sg-id add-config :AuthorizeSecurityGroupIngress)
    (cf/info (str "I've applied the new ingress rule '" (vec add-config) "' to the existing security group '" sg-name "'")))
  (when-not (empty? revoke-config)
    (network-action sg-id revoke-config :RevokeSecurityGroupIngress)
    (cf/info (str "I've revoked the ingress rule '" (vec revoke-config) "' from the existing security group '" sg-name "'"))))

(defn- ensure-egress
  [sg-name sg-id [revoke-config add-config]]
  (when-not (empty? add-config)
    (network-action sg-id add-config :AuthorizeSecurityGroupEgress)
    (cf/info (str "I've applied the new egress rule '" (vec add-config) "' to the existing security group '" sg-name "'")))
  (when-not (empty? revoke-config)
    (network-action sg-id revoke-config :RevokeSecurityGroupEgress)
    (cf/info (str "I've revoked the egress rule '" (vec revoke-config) "' from the existing security group '" sg-name "'"))))

(defn- compare-sg
  [sg-id aws local]
  (let [remote (build-config aws)
        ingress (compare-config  (:Ingress local) (:Ingress remote))
        egress  (compare-config (:Egress local) (:Egress remote))]
    (ensure-ingress (:GroupName local) sg-id ingress)
    (ensure-egress (:GroupName local) sg-id egress)))

(defn- delete-sg
  [name]
  (if-let [id (sg-id name)]
    (do
      (process :DeleteSecurityGroup {"GroupId" id})
      (cf/info  (str "I've succesfully deleted the security group '" name "' with id: " id)))))

(defn- build-sg
  [opts]
  (delete-sg (:GroupName opts))
  (if-let [sg-id (create opts)]
    (try+
     (do
       (configure-network sg-id opts)
       (cf/info (str "I've succesfully created and configured the security group '" (:GroupName opts) "'")))
     (catch map? error
       (delete-sg (:GroupName opts))
       (throw+ error)))))

(defn- filter-params
  [opts]
  {"Filter.1.Name" "group-name"
   "Filter.1.Value" (:GroupName opts)
   "Filter.2.Name" "vpc-id"
   "Filter.2.Value" (:VpcId opts)
   "Filter.3.Name" "description"
   "Filter.3.Value" (:GroupDescription opts)})

(defn- update-sg-id
  [name]
  (if (and (empty? (re-find #"[\d\/.]*" name))
           (empty? (re-find #"^sg-" name)))
    (if-let [id (sg-id name)]
      id
      (throw-aws-exception "EC2"
                           "DescribeSecurityGroups"
                           "config-check"
                           404
                           {:message (str  "A security group with the name '" name "' cannot be found.")
                            :__type "Bad Configuration"}
                           :json)) name))

(defn- update-sg-ids
  [opts]
  (->> opts
       (map sg-rule)
       (flatten)
       (map #(update-in % [:IpRanges] update-sg-id))))

(defn- ensure-sg
  "Get details of the security group, if one exists
   if not present create and apply ingress/outgress
   if present compare with the local config and apply changes if needed"
  [opts]
  (let [opts (-> opts
                 (assoc :Ingress (update-sg-ids (:Ingress opts)))
                 (assoc :Egress (update-sg-ids (:Egress opts))))
        response (process :DescribeSecurityGroups (filter-params opts))]
    (if-let [sg-id (xml1-> response :securityGroupInfo :item :groupId text)]
      (compare-sg sg-id response opts)
      (build-sg opts))))

(defn ensure-sgs
  [{:keys [SecurityGroups]}]
  (doseq [opts SecurityGroups]
    (ensure-sg opts)))

(defn delete-sgs
  [{:keys [SecurityGroups]}]
  (doseq [group SecurityGroups]
    (delete-sg (:GroupName group))))
