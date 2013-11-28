(ns shuppet.validator
  (:require [bouncer
             [core :as b]
             [validators :as v]]
            [shuppet.util :refer :all]
            [slingshot.slingshot :refer [throw+]]))

(defn count=2?
  [input]
  (= (count input) 2))

(def validator
  {:SecurityGroups [[v/required] [count=2? :message "you should and can have only 2 security groups"]]
   [:Role :RoleName] v/required})


(defn validate
  [config]
  (if-let [result (first (b/validate
                          config
                          validator))]
    (throw+ {:type ::validator
             :details result})))
