(ns shuppet.core-shuppet
  (:require [clojure.string :refer [lower-case]]
            [clojure.data.json :refer [json-str]]
            [shuppet.util :refer :all]
            [clojure.string :refer [join split]]
            [clojure.java.io :refer [as-file file resource]]
            [clojail.core :refer [sandbox safe-read]]
            [clojail.testers :refer [secure-tester-without-def blanket]]
            [shuppet
             [report :as report]
             [git :as git]
             [securitygroups :refer [ensure-sgs delete-sgs]]
             [elb :refer [ensure-elb delete-elb]]
             [iam :refer [ensure-iam delete-role]]
             [s3 :refer [ensure-s3s delete-s3s]]
             [ddb :refer [ensure-ddbs delete-ddbs]]
             [campfire :as cf]
             [util :refer [to-vec]]
             [validator :refer [validate validate-names]]]
            [clj-http.client :as client]
            [environ.core :refer [env]]
            [slingshot.slingshot :refer [try+ throw+]]))

(def shuppet-tester
  (conj secure-tester-without-def (blanket "shuppet" "environ" "compojure")))

(defn- make-sandbox []
  (sandbox shuppet-tester
           :timeout 4000
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
  (try+
    (let [clojure-string (with-vars {:$app-name app-name
                                     :$env env} clojure-string)
          wrapped (str "(let [_ nil] \n" clojure-string "\n)")
          form (safe-read wrapped)]
      ((make-sandbox) form))
    (catch java.util.concurrent.ExecutionException e
      (throw+ {:type ::invalid-config :message (.getMessage e)}))))

(defn app-names
  []
  (list-names *application-names*))

(defn create-config
  [app-name master-only]
  (configure *configuration* app-name master-only))

(defn- configuration [environment name]
  (as-string *configuration* environment name))

(defn load-config
  [environ & [app-name]]
  (let [environment (configuration environ environ)
        env-config (execute-string environment)]
    (if app-name
      (let [default-policies (:DefaultRolePolicies env-config)
            application (configuration environ app-name)
            app-config (execute-string (str environment "\n" application) environ app-name)]
        (validate-names env-config app-config)
        (assoc-in app-config [:Role :Policies]
                  (concat (get-in app-config [:Role :Policies])
                          default-policies)))
      env-config)))

(defn try-app-config
  [environ app-name config]
  (let [environment (configuration environ environ)]
      (execute-string (str environment "\n" config) environ app-name)))

(defn try-env-config
  [config]
  (execute-string config))

;TODO: extract campfire and remove all calls in various ensure-*
(defn apply-config
  [environment app-name]
  (let [config (cf/with-messages {:environment environment :app-name app-name}
                 (load-config environment app-name))]
    (cf/with-messages {:environment environment :app-name app-name :config config}
      (when app-name
        (validate config))
      (binding [report/report (atom [])]
        (doto config
          ensure-sgs
          ensure-elb
          ensure-iam
          ensure-s3s
          ensure-ddbs)
        @report/report))))

(defn clean-config [environment app-name]
  (let [config (load-config environment app-name)]
    (delete-elb config)
    (Thread/sleep 6000)
    (doto config
      delete-sgs
      delete-role
      delete-s3s
      delete-ddbs)))
