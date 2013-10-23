(ns shuppet.core
  (:require [clojure.string :refer [lower-case]]))

(declare ^:dynamic application-name)
(declare ^:dynamic environment)
(declare ^:dynamic action)
(declare ^:dynamic vpc-id)

(defn execute-string [app-name env action clojure-string]
  (let [ns (-> (java.util.UUID/randomUUID) (str) (symbol) (create-ns))]
    (try
      (binding [*ns* ns
                application-name app-name
                environment env
                action action
                vpc-id "vpc-7bc88713"] ;todo get it from somewhere
        (clojure.core/refer 'clojure.core)
        (refer 'clojure.string)
        (refer 'clojure.data.json)
        (refer 'shuppet.core)
        (refer 'shuppet.util)
        (refer 'shuppet.securitygroups)
        (eval (load-string clojure-string)))
      (finally (remove-ns (symbol (ns-name ns)))))))

(defn configure
  ([app-name env action]
     (let [config-file (str app-name ".clj")
           contents (slurp config-file)]
       (execute-string (lower-case app-name) (lower-case env) action contents)))
  ([app-name env]
     (configure app-name env nil)))

;(configure  "test" "prod")
