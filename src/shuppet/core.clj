(ns shuppet.core
  (:require [clojure.string :refer [lower-case]]
            [clojure.data.json :refer [json-str]]
            [shuppet.util :refer :all]
            [clojure.string :refer [join]]
            [shuppet
             [git :as git]
             [securitygroups :refer [ensure-sgs delete-sgs]]
             [elb :refer [ensure-elb delete-elb]]
             [iam :refer [ensure-iam delete-role]]
             [s3 :refer [ensure-s3s delete-s3s]]
             [ddb :refer [ensure-ddbs delete-ddbs]]
             [campfire :as cf]
             [util :refer [to-vec]]]
            [clj-http.client :as client]
            [clojure.tools.logging :refer [error]]
            [environ.core :refer [env]]
            [slingshot.slingshot :refer [try+ throw+]]))

(def ^:const ^:private onix-url (env :environment-entertainment-onix-url))
(def ^:private default-info-room (to-vec (env :service-campfire-default-info-room)))
(def ^:private default-error-rooms
  (reduce conj
          default-info-room
          (to-vec (env :service-campfire-default-info-room))))

(defprotocol ApplicationNames
  (list-names
    [apps]
    "Gets a list of the application names"))

(defrecord ^:private FromService [^String url]
           ApplicationNames
           (list-names
             [this]
             (let [response (client/get (str url "/applications") {:as :json
                                                                   :throw-exceptions false})]
               (if (= 200 (:status response))
                 (get-in response [:body :applications])
                 (error (str  "Onix responded with error response : " response))))))

(defrecord ^:private LocalName [^String path]
           ApplicationNames
           (list-names
             [this]
             ["localtest"]))


(defprotocol Configuration
  (contents
    [this]
    "Gets the configuration file contents for evaluation"))

(defrecord ^:private FromGit [^String env ^String filename]
           Configuration
           (contents
             [this]
             (git/get-data (lower-case env) (lower-case filename))))

(defrecord ^:private FromFile [^String env ^String name]
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

(defn- app-name-provider
  [env]
  (if (= "local" env)
    (LocalName. "irrelevant-path")
    (FromService. onix-url)))

(defn get-app-names
  [env]
  (list-names (app-name-provider env)))

(defn- get-configuration [env name]
  (if (= "local" env)
    (FromFile. env name)
    (FromGit. env name)))

(defn- load-config
  [env app-name]
  (let [environment (contents (get-configuration env env))]
    (if app-name
      (let [application (contents (get-configuration env app-name))]
        (execute-string (str environment "\n" application) app-name))
      (execute-string environment))))


(defn apply-config
  ([env & [app-name]]
     (let [config (load-config env app-name)]
       (binding [cf/*info-rooms* (reduce conj
                                         (to-vec (get-in config [:Campfire :Info]))
                                         default-info-room)
                 cf/*error-rooms* (reduce conj
                                          (to-vec (get-in config [:Campfire :Error]))
                                          default-error-rooms)]
         (try+
          (doto config
            ensure-sgs
            ensure-elb
            ensure-iam
            ensure-s3s
            ensure-ddbs)
          (catch [:type :shuppet.util/aws] e
            (cf/error env app-name e)
            (throw+ e)))))))

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
      delete-role
      delete-s3s
      delete-ddbs)))

(defn update-configs
  [env]
  (let [names (list-names (FromService. onix-url))]
    (map #(apply-config env %) names)))
