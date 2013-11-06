(ns shuppet.core
  (:require [clojure.string :refer [lower-case]]
            [shuppet.util :refer :all]
            [clojure.string :refer [join]]
            [shuppet
             [git :as git]
             [securitygroups :refer [ensure-sgs delete-sgs]]
             [elb :refer [ensure-elb delete-elb]]
             [iam :refer [ensure-iam delete-role]]]
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

(defrecord FromGit [^String env ^String filename]
  Configuration
  (contents
    [this]
    (git/get-data (lower-case env) (lower-case filename))))

(defrecord FromFile [^String env ^String name]
  Configuration
  (contents
    [this]
    (let [f-name (if (= env name)
                   (str (lower-case env) ".clj")
                   (str (lower-case name) ".clj"))
          resource (str  "test/shuppet/resources/" f-name)]
      (-> resource
          (clojure.java.io/as-file)
          (slurp)))))

(defn- with-vars [vars clojure-string]
  (str (join (map (fn [[k v]]
                    (str "(def " (name k) " " (if (string? v) (str "\""v "\"")  v) ")\n"))
                  vars))
       clojure-string))

(defn- execute-string
  [clojure-string & [app-name]]
  (let [ns (-> (java.util.UUID/randomUUID) (str) (symbol) (create-ns))]
    (binding [*ns* ns]
      (refer 'clojure.core)
      (let [config (load-string (with-vars {:$app-name app-name}
                                  clojure-string))]
        (remove-ns (symbol (ns-name ns)))
        config))))

(defn- get-configuration [env name]
  (if (= "local" env)
    (FromFile. env name)
    (FromGit. env name)))

(defn load-config
  [env app-name]
  (let [environment (contents (get-configuration env env))]
    (if app-name
      (let [application (contents (get-configuration env app-name))]
        (execute-string (str environment "\n" application) app-name))
      (execute-string environment))))

(defn apply-config
  ([env & [app-name]]
     (let [config (load-config env app-name)]
       (try+
        (doto config
          ensure-sgs
          ensure-elb
          ensure-iam)
        (catch map? error
          (throw+ (merge error {:name app-name
                                :env env
                                :cf-rooms (:Campfire config)})))))))

(defn get-config
  ([env & [app-name]]
     (load-config env app-name)))


(defn clean-config [environment app-name]
  (when-not (= "poke" (lower-case (env :environment-name)))
    (throw+ {:type ::wrong-environment}))
  (let [config (load-config environment app-name)]
    (delete-elb config)
    (Thread/sleep 6000)
    (doto config
      delete-sgs
      delete-role)))

(defn update-configs
  [env]
  (let [names (list-names (NamesFromService. onix-url))]
    (map #(apply-config env %) names)))
