(ns shuppet.scheduler
  (:require [clojure.string :as str]
            [clojure.tools.logging :refer [info warn error]]
            [environ.core :refer [env]]
            [metrics.timers :refer [time! timer]]
            [overtone.at-at :as at-at]
            [shuppet
             [core :as core]
             [util :refer [rfc2616-time str-stacktrace]]]
            [slingshot.slingshot :refer [try+ throw+]]))

(def ^:private environments
  (set (str/split (env :service-environments) #",")))

(def ^:private schedule-pools
  (atom {}))

(def ^:private scheduler-on?
  (Boolean/valueOf (env :service-scheduler-on)))

(def ^:private scheduler-interval
  (Integer/valueOf (env :service-scheduler-interval)))

(defn- create-pool
  [environment]
  {environment (at-at/mk-pool)})

(defn- get-pool
  [environment]
  (let [environment (keyword environment)
        pool (environment @schedule-pools)]
    (if pool
      pool
      (environment (swap! schedule-pools merge (create-pool environment))))))

(defn configure-apps
  [environment]
  (try
    (time! (timer ["info" "application" (str "scheduler." environment ".configure_apps")]
                  (dorun (core/configure-apps environment))))
    (catch Exception e
      (error (str-stacktrace e)))))

(defn schedule
  [environment & [action interval]]
  (try+
   (let [pool (get-pool environment)]
     (at-at/stop-and-reset-pool! pool :strategy :kill)
     (if (= action "stop")
       {:message "Successfully stopped the shuppet scheduler"}
       (let [interval (or interval scheduler-interval)]
         (at-at/every (* interval 60 1000)
                      #(configure-apps environment)
                      pool
                      :desc (rfc2616-time))
         {:message (str "Successfully started the shuppet scheduler. The scheduler will run every " interval " minutes.") })))
   (catch Exception e
     (error (str-stacktrace e))
     (throw+ e))))

(defn get-schedule
  [env]
  (if-let [job (first (at-at/scheduled-jobs (get-pool env)))]
    (if-let [interval (:ms-period job)]
      {:created (:desc job)
       :interval (str (/ interval (* 60 1000)) " minutes")}
      {:created (:desc job)
       :delay (str (/ (:initial-delay job) (* 60 1000)) " minutes")})))

(defn environment-delay
  [environment]
  (let [property (keyword (str "service-scheduler-delay-" environment))]
    (Integer/valueOf (or (env property 1)))))

(defn start
  []
  (when scheduler-on?
    (doseq [environment environments]
      (at-at/after (* (environment-delay environment) 60 1000)
                   #(schedule environment)
                   (get-pool environment)
                   :desc (rfc2616-time)))))
