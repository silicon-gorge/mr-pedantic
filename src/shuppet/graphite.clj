(ns shuppet.graphite
  (:require [environ.core :refer [env]])
  (:import [com.yammer.metrics Metrics]
           [java.io PrintWriter StringWriter]
           [com.ovi.common.metrics MetricNames]
           [java.util.concurrent TimeUnit]
           [com.ovi.common.metrics.graphite GraphiteName AsynchronousGraphiteValueReporter ReporterState]
           [com.ovi.common.metrics HostnameFactory]))

(defn update-timer!
  [name duration unit]
  (doto (Metrics/newTimer (MetricNames/name name)
                          unit
                          TimeUnit/SECONDS)
    (.update duration unit)))


(defmacro report-time
  "Evaluates the expression and logs the amount of time it took to graphite"
  [name expr]
  `(let [start# (System/nanoTime)
         ret# ~expr]
     (update-timer! ~name
                    (/ (double (- (System/nanoTime) start#)) 1000000.0)
                    TimeUnit/MILLISECONDS)
     ret#))
