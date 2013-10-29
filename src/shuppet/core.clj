(ns shuppet.core
  (:require [clojure.string :refer [lower-case]]
            [clojure.data.json :refer [json-str]]
            [shuppet.util :refer :all]
            [clojure.string :refer [join]]
            [shuppet
             [git :as git]
             [campfire :refer [set-rooms!]]
             [securitygroups :refer [ensure-sgs]]
             [elb :refer [ensure-elb]]]
            [clj-http.client :as client]
            [environ.core :refer [env]]
            [slingshot.slingshot :refer [try+ throw+]]))

(def ^:const onix-url (env :environment-onix-url) ) ;todo check

(defprotocol ApplicationNames
  (list-names
    [apps]
    "Gets a list of the application names"))

(defrecord NamesFromService [^String url]
  ApplicationNames
  (list-names
    [this]
    (let [response (client/get (str url "/applications") {:as :json
                                                          :throw-exceptions false})]
      (if (= 200 (:status response))
        (get-in response [:body :applications])
        (prn "Cant get a proper response from onix application " response)))))

(defrecord NamesFromFile [^String path]
  ApplicationNames
  (list-names
    [this]
    (prn "Get service names from local file system here")))


(defprotocol Configuration
  (contents
    [this]
    "Gets the configuration file contents for evaluation"))

(defrecord FromGit [^String env ^String name]
  Configuration
  (contents
    [this]
    (git/get-data (lower-case env) (lower-case name))))

(defrecord FromFile [^String env ^String name]
  Configuration
  (contents
    [this]
    (let [f-name (if (= env name)
                   (str (lower-case env) ".clj")
                   (str (lower-case name) ".clj"))
          res (clojure.java.io/resource f-name)
          resource (if (nil? res) ; will be nil if its executed outside the web context
                     (str  "shared/" f-name)
                     res)]
      (-> resource
          (clojure.java.io/as-file)
          (slurp)))))

(defn- with-vars [vars clojure-string]
  (str (join (map (fn [[k v]]
                    (str "(def " (name k) " " (if (string? v) (str "\""v "\"")  v) ")\n"))
                  vars))
       clojure-string))

(defn- execute-string
  [clojure-string name environment]
  (let [ns (-> (java.util.UUID/randomUUID) (str) (symbol) (create-ns))]
    (binding [*ns* ns]
      (clojure.core/refer 'clojure.core)
      (refer 'clojure.data.json)
      (refer 'shuppet.config-util)
      (let [config (load-string (with-vars {:app-name name :environment (keyword environment)}
                                  clojure-string))]
        (remove-ns (symbol (ns-name ns)))
        config))))

(defn- get-configuration [env name]
  (if (= "local" env)
    (FromFile. env name)
    (FromGit. env name)))

(defn configure
  ([env app-name print-json]
     (let [defaults (contents (get-configuration env env))
           config (contents (get-configuration env app-name))
           config (execute-string (str defaults "\n" config) app-name env)]
       (if print-json
         (json-str config)
         (try+
          (do
            (set-rooms! (:campfire config))
            (ensure-sgs (:sg config))
            (ensure-elb (:elb config)))
          (catch map? error
            (throw+ (merge error {:name app-name :env env}))))))))

(defn update
  [env]
  (let [names (list-names (NamesFromService. onix-url))]
    (map #(configure % env false) names)))
