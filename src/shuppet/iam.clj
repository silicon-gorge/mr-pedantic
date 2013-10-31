(ns shuppet.iam
  (:require
   [shuppet.aws :refer [iam-request iam-post-request]]
   [shuppet.util :refer :all]
   [shuppet.policy :refer :all]
   [clojure.data.json :refer [write-str]]
   [clojure.tools.logging :as log]
   [clojure.data.zip.xml :refer [xml1-> text xml->]]
   [slingshot.slingshot :refer [try+ throw+]]))

(defn- create-policy-document
  ([sid effect services actions resources]
     (let [effect (if (empty? effect) "Allow" effect)
           services (if (string? services) [services] (vec services))
           actions (if (string? actions) [actions] (vec actions))
           resources (if (string? resources) [resources] (vec resources))]
       (without-nils {:Effect effect
                      :Sid sid
                      :Principal (without-nils {:Service services})
                      :Action actions
                      :Resource resources}))))

(defn- create-policy
  ([sid effect services actions resources]
     {:Statement [(create-policy-document sid effect services actions resources)]})
  ([{:keys [Sid Effect Service Action Resource]}]
     (create-policy Sid Effect Service Action Resource)))

(def default-policy (write-str (create-policy nil "Allow" "ec2.amazonaws.com" "sts:AssumeRole" nil)))

(defn- process
  [action params]
  (if (= action "PutRolePolicy")
    (iam-post-request (merge params {"Action" (name action)}))
    (iam-request (merge params {"Action" (name action)}))))

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
  [{:keys [RoleName] :as opts}]
  (when-not (role-exists? RoleName)
    (create-role RoleName))
  opts)

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
  [{:keys [RoleName]}]
  (when-not (iprofile-exists? RoleName)
    (create-iprofile RoleName))
  (ensure-profile-with-role RoleName RoleName))

(defn- join-policies
  [policy-docs]
  {:Statement (vec (map #(first (:Statement %)) policy-docs))})

(defn- create-policy-stmt
  [opts]
  (write-str (join-policies (map create-policy opts))))

(defn- create-policy-doc
  [opts]
  (map #(update-in % [:PolicyDocument] create-policy-stmt) opts))

(defn- get-policy-document
  [r-name p-name]
  (let [response (process :GetRolePolicy {"RoleName" r-name
                                          "PolicyName" p-name})]
    {:PolicyName (xml1-> response :GetRolePolicyResult :PolicyName text)
     :PolicyDocument (url-decode (xml1-> response :GetRolePolicyResult :PolicyDocument text)) }))

(defn- get-remote-policies
  [r-name]
  (let [response (process :ListRolePolicies {"RoleName" r-name
                                             "MaxItems" 100})
        p-names (xml-> response :ListRolePoliciesResult :PolicyNames :member text)]
    (flatten (map #(get-policy-document r-name %) p-names)))) ;tocheck

(defn- delete-role-policy
  [r-name {:keys [PolicyName]}]
  (process :DeleteRolePolicy {"RoleName" r-name
                              "PolicyName" PolicyName}))

(defn- put-role-policy
  [r-name {:keys [PolicyName PolicyDocument]}]
  (process :PutRolePolicy {"RoleName" r-name
                                 "PolicyName" PolicyName
                                 "PolicyDocument" PolicyDocument}))

(defn- ensure-policies
  [{:keys [RoleName Policies]}]
  (let [local (create-policy-doc Policies)
        remote (get-remote-policies RoleName)
        lp-names (apply sorted-set (map :PolicyName Policies))
        [r l] (compare-config local remote)]
    (when-not (empty? r)
      (doseq [policy r]
        ;delete it iff the policyname is not present in the local config
        ;otherwise put role policy will update the changes on the existing policy.
        (when-not (contains? lp-names (:PolicyName policy))
          (delete-role-policy RoleName policy))))
    (when-not (empty? l)
      (doseq [policy l]
        (put-role-policy RoleName policy)))))

(defn ensure-iam
  [opts]
  (doto opts
    ensure-role
    ensure-iprofile
    ensure-policies))
