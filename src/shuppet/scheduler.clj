(ns shuppet.scheduler
  (:require
   [shuppet
    [graphite :as graphite]
    [core :as core]
    [util :refer [rfc2616-time str-stacktrace]]]
   [clojure.tools.logging :refer [info warn error]]
   [environ.core :refer [env]]
   [clojure.string :refer [split]]
   [overtone.at-at :as at-at]))

(def ^:private environments (set (split (env :service-environments) #",")))

(def ^:private schedule-pools (atom {}))

(defn- create-pool
  [env]
  {env (at-at/mk-pool)})

(defn- get-pool
  [env]
  (let [env (keyword env)
        pool (env @schedule-pools)]
    (if pool
      pool
      (env (swap! schedule-pools merge (create-pool env))))))

(defn configure-apps [env]
  (try
    (graphite/report-time
     (str "scheduler." env ".configure_apps")
     (core/configure-apps env))
    (catch Exception e
      (error (str-stacktrace e)))))

(defn schedule
  [env & [action interval]]
  (let [pool (get-pool env)]
    (at-at/stop-and-reset-pool! pool :strategy :kill)
    (if (= action "stop")
      {:message "Succefully stopped the shuppet scheduler"}
      (let [interval (Integer/parseInt (or interval (env :service-scheduler-interval)))]
        (at-at/every (* interval 60 1000)
                     #(configure-apps env)
                     pool
                     :desc (rfc2616-time))
        {:message
         (str "Succesfully started the shuppet scheduler. The scheduler will run in every "
              interval " minutes.") }))))

(defn get-schedule
  [env]
  (if-let [job (first (at-at/scheduled-jobs (get-pool env)))]
    (if-let [interval (:ms-period job)]
      {:created (:desc job)
       :interval (str (/ interval (* 60 1000)) " minutes")}
      {:created (:desc job)
       :delay (str (/ (:initial-delay job) (* 60 1000)) " minutes")})))

(defn start
  []
  (when-not (= (env :environment-name) "local") ;dont want the auto scheduler for our test envs
    (doseq [environment environments]
      (at-at/after (* (or
                       (Integer/parseInt (env (keyword (str "service-scheduler-delay-" environment))))
                       10)
                      60 1000)
                   #(schedule environment)
                   (get-pool environment)
                   :desc (rfc2616-time)))))
