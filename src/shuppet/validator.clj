(ns shuppet.validator
  (:require [bouncer
             [core :as b]
             [validators :as v]]
            [shuppet.util :refer :all]
            [clojure.set :refer [intersection]]
            [slingshot.slingshot :refer [throw+]]))

(defn- count<=2?
  [input]
  (<= (count input) 2))

(def ^:private validator
  {:SecurityGroups [[count<=2? :message "You can have a maximum of 2 security groups only."]]
   [:Role :RoleName] v/required})

(defn validate
  [config]
  (if-let [result (first (b/validate
                          config
                          validator))]
    (throw+ {:type ::validator
             :details result})))

(defn validate-names
  [env-config app-config]
  (let [default-role-names (set (map :PolicyName (env-config :DefaultRolePolicies)))
        default-sg-names (set (map :GroupName (env-config :SecurityGroups)))
        role-names (set (map :PolicyName (get-in app-config [:Role :Policies])))
        sg-names (set (map :GroupName (app-config :SecurityGroups)))]
    (let [role (intersection default-role-names role-names)
          sg (intersection default-sg-names sg-names)]
      (when-not (empty? role)
        (throw+ {:type ::validator
                 :details (str "Cannot use role names " role
                               " in application configuration, as its already defined in the environment configuration.")}))
      (when-not (empty? sg)
        (throw+ {:type ::validator
                 :details (str "Cannot use security group names " sg
                               " in application configuration, as its already defined in the environment configuration.") })))))
