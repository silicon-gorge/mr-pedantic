(ns shuppet.scheduler
  (:require
   [shuppet
    [graphite :as graphite]
    [core :as core]
    [util :refer [rfc2616-time str-stacktrace]]]
   [clojure.tools.logging :refer [info warn error]]
   [slingshot.slingshot :refer [try+ throw+]]
   [environ.core :as env]
   [clojure.string :refer [split]]
   [overtone.at-at :as at-at]))

(def ^:private environments (set (split (env/env :service-environments) #",")))

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
     (dorun (core/configure-apps env)))
    (catch Exception e
      (error (str-stacktrace e)))))

(defn schedule
  [environment & [action interval]]
  (try+
   (let [pool (get-pool environment)]
     (at-at/stop-and-reset-pool! pool :strategy :kill)
     (if (= action "stop")
       {:message "Successfully stopped the shuppet scheduler"}
       (let [interval (Integer/parseInt (or interval (env/env :service-scheduler-interval)))]
         (at-at/every (* interval 60 1000)
                      #(configure-apps environment)
                      pool
                      :desc (rfc2616-time))
         {:message
          (str "Successfully started the shuppet scheduler. The scheduler will run every "
               interval " minutes.") })))
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
  (when-not (env/env :service-scheduler-off)
    (doseq [environment environments]
      (at-at/after (* (Integer/parseInt
                       (or (env/env (keyword (str "service-scheduler-delay-" environment))) "1"))
                      60 1000)
                   #(schedule environment)
                   (get-pool environment)
                   :desc (rfc2616-time)))))
