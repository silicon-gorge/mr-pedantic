(ns shuppet.core)

(defn execute-string [clojure-string]
  (let [ns (-> (java.util.UUID/randomUUID) (str) (symbol) (create-ns))]
    (try
      (binding [*ns* ns]
        (clojure.core/refer 'clojure.core)
        (refer 'clojure.string)
        (refer 'shuppet.util)
        (refer 'shuppet.securitygroups)
        (eval (load-string clojure-string)))
      (finally (remove-ns (symbol (ns-name ns)))))))

(defn configure [env service-name]
  (let [config-file (str service-name "-" env ".clj")
        contents (slurp config-file)]
    (execute-string contents)))


;(configure "dev" "test")
