(ns shuppet.core-shuppet
  (:require [clojure.string :refer [lower-case]]
            [clojure.data.json :refer [json-str]]
            [shuppet.util :refer :all]
            [clojure.string :refer [join split]]
            [clojure.java.io :refer [as-file file resource]]
            [clojail.core :refer [sandbox safe-read]]
            [clojail.testers :refer [secure-tester-without-def blanket]]
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

(def shuppet-tester
  (conj secure-tester-without-def (blanket "shuppet" "environ" "compojure")))

(defn- make-sandbox []
  (sandbox shuppet-tester
           :timeout 2000
           :init '(future (Thread/sleep 6000)
                          (-> *ns* .getName remove-ns))))

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
    "Gets the configuration file as string")
  (configure
    [this app-name master-only]
    "Sets up the configuration file for the application"))

(deftype LocalConfig []
  Configuration
  (as-string
    [_ environment name]
    (let [filename (str (lower-case name) ".clj")
          path (str (env :service-local-config-path) "/" environment "/" filename)]
      (-> path
          (as-file)
          (slurp))))
  (configure
    [_ name master-only]
    (let [dest-path (str (env :service-local-config-path) "/local/" name ".clj")]
      (spit dest-path (slurp (resource "default.clj")))
      {:message (str "Created new configuration file " dest-path)})))

(def ^:dynamic *application-names* (LocalAppNames.))
(def ^:dynamic *configuration* (LocalConfig.))

(defn- with-vars [vars clojure-string]
  (str (join (map (fn [[k v]]
                    (str "(def " (name k) " " (if (string? v) (str "\""v "\"")  v) ")\n"))
                  vars))
       clojure-string))

(defn- execute-string
  [clojure-string & [env app-name]]
  (let [clojure-string (with-vars {:$app-name app-name
                                   :$env env} clojure-string)
        wrapped (str "(let [_ nil] \n" clojure-string "\n)")
        form (safe-read wrapped)]
    ((make-sandbox) form)))

(defn app-names
  []
  (list-names *application-names*))

(defn create-config
  [app-name master-only]
  (configure *configuration* app-name master-only))

(defn- configuration [env name]
  (as-string *configuration* env name))

(defn load-config
  [env & [app-name]]
  (let [environment (configuration env env)]
    (if app-name
      (let [application (configuration env app-name)]
        (execute-string (str environment "\n" application) env app-name))
      (execute-string environment))))

(defn try-app-config
  [env app-name config]
  (let [environment (configuration env env)]
      (execute-string (str environment "\n" config) env app-name)))

(defn try-env-config
  [config]
  (execute-string config))

(defn apply-config
  [env app-name]
  (let [config (cf/with-messages {:env env :app-name app-name}
                   (load-config env app-name))]
    (cf/with-messages {:env env :app-name app-name :config config}
        (doto config
          ensure-sgs
          ensure-elb
          ensure-iam
          ensure-s3s
          ensure-ddbs))))

(defn clean-config [environment app-name]
  (let [config (load-config environment app-name)]
    (delete-elb config)
    (Thread/sleep 6000)
    (doto config
      delete-sgs
      delete-role
      delete-s3s
      delete-ddbs)))
