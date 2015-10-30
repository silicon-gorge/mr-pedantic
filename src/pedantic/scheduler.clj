(ns pedantic.scheduler
  (:require [clj-time.core :as time]
            [clojure.string :as str]
            [clojure.tools.logging :refer [info warn error]]
            [environ.core :refer [env]]
            [metrics.timers :refer [time! timer]]
            [overtone.at-at :as at-at]
            [pedantic
             [core :as core]
             [environments :as environments]
             [util :refer [str-stacktrace]]]
            [slingshot.slingshot :refer [try+ throw+]]))

(def ^:private schedule-pools
  (atom {}))

(def ^:private scheduler-on?
  (Boolean/valueOf (env :scheduler-on)))

(def ^:private scheduler-interval
  (Integer/valueOf (env :scheduler-interval)))

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
    (time! (timer ["info" "application" (str "scheduler." environment ".configure_apps")])
           (dorun (core/configure-apps environment)))
    (catch Exception e
      (error (str-stacktrace e)))))

(defn schedule
  [environment & [action interval]]
  (try+
   (let [pool (get-pool environment)]
     (at-at/stop-and-reset-pool! pool :strategy :kill)
     (if (= action "stop")
       {:message "Successfully stopped the Pedantic scheduler"}
       (let [interval (or interval scheduler-interval)]
         (at-at/every (* interval 60 1000)
                      #(configure-apps environment)
                      pool
                      :desc (str (time/now)))
         {:message (str "Successfully started the Pedantic scheduler. The scheduler will run every " interval " minutes.") })))
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

(defn start
  []
  (when scheduler-on?
    (doseq [environment (environments/environment-names)]
      (let [start-delay (environments/start-delay environment)]
        (info "Starting scheduler for" environment "after" start-delay "minutes")
        (at-at/after (* start-delay 60 1000)
                     #(schedule environment)
                     (get-pool environment)
                     :desc (str (time/now)))))))
