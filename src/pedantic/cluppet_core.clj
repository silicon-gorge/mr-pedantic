(ns pedantic.cluppet-core
  (:require [clojail
             [core :refer [sandbox safe-read]]
             [testers :refer [secure-tester-without-def blanket]]]
            [clojure.string :refer [join]]
            [environ.core :refer [env]]
            [ninjakoala.instance-metadata :as im]
            [pedantic
             [aws :as aws]
             [elb :as elb]
             [iam :as iam]
             [report :as report]
             [securitygroups :as sg]
             [util :refer :all]]
            [slingshot.slingshot :refer [try+ throw+]]))

(def ^:private cluppet-tester
  (conj secure-tester-without-def (blanket "environ" "pedantic")))

(defn- make-sandbox
  []
  (sandbox cluppet-tester
           :timeout 4000
           :init '(future (Thread/sleep 6000)
                          (-> *ns* .getName remove-ns))))

(defn- with-vars
  [vars clojure-string]
  (str (join (map (fn [[k v]]
                    (str "(def " (name k) " " (if (string? v) (str "\""v "\"")  v) ")\n"))
                  vars))
       clojure-string))

(defmulti evaluate-string
  (fn [strings & [vars]] (sequential? strings)))

(defmethod evaluate-string false
  [strings & [vars]]
  (evaluate-string [strings] vars))

(defmethod evaluate-string true
  [strings & [vars]]
  (try+
   (let [clojure-string (join "\n" (assoc strings 0 (with-vars vars (first strings))))
         wrapped (str "(let [_ nil] \n" clojure-string "\n)")
         form (safe-read wrapped)]
     ((make-sandbox) form))
   (catch java.util.concurrent.ExecutionException e
     (throw+ {:type ::invalid-config :message (.getMessage e)}))
   (catch clojure.lang.LispReader$ReaderException e
     (throw+ {:type ::invalid-config :message (.getMessage e)}))))

(defn apply-config
  [application environment config]
  (binding [report/report (atom [])
            aws/application application
            aws/environment environment
            aws/region (:region (im/instance-identity))]
    (sg/ensure-security-groups config application)
    (elb/ensure-elbs config application)
    (iam/ensure-iam config)
    @report/report))
