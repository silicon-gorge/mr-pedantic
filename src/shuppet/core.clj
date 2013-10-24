(ns shuppet.core
  (:require [clojure.string :refer [lower-case]]
            [clojure.data.json :refer [json-str]]
            [shuppet.util :refer :all]
            [shuppet.securitygroups :refer :all]))

(declare ^:dynamic default-config)

(defn execute-string
  [default-vars clojure-string]
  (let [ns (-> (java.util.UUID/randomUUID) (str) (symbol) (create-ns))]
    (try
      (binding [*ns* ns
                default-config default-config]
        (clojure.core/refer 'clojure.core)
        (refer 'clojure.data.json)
        (refer 'shuppet.core)
        (refer 'shuppet.util)
        (refer 'shuppet.securitygroups)
        (eval (load-string clojure-string)))
      (finally (remove-ns (symbol (ns-name ns)))))))

(defn- get-default-config
  [app-name env print-json vpc-id]
  {:Application (lower-case app-name)
   :Environment (keyword (lower-case env))
   :PrintJson print-json
   :VpcId vpc-id})

(defn configure
  ([app-name env print-json]
     (execute-string (get-default-config app-name
                                         env
                                         print-json
                                         "vpc-7bc88713")
                     (slurp (str app-name ".clj")))))

                                        ;(configure  "test" "dev" true)
