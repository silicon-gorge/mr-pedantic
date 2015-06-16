(ns pedantic.core
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
            [pedantic
             [hubot :as hubot]
             [util :as util]
             [git :as git]
             [sqs :as sqs]
             [validator :refer [validate-app validate-env]]]
            [slingshot.slingshot :refer [try+ throw+]]))

(def no-schedule-services
  (atom {}))

(def default-stop-interval
  60)

(def max-stop-interval
  720)

(def retry-interval
  (Integer/valueOf (env :retry-interval-millis "10")))

(def default-keys-map
  {:key (env :aws-access-key-id-poke)
   :secret (env :aws-secret-access-key-poke)})

(defn lister-app-names
  []
  (let [url (str (env :lister-baseurl) "/applications")
        response (client/get url {:as :json})]
    (get-in response [:body :applications])))

(defn create-git-config
  [application]
  (git/create-application application))

(defn aws-keys-map
  [environment]
  {:key (env (keyword (str "aws-access-key-id-" environment)))
   :secret (env (keyword (str "aws-secret-access-key-" environment)))} )

(defn- tooling-service?
  [name]
  ((set (str/split (env :tooling-applications) #",")) name))

(defn- process-report
  [report]
  (hubot/info report)
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
        (seq (swap! no-schedule-services dissoc (keyword (str environment "-" name))))
        true)
      false)))

(defn get-config
  ([environment]
     (let [config (git/get-data environment)]
       (validate-env (cl-core/evaluate-string config))))
  ([environment application]
     (let [config (git/get-data environment)]
       (get-config config environment application)))
  ([env-str-config environment application]
     (if (and env-str-config application)
       (let [default-policies (:DefaultRolePolicies (cl-core/evaluate-string env-str-config))
             app-config (git/get-data application)
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

(defn try-times*
  [n thunk]
  (loop [n n]
    (if-let [result (try+ [(thunk)]
                          (catch [:code "Throttling"] e
                            (warn e "Retryable exception while applying configuration")
                            (when (zero? n)
                              (throw+ e))))]
      (result 0)
      (do
        (Thread/sleep retry-interval)
        (recur (dec n))))))

(defmacro try-times
  [n & body]
  `(try-times* ~n (fn [] ~@body)))

(defn apply-config
  ([environment]
   (apply-config nil environment nil))
  ([environment application]
   (apply-config (git/get-data environment) environment application))
  ([env-str-config environment application]
   (try+
    (try-times 2 (binding [cl-sign/*aws-credentials* (aws-keys-map environment)]
                   (-> (cl-core/apply-config (get-config env-str-config environment application))
                       (format-report environment application)
                       (process-report))))
    (catch [:type :pedantic.git/git] m
      (let [message (merge {:environment environment
                            :application application} m)]
        (warn message)
        message))
    (catch map? m
      (let [message (merge {:environment environment
                            :application application} m)]
        (hubot/error message)
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
        env-config (git/get-data environment)
        config (if environment?
                 (cl-core/evaluate-string config)
                 (cl-core/evaluate-string [env-config config]
                                          {:$app-name application
                                           :$env environment}))]
    (if environment?
      (validate-env config)
      (validate-app config))))

(defn create-config
  [application]
  (create-git-config application))

(defn- filter-tooling-services
  [environment names]
  (if (= environment "prod")
    (remove tooling-service? names)
    names))

(defn app-names
  [environment]
  (filter-tooling-services environment (lister-app-names)))

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
