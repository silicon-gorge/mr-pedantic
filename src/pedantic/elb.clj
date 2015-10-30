(ns pedantic.elb
  (:require [amazonica.aws
             [ec2 :as ec2]
             [elasticloadbalancing :as elb]]
            [clj-http.client :as client]
            [clojure
             [data :refer [diff]]
             [set :refer [rename-keys]]
             [string :refer [lower-case upper-case]]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [pedantic
             [aws :as aws]
             [guard :refer [guarded]]
             [report :as report]
             [securitygroups :as sg]
             [subnets :as subnets]
             [util :refer :all :as util]]
            [slingshot.slingshot :refer [throw+ try+]])
  (:import [com.amazonaws.services.elasticloadbalancing.model LoadBalancerNotFoundException]))

(def default-access-log-enabled
  (Boolean/valueOf (env :default-access-log-enabled false)))

(def default-access-log
  {:enabled default-access-log-enabled})

(def default-connection-draining-enabled
  (Boolean/valueOf (env :default-connection-draining-enabled true)))

(def default-connection-draining-timeout-seconds
  (Integer/valueOf (env :default-connection-draining-timeout-seconds 300)))

(def default-connection-draining
  {:enabled default-connection-draining-enabled
   :timeout default-connection-draining-timeout-seconds})

(def default-idle-timeout-seconds
  (Integer/valueOf (env :default-idle-timeout-seconds 60)))

(def default-idle-timeout
  {:idle-timeout default-idle-timeout-seconds})

(def default-cross-zone-enabled
  (Boolean/valueOf (env :default-cross-zone-enabled true)))

(def default-cross-zone
  {:enabled default-cross-zone-enabled})

(defn uppercase-protocols
  [listener]
  (-> listener
      (update-in [:instance-protocol] upper-case)
      (update-in [:protocol] upper-case)))

(defn adjust-listeners
  [{:keys [listeners] :as config}]
  (if listeners
    (-> config
        (assoc :listener-descriptions (map (fn [l] {:listener (uppercase-protocols l)}) listeners))
        (dissoc :listeners))
    config))

(defn convert-local-config
  [config]
  (-> config
      keywordize-keys
      adjust-listeners
      (dissoc :vpc-id)))

(defn security-group-names-to-ids
  [security-group-names vpc-id]
  (map #(:group-id (sg/security-group-by-name % vpc-id)) security-group-names))

(defn subnets-to-availability-zones
  [subnets]
  (map #(subnets/availability-zone %) subnets))

(defn create-healthcheck
  [{:keys [health-check load-balancer-name] :as config}]
  (guarded (elb/configure-health-check (aws/config) :health-check health-check :load-balancer-name load-balancer-name))
  (report/add :elb/configure-health-check (str "I've created a new health check config for elb " load-balancer-name))
  config)

(defn create-elb
  [{:keys [availability-zones listener-descriptions load-balancer-name scheme security-groups subnets tags] :as config}]
  (guarded (elb/create-load-balancer (aws/config)
                                     :availability-zones (vec availability-zones)
                                     :listeners (vec (map :listener listener-descriptions))
                                     :load-balancer-name load-balancer-name
                                     :scheme scheme
                                     :security-groups (vec security-groups)
                                     :subnets (vec subnets)
                                     :tags [{:key "ManagedBy"
                                             :value "Pedantic"}]))
  (report/add :elb/create-load-balancer (str "I've created a new ELB called " load-balancer-name)
              {:elb-name load-balancer-name})
  config)

(defn remove-policy-names
  [{:keys [listener-descriptions] :as load-balancer}]
  (assoc load-balancer :listener-descriptions (map #(dissoc % :policy-names) listener-descriptions)))

(defn correct-listener-description-keys
  [{:keys [listener] :as listener-description}]
  (assoc listener-description :listener (rename-keys listener {:sslcertificate-id :ssl-certificate-id})))

(defn correct-load-balancer-keys
  [{:keys [listener-descriptions] :as load-balancer}]
  (assoc load-balancer :listener-descriptions (map correct-listener-description-keys listener-descriptions)))

(defn find-elb
  [name]
  (try
    (let [load-balancers (guarded (elb/describe-load-balancers (aws/config) :load-balancer-names [name]))]
      (let [load-balancer (first (:load-balancer-descriptions load-balancers))]
        (-> load-balancer
            (select-keys [:availability-zones :health-check :listener-descriptions :load-balancer-name :scheme :security-groups :subnets])
            remove-policy-names
            correct-load-balancer-keys)))
    (catch LoadBalancerNotFoundException e
      nil)))

(defn get-load-balancer-attributes
  [name]
  (try
    (:load-balancer-attributes (guarded (elb/describe-load-balancer-attributes (aws/config) :load-balancer-name name)))
    (catch LoadBalancerNotFoundException e
      nil)))

(defn get-load-balancer-tags
  [name]
  (try
    (:tags (first (:tag-descriptions (guarded (elb/describe-tags (aws/config) :load-balancer-names [name])))))
    (catch LoadBalancerNotFoundException e
      nil)))

(defn ensure-healthcheck
  [{:keys [local remote] :as config}]
  (let [remote-health-check (:health-check remote)
        local-health-check (:health-check local)]
    (when-not (= remote-health-check local-health-check)
      (create-healthcheck local))
    config))

(defn ensure-listeners
  [{:keys [local remote] :as config}]
  (let [remote-listeners (map :listener (:listener-descriptions remote))
        load-balancer-name (:load-balancer-name local)
        local-listeners (map :listener (:listener-descriptions local))
        [revoke add] (compare-config local-listeners remote-listeners)]
    (when (seq revoke)
      (report/add :elb/delete-load-balancers-listeners (str "I had to delete listeners on load balancer '" load-balancer-name "'."))
      (guarded (elb/delete-load-balancer-listeners (aws/config)
                                                   :load-balancer-name load-balancer-name
                                                   :load-balancer-ports (vec (map :load-balancer-port revoke)))))
    (when (seq add)
      (report/add :elb/create-load-balancer-listeners (str "I had to create listeners on load balancer '" load-balancer-name "'."))
      (guarded (elb/create-load-balancer-listeners (aws/config)
                                                   :listeners (vec add)
                                                   :load-balancer-name load-balancer-name)))
    config))

(defn ensure-subnets
  [{:keys [local remote] :as config}]
  (let [remote-subnets (:subnets remote)
        load-balancer-name (:load-balancer-name local)
        local-subnets (:subnets local)
        [revoke add] (compare-config local-subnets remote-subnets)]
    (when (seq revoke)
      (report/add :elb/detach-load-balancer-from-subnets (str "I had to detach subnets [" (str/join "," revoke) "] from load balancer '" load-balancer-name "'."))
      (guarded (elb/detach-load-balancer-from-subnets (aws/config)
                                                      :load-balancer-name load-balancer-name
                                                      :subnets (vec revoke))))
    (when (seq add)
      (report/add :elb/attach-load-balancer-to-subnets (str "I had to attach subnets [" (str/join "," add) "] to load balancer '" load-balancer-name "'.'"))
      (guarded (elb/attach-load-balancer-to-subnets (aws/config)
                                                    :load-balancer-name load-balancer-name
                                                    :subnets (vec add))))
    config))

(defn ensure-security-groups
  [{:keys [local remote] :as config}]
  (let [remote-security-groups (:security-groups remote)
        load-balancer-name (:load-balancer-name local)
        local-security-groups (:security-groups local)]
    (when-not (= (set local-security-groups) (set remote-security-groups))
      (report/add :elb/apply-security-groups-to-load-balancer (str "I had to apply security groups [" (str/join "," local-security-groups) "] to load balancer '" load-balancer-name "'."))
      (guarded (elb/apply-security-groups-to-load-balancer (aws/config)
                                                           :load-balancer-name load-balancer-name
                                                           :security-groups (vec local-security-groups))))
    config))

(defn convert-cross-zone
  [cross-zone]
  (if (some? cross-zone)
    (if (map? cross-zone)
      cross-zone
      {:enabled cross-zone})
    default-cross-zone))

(defn convert-local-attributes
  [{:keys [access-log connection-draining connection-settings cross-zone]}]
  {:access-log (or access-log default-access-log)
   :additional-attributes []
   :connection-draining (or connection-draining default-connection-draining)
   :connection-settings (or connection-settings default-idle-timeout)
   :cross-zone-load-balancing (convert-cross-zone cross-zone)})

(defn ensure-attributes
  [{:keys [local] :as config}]
  (let [{:keys [load-balancer-name]} local
        local-attributes (convert-local-attributes local)
        remote-attributes (get-load-balancer-attributes load-balancer-name)]
    (when-not (= local-attributes remote-attributes)
      (report/add :elb/modify-load-balancer-attributes (str "I had to modify attributes on load balancer '" load-balancer-name "'. They are now: " local-attributes))
      (elb/modify-load-balancer-attributes (aws/config)
                                           :load-balancer-name load-balancer-name :load-balancer-attributes local-attributes))
    config))

(defn ensure-tags
  [{:keys [local] :as config} application]
  (let [{:keys [load-balancer-name tags]} local
        local-tags (util/dedupe-tags (concat tags (util/required-tags application)))
        remote-tags (get-load-balancer-tags load-balancer-name)
        [revoke add] (compare-config local-tags remote-tags)
        add-keys (into (hash-set) (map :key add))
        revoke-keys (remove (fn [k] (contains? add-keys k)) (map :key revoke))
        revoke-tags (map (fn [k] {:key k}) revoke-keys)]
    (when (seq revoke-tags)
      (report/add :elb/remove-tags (str "I had to remove tags with keys [" (str/join "," revoke-keys) "] from load balancer '" load-balancer-name "'."))
      (elb/remove-tags (aws/config) :load-balancer-names [load-balancer-name] :tags revoke-tags))
    (when (seq add)
      (report/add :elb/add-tags (str "I had to add tags " add " to load balancer '" load-balancer-name "'."))
      (elb/add-tags (aws/config) :load-balancer-names [load-balancer-name] :tags add))
    config))

(defn subnets-to-vpc
  [subnet-ids]
  (let [{:keys [subnets]} (guarded (ec2/describe-subnets (aws/config) :subnet-ids subnet-ids))
        vpc-ids (distinct (map :vpc-id subnets))]
    (if (= (count vpc-ids) 1)
      (first vpc-ids)
      (throw (ex-info "Multiple VPCs identified" {:subnet-ids subnet-ids})))))

(defn ensure-elb
  [application load-balancer]
  (let [vpc-id (subnets-to-vpc (:subnets load-balancer))
        security-group-ids (:security-groups load-balancer)
        local (-> load-balancer
                  (assoc :security-groups (security-group-names-to-ids security-group-ids vpc-id))
                  (dissoc :vpc-id))
        remote (find-elb (:load-balancer-name load-balancer))]
    (if remote
      (-> {:local local :remote remote}
          (ensure-healthcheck)
          (ensure-security-groups)
          (ensure-subnets)
          (ensure-listeners)
          (ensure-attributes)
          (ensure-tags application))
      (-> local
          (create-elb)
          (create-healthcheck)
          (ensure-attributes)
          (ensure-tags application)))
    nil))

(defn ensure-elbs
  [{:keys [LoadBalancer]} application]
  (one-or-more (comp (partial ensure-elb application) convert-local-config) LoadBalancer)
  nil)
