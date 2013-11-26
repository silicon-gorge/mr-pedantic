(ns shuppet.core
  (:require [shuppet
             [core-shuppet :as shuppet]
             [git :as git]
             [campfire :as cf]
             [util :refer [is-prod?]]]
            [clojure.string :refer [lower-case split]]
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
             [_ env filename]
             (git/get-data env filename))
           (configure
             [_ appname master-only]
             (git/create-application appname master-only)))

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
                                         (GitConfig.))]
       ~@body)))

(defn apply-config
  ([env & [app-name]]
     (with-ent-bindings env
       (shuppet/apply-config env app-name))))

(defn get-config
  [env & [app-name]]
  (with-ent-bindings env
    (shuppet/load-config env app-name)))

(defn- env-config? [config]
  (re-find #"\(def +\$" config))

(defn validate-config
  [config name]
  (with-ent-bindings nil
    (if (env-config? config)
      (shuppet/try-env-config config)
      (shuppet/try-app-config "poke" name config))))

(defn create-config
  [env app-name master-only]
  (with-ent-bindings env
    (shuppet/create-config app-name master-only)))

(defn clean-config
  [environment app-name]
  (with-ent-bindings environment
    (shuppet/clean-config environment app-name)))

(defn app-names [env]
  (with-ent-bindings env
    (shuppet/app-names)))

(defn- filter-tooling-services
  [names]
  (clojure.set/difference
   names
   (set (split (env :service-tooling-applications) #","))))

(defn update-configs
  [env]
  (let [names (app-names env)
        names (if (= env "prod")
                (filter-tooling-services names)
                names)]
    (pmap #(with-ent-bindings env
             (shuppet/apply-config env %)) names) ))
