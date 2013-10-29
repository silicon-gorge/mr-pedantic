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
                        "AssumeRolePolicyDocument" default-policy}))

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
                                      "InstanceProfileName" p-name}))

(defn- ensure-profile-with-role
  [role-name profile-name]
  (when-not (profile-with-role-exists? role-name)
    (add-role-to-profile role-name profile-name)))

(defn- create-iprofile
  [name]
  (process :CreateInstanceProfile {"InstanceProfileName" name}))

(defn- iprofile-exists?
  [name]
  (not (empty? (process :GetInstanceProfile {"InstanceProfileName" name}))))

(defn- ensure-iprofile
  [name]
  (when-not (iprofile-exists? name)
    (create-iprofile name))
  (ensure-profile-with-role name name))


(defn- ensure-role-and-profile
  [name]
  (ensure-role name)
  (ensure-iprofile name))


;(prn (ensure-role-and-profile "test-role"))