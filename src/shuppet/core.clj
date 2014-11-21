(ns shuppet.core
  (:require [clj-http.client :as client]
            [clj-time
             [core :refer [plus after? minutes]]
             [local :refer [local-now to-local-date-time]]]
            [clojure.string :as str]
            [clojure.tools.logging :refer [info warn error]]
            [cluppet
             [core :as cl-core]
             [signature :as cl-sign]]
            [environ.core :refer [env]]
            [shuppet
             [util :as util]
             [git :as git]
             [sqs :as sqs]
             [campfire :as cf]
             [validator :refer [validate-app validate-env]]]
            [slingshot.slingshot :refer [try+ throw+]]))

(def no-schedule-services
  (atom {}))

(def default-stop-interval
  60)

(def max-stop-interval
  720)

(def default-keys-map
  {:key (env :aws-access-key-id-poke)
   :secret (env :aws-secret-access-key-poke)})

(defn onix-app-names
  []
  (let [url (str (env :onix-baseurl) "/applications")
        response (client/get url {:as :json})]
    (get-in response [:body :applications])))

(defn git-config-as-string
  [environment application]
  (git/get-data environment application))

(defn create-git-config
  [application master-only]
  (git/create-application application master-only))

(defn aws-keys-map
  [environment]
  {:key (env (keyword (str "aws-access-key-id-" environment)))
   :secret (env (keyword (str "aws-secret-access-key-" environment)))} )

(defn- tooling-service?
  [name]
  ((set (str/split (env :tooling-applications) #",")) name))

(defn- process-report
  [report]
  (cf/info report)
  (info report)
  (doseq [item (:report report)]
    (when (= :CreateLoadBalancer (:action item))
      (sqs/announce-elb (:elb-name item) (:environment report))))
  report)

(defn stop-schedule-temporarily
  [environment name interval]
  (let [interval (or interval default-stop-interval)
        interval (if (> interval max-stop-interval) max-stop-interval interval)
        t-key (keyword (str environment "-" name))]
    (swap! no-schedule-services merge {t-key (plus (local-now) (minutes interval))})))

(defn restart-app-schedule
  [environment name]
  (swap! no-schedule-services dissoc (keyword (str environment "-" name))))

(defn get-app-schedule
  [environment name]
  (@no-schedule-services (keyword (str environment "-" name))))

(defn- is-stopped?
  [environment name]
  (let [schedule (get-app-schedule environment name)]
    (if schedule
      (if (after? (local-now) schedule)
        (not (empty? (swap! no-schedule-services dissoc (keyword (str environment "-" name)))))
        true)
      false)))

(defn- local-config [filename]
  (slurp (str "test/shuppet/resources/local/" filename ".clj")))

(defn get-config
  ([environment]
     (let [config (if (= environment "local") (local-config environment) (git/get-data environment))]
       (-> (cl-core/evaluate-string config)
           (validate-env))))
  ([environment application]
     (let [config (if (= environment "local") (local-config environment) (git/get-data environment))]
       (get-config config environment application)))
  ([env-str-config environment application]
     (if (and env-str-config application)
       (let [default-policies (:DefaultRolePolicies (cl-core/evaluate-string env-str-config))
             app-config (if (= environment "local") (local-config application) (git/get-data environment application))
             app-config (cl-core/evaluate-string [env-str-config app-config]
                                                 {:$app-name application
                                                  :$env environment})
             app-config (assoc-in app-config
                                  [:Role :Policies]
                                  (concat (get-in app-config [:Role :Policies])
                                          default-policies))]
         (validate-app app-config))
       (get-config environment))))

(defn format-report
  [report environment application]
  (cond-> {:report report}
          environment (assoc :environment environment)
          application (assoc :application application)))

(defn apply-config
  ([environment]
     (apply-config nil environment nil))
  ([environment application]
     (apply-config (git/get-data environment) environment application))
  ([env-str-config environment application]
     (try+
      (binding [cl-sign/*aws-credentials* (aws-keys-map environment)]
        (->
         (cl-core/apply-config (get-config env-str-config environment application))
         (format-report environment application)
         (process-report)))
      (catch [:type :shuppet.git/git] m
        (let [message (merge {:environment environment
                              :application application} m)]
          (warn message)
          message))
      (catch map? m
        (let [message (merge {:environment environment
                              :application application} m)]
          (cf/error message)
          (error message)
          message))
      (catch Exception e
        (let [message  {:application application
                        :environment environment
                        :message (.getMessage e)
                        :stacktrace  (util/str-stacktrace e)}]
          (error message)
          message)))))

(defn filtered-apply-config
  [env-str-config environment application]
  (if (is-stopped? environment application)
    {:environment environment :application application :excluded true}
    (apply-config env-str-config environment application)))

(defn- env-config?
  [config]
  (re-find #"\(def +\$" config))

(defn validate-config
  [environment application config]
  (let [environment? (env-config? config)
        environent (or environment "poke")
        application (or application "app-name")
        env-config (if (= environment "local") (local-config environment) (git/get-data environment))
        config (if environment?
                 (cl-core/evaluate-string config)
                 (cl-core/evaluate-string [env-config config]
                                          {:$app-name application
                                           :$env environment}))]
    (if environment?
      (validate-env config)
      (validate-app config))))

(defn create-config
  [environment application master-only]
  (when-not (= "local" environment)
    (let [master-only (or master-only (tooling-service? application))]
      (create-git-config application master-only))))

(defn clean-config
  [environment application]
  (binding [cl-sign/*aws-credentials* (aws-keys-map environment)]
    (cl-core/clean-config (get-config environment application))))

(defn- filter-tooling-services
  [environment names]
  (if (= environment "prod")
    (remove tooling-service? names)
    names))

(defn app-names
  [environment]
  (filter-tooling-services environment (onix-app-names)))

(defn update-configs
  [env-str-config environment]
  (let [apps (app-names environment)]
    (map (fn [application]
           (filtered-apply-config env-str-config environment application))
         apps)))

(defn configure-apps
  [environment]
  (try
    (let [env-report (apply-config environment)]
      (cons env-report (update-configs (git/get-data environment) environment)))
    (catch Exception e
      (let [message  {:environment environment
                      :message (.getMessage e)
                      :stacktrace  (util/str-stacktrace e)}]
        (error message)
        message))))
