(ns shuppet.core
  (:require [clojure.string :refer [lower-case]]))

(declare ^:dynamic application-name)
(declare ^:dynamic environment)

(defn execute-string [app-name env clojure-string]
  (let [ns (-> (java.util.UUID/randomUUID) (str) (symbol) (create-ns))]
    (try
      (binding [*ns* ns
                application-name app-name
                environment env]
        (clojure.core/refer 'clojure.core)
        (refer 'clojure.string)
        (refer 'shuppet.core)
        (refer 'shuppet.util)
        (refer 'shuppet.securitygroups)
        (eval (load-string clojure-string)))
      (finally (remove-ns (symbol (ns-name ns)))))))

(defn configure [app-name env]
  (let [config-file (str app-name ".clj")
        contents (slurp config-file)]
    (execute-string (lower-case app-name) (lower-case env) contents)))

;(configure  "test" "dev")
