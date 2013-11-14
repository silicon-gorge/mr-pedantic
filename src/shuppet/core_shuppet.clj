(ns shuppet.core-shuppet
  (:require [clojure.string :refer [lower-case]]
            [clojure.data.json :refer [json-str]]
            [shuppet.util :refer :all]
            [clojure.string :refer [join split]]
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
            [environ.core :refer [env]]
            [slingshot.slingshot :refer [try+ throw+]]))

(def default-info-room (env :service-campfire-default-info-room))
(def default-error-rooms
  (conj [default-info-room] (env :service-campfire-default-error-room)))

(defprotocol ApplicationNames
  (list-names
    [this]
    "Gets a list of the application names"))

(deftype LocalAppNames []
           ApplicationNames
           (list-names
             [_]
             (split (env :service-local-app-names) #",")))

(defprotocol Configuration
  (as-string
    [this env filename]
    "Gets the configuration file as string"))

(deftype LocalConfig []
           Configuration
           (as-string
             [_ environment name]
             (let [filename (str (lower-case name) ".clj")
                   path (str (env :service-local-config-path) "/" environment "/" filename)]
               (-> path
                   (clojure.java.io/as-file)
                   (slurp)))))

(def ^:dynamic *application-names* (LocalAppNames.))
(def ^:dynamic *configuration* (LocalConfig.))

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

(defn app-names
  []
  (list-names *application-names*))

(defn- configuration [env name]
  (as-string *configuration* env name))

(defn load-config
  [env & [app-name]]
  (let [environment (configuration env env)]
    (if app-name
      (let [application (configuration env app-name)]
        (execute-string (str environment "\n" application) app-name))
      (execute-string environment))))

(defn- load-config-with-error-handling
  [env app-name]
  (try+
   (load-config env app-name)
   (catch clojure.lang.Compiler$CompilerException e
     (cf/error {:env env :app-name app-name :title "I cannot read this config" :message (.getMessage e)}
               default-error-rooms)
     (throw+ e))))

(defn apply-config
  ([env app-name]
     (let [config (load-config-with-error-handling env app-name)]
       (binding [cf/*info-rooms* (conj (to-vec (get-in config [:Campfire :Info]))
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
            (cf/error (merge {:env env :app-name app-name} e))
            (throw+ e)))))))

(defn clean-config [environment app-name]
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
  (let [names (app-names env)]
    (map #(apply-config env %) names)))
