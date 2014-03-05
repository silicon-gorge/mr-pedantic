(ns shuppet.core
  (:require [shuppet
             [util :as util]
             [core-shuppet :as shuppet]
             [git :as git]
             [sqs :as sqs]
             [campfire :as cf]
             [signature :as signature]
             [validator :refer [validate]]]
            [clojure.string :refer [lower-case split]]
            [clojure.tools.logging :refer [info warn error]]
            [clj-http.client :as client]
            [environ.core :refer [env]]
            [slingshot.slingshot :refer [try+ throw+]]
            [clj-time.local :refer [local-now to-local-date-time]]
            [clj-time.core :refer [plus after? minutes]])
  (:import [shuppet.core_shuppet LocalConfig]
           [shuppet.core_shuppet LocalAppNames]))

(def no-schedule-services  (atom {}))
(def default-stop-interval 30)

(deftype OnixAppNames [^String url]
  shuppet/ApplicationNames
  (list-names
    [_]
    (let [url (str url "/applications")
          response (client/get url {:as :json
                                    :throw-exceptions false})
          status (:status response)]
      (if (= 200 status)
        (get-in response [:body :applications])
        (cf/error {:title "Failed to get application list from Onix."
                   :url url
                   :status status
                   :message (:body response)})))))

(deftype GitConfig []
  shuppet/Configuration
  (as-string
    [_ environment filename]
    (git/get-data environment filename))
  (configure
    [_ appname master-only]
    (git/create-application appname master-only)))

(defn aws-keys-map
  [environment]
  {:key (env (keyword (str "service-aws-access-key-id-" environment)))
   :secret (env (keyword (str "service-aws-secret-access-key-" environment)))} )

                                        ;change env arg to flag
(defmacro with-ent-bindings
  "Specific Entertainment bindings"
  [environment & body]
  `(let [local?# (= "local" ~environment)]
     (binding [shuppet/*application-names* (if local?#
                                             (shuppet/LocalAppNames.)
                                             (OnixAppNames. (env :environment-entertainment-onix-url)))
               shuppet/*configuration* (if local?#
                                         (shuppet/LocalConfig.)
                                         (GitConfig.))
               signature/*aws-credentials* (if local?#
                                             signature/default-keys-map
                                             (aws-keys-map ~environment))]
       ~@body)))

(defn- tooling-service?
  [name]
  ((set (split (env :service-tooling-applications) #",")) name))

(defn- process-report [report environment]
  (with-ent-bindings environment
    (doseq [item report]
      (when (= :CreateLoadBalancer (:action item))
        (sqs/announce-elb (:elb-name item) environment))))
  report)

(defn stop-schedule-temporarily
  [env name]
  (let [t-key (keyword (str env "-" name))
        services @no-schedule-services]
    (reset! no-schedule-services (merge services {t-key (local-now)}))))

(defn restart-app-schedule
  [env name]
  (if-let [services @no-schedule-services]
    (reset! no-schedule-services (dissoc services (keyword (str env "-" name))))))

(defn get-app-schedule
  [env name]
  (if-let [start-time (@no-schedule-services (keyword (str env "-" name)))]
    (plus start-time (minutes default-stop-interval))))

(defn- is-stopped?
  [env name]
  (let [schedule (get-app-schedule env name)]
    (if schedule
      (if (after? (local-now) schedule)
        (not (empty? (reset! no-schedule-services (dissoc @no-schedule-services (keyword (str env "-" name))))))
        true)
      false)))

(defn- can-apply-config?
  [env name]
  (not (or (is-stopped? env name)
           (and (= env "prod")
                (tooling-service? name)))))

(defn- *apply-config
  ([environment & [app-name]]
     (when (can-apply-config? environment app-name)
       (with-ent-bindings environment
         (shuppet/apply-config environment app-name)))))

(defn apply-config
  ([environment & [app-name]]
     (->
      (*apply-config environment app-name)
      (process-report environment))))

(defn get-config
  [environment & [app-name]]
  (with-ent-bindings environment
    (when-let [config (shuppet/load-config environment app-name)]
      (when app-name
        (validate config))
      config)))

(defn- env-config? [config]
  (re-find #"\(def +\$" config))

(defn validate-config
  [environment app-name config]
  (with-ent-bindings environment
    (if (env-config? config)
      (shuppet/try-env-config config)
      (when-let [config (shuppet/try-app-config (or environment "poke") (or app-name "app-name") config)]
        (validate config)
        config))))

(defn create-config
  [environment app-name master-only]
  (let [master-only (or master-only (tooling-service? app-name))]
    (with-ent-bindings environment
      (shuppet/create-config app-name master-only))))

(defn clean-config
  [environment app-name]
  (with-ent-bindings environment
    (shuppet/clean-config environment app-name)))

(defn- filter-tooling-services
  [environment names]
  (if (= environment "prod")
    (remove tooling-service? names)
    names))

(defn app-names [environment]
  (with-ent-bindings environment
    (filter-tooling-services environment (shuppet/app-names))))

(defn- process-reports [reports environment]
  (doall
   (map #(process-report % environment)
        reports))
  reports)

(defn- concurrent-config-update [environment]
  (let [names (app-names environment)]
    (doall
     (map (fn [app-name]
            (try+
             {:app app-name
              :report (*apply-config environment app-name)}
             (catch [:type :shuppet.git/git] {:keys [message]}
               (warn message)
               {:app app-name
                :error message})
             (catch  [:type :shuppet.core-shuppet/invalid-config] {:keys [message]}
               (cf/error {:environment environment
                          :title "error while loading config"
                          :app-name app-name
                          :message message })
               (error (str app-name " config in " environment " cannot be loaded: " message))
               {:app app-name
                :error message})
             (catch Exception e
               (error (str app-name " in " environment " failed: " (.getMessage e) " stacktrace: " (util/str-stacktrace e)))
               {:app app-name
                :error (.getMessage e)})
             (catch Object e
               (error (str app-name " in " environment " failed: " e))
               {:app app-name
                :error e})))
          names))))

(defn update-configs
  [environment]
  (->
   (concurrent-config-update environment)
   (process-reports environment)))

(defn configure-apps
  [environment]
  (apply-config environment)
  (update-configs environment))
