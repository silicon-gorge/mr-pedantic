(ns shuppet.core
  (:require [shuppet
             [util :as util]
             [core-shuppet :as shuppet]
             [git :as git]
             [campfire :as cf]
             [signature :as signature]
             [validator :refer [validate]]]
            [clojure.string :refer [lower-case split]]
            [clojure.tools.logging :refer [info warn error]]
            [clj-http.client :as client]
            [environ.core :refer [env]]
            [slingshot.slingshot :refer [try+ throw+]])
  (:import [shuppet.core_shuppet LocalConfig]
           [shuppet.core_shuppet LocalAppNames]))

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
               signature/*aws-keys* (if local?#
                                      signature/default-keys-map
                                      (aws-keys-map ~environment)) ]
       ~@body)))

(defn- tooling-service?
  [name]
  ((set (split (env :service-tooling-applications) #",")) name))

(defn apply-config
  ([environment & [app-name]]
     (when-not (and (= environment "prod") (tooling-service? app-name))
       (with-ent-bindings environment
         (shuppet/apply-config environment app-name)))))

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

(defn update-configs
  [environment]
  (let [names (app-names environment)
        names (filter-tooling-services environment names)]
    (doall
     (pmap (fn [app-name]
             (with-ent-bindings environment
               (try+
                 (shuppet/apply-config environment app-name)
                 (catch [:type :shuppet.git/git] {:keys [message]}
                   (warn message))
                 (catch Exception e
                   ;TODO: campfire message
                   (error (str app-name " in " environment " failed: " (util/str-stacktrace e)))))))
           names))))

(defn configure-apps
  [environment]
  (apply-config environment)
  (update-configs environment))
