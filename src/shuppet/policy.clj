(ns shuppet.policy
  (:require
   [clojure.data.json :refer [json-str]]))

(defn- generate-policy [effect services actions]
  (let [services (if (string? services) [services] (vec services))
        actions (if (string? actions) [actions] (vec actions))]
    (json-str {:Statement [{:Effect effect
                            :Principal {:Service services}
                            :Action actions}] })))

(def default-policy (generate-policy "Allow" "ec2.amazonaws.com" "sts:AssumeRole" ))

;(prn default-policy)
