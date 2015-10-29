(ns pedantic.iam
  (:require [amazonica.aws.identitymanagement :as im]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [pedantic
             [aws :as aws]
             [guard :refer [guarded]]
             [report :as report]
             [util :refer :all]])
  (:import [com.amazonaws.services.identitymanagement.model NoSuchEntityException]))

(def ^:private default-role-policy
  (json/generate-string (create-policy {:Principal {:Service "ec2.amazonaws.com"}
                                        :Action "sts:AssumeRole"})))

(defn role-exists?
  [name]
  (try
    (some? (guarded (im/get-role (aws/config) :role-name name)))
    (catch NoSuchEntityException _
      false)))

(defn create-role
  [name]
  (guarded (im/create-role (aws/config)
                           :role-name name
                           :path "/"
                           :assume-role-policy-document default-role-policy))
  (report/add :im/create-role (str "I've created a new IAM role called '" name "'."))
  nil)

(defn ensure-role
  [{:keys [RoleName] :as opts}]
  (when-not (role-exists? RoleName)
    (create-role RoleName))
  opts)

(defn profile-with-role-exists?
  [name]
  (let [result (guarded (im/list-instance-profiles-for-role (aws/config)
                                                            :role-name name
                                                            :max-items 1))]
    (not-empty (:instance-profiles result))))

(defn add-role-to-profile
  [role-name profile-name]
  (guarded (im/add-role-to-instance-profile (aws/config)
                                            :role-name role-name
                                            :instance-profile-name profile-name))
  (report/add :im/add-role-to-instance-profile (str  "I've succesfully attached the role name '" role-name "' to the profile '" profile-name "'."))
  nil)

(defn ensure-profile-with-role
  [role-name profile-name]
  (when-not (profile-with-role-exists? role-name)
    (add-role-to-profile role-name profile-name))
  nil)

(defn create-instance-profile
  [name]
  (guarded (im/create-instance-profile (aws/config) :instance-profile-name name))
  (report/add :im/create-instance-profile (str "I've succesfully created an instance profile '" name "'."))
  nil)

(defn instance-profile-exists?
  [name]
  (try
    (some? (guarded (im/get-instance-profile (aws/config) :instance-profile-name name)))
    (catch NoSuchEntityException _
      false)))

(defn ensure-instance-profile
  [{:keys [RoleName]}]
  (when-not (instance-profile-exists? RoleName)
    (create-instance-profile RoleName))
  (ensure-profile-with-role RoleName RoleName)
  nil)

(defn create-policy-document
  [version opts]
  (without-nils (merge
                 {:Version version}
                 (join-policies (map create-policy opts)))))

(defn create-iam-policy
  [policy]
  {:PolicyName (policy :PolicyName)
   :PolicyDocument (create-policy-document
                    (policy :Version)
                    (policy :PolicyDocument))})

(defn create-iam-policies
  [opts]
  (map create-iam-policy opts))

(defn get-policy-document
  [role-name policy-name]
  (let [result (guarded (im/get-role-policy (aws/config)
                                            :role-name role-name
                                            :policy-name policy-name))]
    {:PolicyName (:policy-name result)
     :PolicyDocument (json/parse-string (url-decode (:policy-document result)) true)}))

(defn get-remote-policies
  [role-name]
  (let [result (guarded (im/list-role-policies (aws/config)
                                               :role-name role-name
                                               :max-items 100))]
    (map #(get-policy-document role-name %) (:policy-names result))))

(defn delete-role-policy
  [role-name {:keys [PolicyName]}]
  (guarded (im/delete-role-policy (aws/config)
                                  :role-name role-name
                                  :policy-name PolicyName))
  (report/add :im/delete-role-policy (str "I've succesfully deleted the policy '" PolicyName "' for role '" role-name "'."))
  nil)

(defn upsert-role-policy
  [role-name {:keys [PolicyName PolicyDocument]}]
  (let [policy-string (json/generate-string PolicyDocument)]
    (guarded (im/put-role-policy (aws/config)
                                 :role-name role-name
                                 :policy-name PolicyName
                                 :policy-document policy-string))
    (report/add :im/put-role-policy (str "I've succesfully upserted the policy '" PolicyName "' for role '" role-name "' with statement '" policy-string "'.")))
  nil)

(defn ensure-policies
  [{:keys [RoleName Policies]}]
  (let [local (create-iam-policies Policies)
        remote (get-remote-policies RoleName)
        local-policy-names (apply sorted-set (map :PolicyName Policies))
        [revoke add] (compare-config local remote)]
    (when (seq add)
      (doseq [policy add]
        (upsert-role-policy RoleName policy)))
    (when (seq revoke)
      (doseq [policy revoke]
        (when-not (contains? local-policy-names (:PolicyName policy))
          (delete-role-policy RoleName policy))))
    nil))

(defn ensure-iam
  [{:keys [Role]}]
  (when Role
    (doto Role
      ensure-role
      ensure-instance-profile
      ensure-policies))
  nil)
