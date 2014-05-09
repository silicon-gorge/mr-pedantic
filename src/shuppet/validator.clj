(ns shuppet.validator
  (:require [bouncer
             [core :as b]
             [validators :as v]]
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
             :details result})
    config))
