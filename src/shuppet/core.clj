(ns shuppet.core
  (:require [clojure.string :refer [lower-case]]
            [clojure.data.json :refer [json-str]]
            [shuppet.util :refer :all]
            [shuppet.securitygroups :refer :all]
            [clj-http.client :as client]
            [environ.core :refer [env]]))

(def ^:const onix-url (or (env :environment-onix-url) "http://onix.brislabs.com:8080/1.x")) ;todo check

(declare ^:dynamic default-config)

(defprotocol ApplicationNames
  (list-names
    [apps]
    "Gets a list of the application names"))

(defrecord NamesFromOnix [^String url]
  ApplicationNames
  (list-names
    [this]
    (let [response (client/get (str url "/applications") {:as :json
                                                          :throw-exceptions true})]
      (if (= 200 (:status response))
        (get-in response [:body :applications])
        (prn "Cant get a proper response from onix application " response)))))

(defrecord NamesFromFile [^String path]
  ApplicationNames
  (list-names
    [this]
    (prn "Get service names from local file system here")))

(defn execute-string
  [default-vars clojure-string]
  (let [ns (-> (java.util.UUID/randomUUID) (str) (symbol) (create-ns))]
    (try
      (binding [*ns* ns
                default-config default-vars]
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
   :VpcId vpc-id
   :SecurityGroups {:Egress (flatten [(group-record -1 nil nil '("0.0.0.0/0"))])}})

(defn configure
  ([app-name env print-json]
     (execute-string (get-default-config app-name
                                         env
                                         print-json
                                         "vpc-7bc88713")
                     (slurp (str app-name ".clj")))))

(defn update
  [env]
  (let [names (list-names (NamesFromOnix. onix-url))]
    (map #(configure % env false) names)))

;(configure  "test" "dev" true)
;(update "dev")
