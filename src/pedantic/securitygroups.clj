(ns pedantic.securitygroups
  (:require [amazonica.aws.ec2 :as ec2]
            [clj-http.client :as client]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [io.clj.logging :refer [with-logging-context]]
            [pedantic
             [aws :as aws]
             [guard :refer [guarded]]
             [report :as report]
             [util :refer :all :as util]]))

(defn security-group-by-name
  [name vpc-id]
  (let [result (guarded (ec2/describe-security-groups (aws/config)
                                                      :filters [{:name "group-name" :values [name]}
                                                                {:name "vpc-id" :values [vpc-id]}]))]
    (first (:security-groups result))))

(defn is-security-group-id?
  [text]
  (re-matches #"^sg-.+" text))

(defn is-cidr?
  [text]
  (re-matches #"^([0-9]|[0-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])\.([0-9]|[0-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])\.([0-9]|[0-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])\.([0-9]|[0-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])\/([0-9]|[12][0-9]|3[0-2])$" text))

(defn replace-security-group-name
  [ip-range vpc-id]
  (if (or (is-cidr? ip-range)
          (is-security-group-id? ip-range))
    ip-range
    (if-let [{:keys [group-id]} (security-group-by-name ip-range vpc-id)]
      group-id
      (with-logging-context {:security-group ip-range
                             :vpc vpc-id}
        (throw (ex-info "Permissions refer to unknown security group" {:security-group ip-range
                                                                       :vpc vpc-id}))))))

(defn switch-ip-range-to-group-id
  [{:keys [ip-ranges] :as permission}]
  (if (is-security-group-id? (first ip-ranges))
    (-> permission
        (assoc :prefix-list-ids [])
        (assoc :ip-ranges [])
        (assoc :user-id-group-pairs (map (fn [id] {:group-id id}) ip-ranges)))
    (-> permission
        (assoc :prefix-list-ids [])
        (assoc :user-id-group-pairs []))))

(defn expand-ip-ranges
  [{:keys [ip-ranges] :as permission} vpc-id]
  (if (coll? ip-ranges)
    (map #(assoc permission :ip-ranges [(replace-security-group-name % vpc-id)]) ip-ranges)
    [(assoc permission :ip-ranges [(replace-security-group-name ip-ranges vpc-id)])]))

(defn convert-ingress-permissions
  [{:keys [ingress vpc-id] :as config}]
  (-> config
      (assoc :ip-permissions (map switch-ip-range-to-group-id (mapcat #(expand-ip-ranges % vpc-id) ingress)))
      (dissoc :ingress)))

(defn convert-egress-permissions
  [{:keys [egress vpc-id] :as config}]
  (-> config
      (assoc :ip-permissions-egress (map switch-ip-range-to-group-id (mapcat #(expand-ip-ranges % vpc-id) egress)))
      (dissoc :egress)))

(defn convert-local-config
  [config]
  (-> config
      util/keywordize-keys
      (set/rename-keys {:group-description :description})
      convert-ingress-permissions
      convert-egress-permissions
      (select-keys [:description :group-name :ip-permissions :ip-permissions-egress :vpc-id])))

(defn expand-user-id-group-pairs
  [{:keys [user-id-group-pairs] :as permission}]
  (map #(assoc permission :user-id-group-pairs [{:group-id (:group-id %)}]) user-id-group-pairs))

(defn convert-ip-permissions
  [{:keys [ip-permissions vpc-id] :as config}]
  (assoc config :ip-permissions (mapcat #(if (seq (:ip-ranges %)) (expand-ip-ranges % vpc-id) (expand-user-id-group-pairs %)) ip-permissions)))

(defn convert-ip-permissions-egress
  [{:keys [ip-permissions-egress vpc-id] :as config}]
  (assoc config :ip-permissions-egress (mapcat #(if (seq (:ip-ranges %)) (expand-ip-ranges % vpc-id) (expand-user-id-group-pairs %)) ip-permissions-egress)))

(defn convert-remote-config
  [config]
  (-> config
      convert-ip-permissions
      convert-ip-permissions-egress
      (select-keys [:description :group-id :group-name :ip-permissions :ip-permissions-egress :tags :vpc-id])))

(defn create-security-group
  [{:keys [description group-name vpc-id]}]
  (let [result (guarded (ec2/create-security-group (aws/config)
                                                   :description description
                                                   :group-name group-name
                                                   :vpc-id vpc-id))]
    (report/add :ec2/create-security-group (str "I've created a new security group called '" group-name "'."))
    (:group-id result)))

(defn ensure-ingress
  [security-group-name security-group-id [revoke-config add-config]]
  (when (seq add-config)
    (guarded (ec2/authorize-security-group-ingress (aws/config)
                                                   :group-id security-group-id
                                                   :ip-permissions (vec add-config)))
    (report/add :ec2/authorize-security-group-ingress (str "I've applied the new ingress rule '" (vec add-config) "' to the existing security group '" security-group-name "'.")))
  (when (seq revoke-config)
    (guarded (ec2/revoke-security-group-ingress (aws/config)
                                                :group-id security-group-id
                                                :ip-permissions (vec revoke-config)))
    (report/add :ec2/revoke-security-group-ingress (str "I've revoked the ingress rule '" (vec revoke-config) "' from the existing security group '" security-group-name "'.")))
  nil)

(defn ensure-egress
  [security-group-name security-group-id [revoke-config add-config]]
  (when (seq add-config)
    (guarded (ec2/authorize-security-group-egress (aws/config)
                                                  :group-id security-group-id
                                                  :ip-permissions (vec add-config)))
    (report/add :ec2/authorize-security-group-egress (str "I've applied the new egress rule '" (vec add-config) "' to the existing security group '" security-group-name "'.")))
  (when (seq revoke-config)
    (guarded (ec2/revoke-security-group-egress (aws/config)
                                               :group-id security-group-id
                                               :ip-permissions (vec revoke-config)))
    (report/add :ec2/revoke-security-group-egress (str "I've revoked the egress rule '" (vec revoke-config) "' from the existing security group '" security-group-name "'.")))
  nil)

(defn ensure-tags
  [security-group-name security-group-id [revoke add]]
  (let [add-keys (into (hash-set) (map :key add))
        revoke-tags (remove (fn [tag] (contains? add-keys (:key tag))) revoke)]
    (when (seq revoke-tags)
      (report/add :ec2/delete-tags (str "I had to remove tags with keys [" (str/join "," (map :key revoke-tags)) "] from security group '" security-group-name "'."))
      (ec2/delete-tags (aws/config) :resources [security-group-id] :tags revoke-tags))
    (when (seq add)
      (report/add :ec2/create-tags (str "I had to add tags " add " to security group '" security-group-name "'."))
      (ec2/create-tags (aws/config) :resources [security-group-id] :tags add))
    nil))

(defn compare-security-group
  [remote {:keys [group-name] :as local} application]
  (let [group-id (:group-id remote)
        ingress (compare-config (:ip-permissions local) (:ip-permissions remote))
        egress (compare-config (:ip-permissions-egress local) (:ip-permissions-egress remote))
        local-tags (util/dedupe-tags (concat (:tags local) (util/required-tags application)))
        tags (compare-config local-tags (:tags remote))]
    (ensure-ingress group-name group-id ingress)
    (ensure-egress group-name group-id egress)
    (ensure-tags group-name group-id tags)
    nil))

(defn delete-security-group
  [group-name vpc-id]
  (when-let [{:keys [group-id]} (security-group-by-name group-name vpc-id)]
    (guarded (ec2/delete-security-group (aws/config) :group-id group-id))
    (report/add :ec2/delete-security-group (str "I've succesfully deleted the security group '" group-name "' with id: " group-id ".")))
  nil)

(def default-egress
  {:ip-protocol "-1"
   :ip-ranges ["0.0.0.0/0"]})

(defn is-default-egress?
  [permission]
  (= (select-keys permission [:ip-protocol :ip-ranges])
     default-egress))

(defn build-security-group
  [{:keys [group-name] :as security-group} application]
  (when-let [new-group-id (create-security-group security-group)]
    (try
      (ensure-ingress group-name new-group-id [[] (:ip-permissions security-group)])
      (when-let [filtered-egress (seq (remove is-default-egress? (:ip-permissions-egress security-group)))]
        (ensure-egress group-name new-group-id [[default-egress] filtered-egress]))
      (ensure-tags group-name new-group-id [[] (util/dedupe-tags (concat (:tags security-group) (util/required-tags application)))])
      nil
      (catch Exception e
        (delete-security-group group-name (:vpc-id security-group))
        (throw e)))))

(defn ensure-security-group
  [application {:keys [vpc-id group-name] :as local-security-group}]
  (if-let [remote-security-group (security-group-by-name group-name vpc-id)]
    (compare-security-group (convert-remote-config remote-security-group) local-security-group application)
    (build-security-group local-security-group application))
  nil)

(defn ensure-security-groups
  [{:keys [SecurityGroups]} application]
  (one-or-more (comp (partial ensure-security-group application) convert-local-config) SecurityGroups)
  nil)
