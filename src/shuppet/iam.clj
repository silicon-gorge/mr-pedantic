(ns shuppet.iam
  (:require
   [shuppet.aws :refer [iam-request]]
   [shuppet.util :refer :all]
   [shuppet.policy :refer :all]
   [clojure.tools.logging :as log]
   [clojure.data.zip.xml :refer [xml1-> text xml->]]
   [slingshot.slingshot :refer [try+ throw+]]))

(defn- process
  [action params]
  (iam-request (merge params {"Action" (name action)})))

(defn- role-exists?
  [name]
  (not (empty? (process :GetRole {"RoleName" name}))))

(defn- create-role
  [name]
  (process :CreateRole {"RoleName" name
                        "Path" "/"
                        "AssumeRolePolicyDocument" default-policy})
  (log/info "Succesfully created the iam role : " name))

(defn ensure-role
  [name]
  (when-not (role-exists? name)
    (create-role name)))

(defn- profile-with-role-exists?
  [name]
  (not (empty? (process :ListInstanceProfilesForRole {"RoleName" name
                                                      "MaxItems" 1}))))

(defn- add-role-to-profile
  [r-name p-name]
  (process :AddRoleToInstanceProfile {"RoleName" r-name
                                      "InstanceProfileName" p-name})
  (log/info "Succesfully attached the tole name " r-name " to the profile " p-name))

(defn- ensure-profile-with-role
  [role-name profile-name]
  (when-not (profile-with-role-exists? role-name)
    (add-role-to-profile role-name profile-name)))

(defn- create-iprofile
  [name]
  (process :CreateInstanceProfile {"InstanceProfileName" name})
  (log/info "Succesfully created the instance profile : " name))

(defn- iprofile-exists?
  [name]
  (not (empty? (process :GetInstanceProfile {"InstanceProfileName" name}))))

(defn- ensure-iprofile
  [name]
  (when-not (iprofile-exists? name)
    (create-iprofile name))
  (ensure-profile-with-role name name))

(defn- list-role-policies [r-name]
  (if-let [response (process :ListRolePolicies {"RoleName" r-name
                                               "MaxItems" 100})]
    (xml-> response :ListRolePoliciesResult :PolicyNames :member text)))

(defn- ensure-role-policies
  [r-name policies]
  (let [[r l] (compare-config policies (list-role-policies r-name))]
    (when-not (empty? r)
      ;remove from remote deleterolepolicy
      )
    (when-not (empty? l)
     ;add to remote putrolepolicy
      )))


(defn- ensure-role-and-profile
  [name]
  (ensure-role name)
  (ensure-iprofile name))


;(prn (list-role-policies "test-role"))
