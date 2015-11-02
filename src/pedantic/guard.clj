(ns pedantic.guard
  (:require [clojure.tools.logging :refer [warn]]
            [environ.core :refer [env]]
            [metrics.meters :refer [mark! meter]])
  (:import [com.amazonaws AmazonServiceException]))

(def retried-error-meter
  (meter ["info" "application" "aws.exceptions.retried"]))

(def thrown-error-meter
  (meter ["info" "application" "aws.exceptions.thrown"]))

(def backoff-enabled?
  (Boolean/valueOf (env :backoff-enabled)))

(def backoff-base-time
  (Integer/valueOf (env :backoff-base-time-millis 100)))

(def backoff-maximum
  (Integer/valueOf (env :backoff-maximum-millis 10000)))

(def backoff-rate
  (Integer/valueOf (env :backoff-rate 2)))

(defn- aws-request-limit-exceeded?
  [e]
  (and (= AmazonServiceException (type e))
       (= "RequestLimitExceeded" (.getErrorCode e))))

(defn- aws-throttling?
  [e]
  (and (= AmazonServiceException (type e))
       (= "Throttling" (.getErrorCode e))))

(defn retryable-error?
  [e]
  (or (aws-request-limit-exceeded? e)
      (aws-throttling? e)))

; Code based on http://www.lispcast.com/exponential-backoff
(defn exponential-backoff [time rate max matcher f]
  (try
    (if (or (not backoff-enabled?)
            (>= time max))
      (f)
      (try
        (f)
        (catch Throwable t
          (if (matcher t)
            (do
              (warn t "Caught retryable error which will be retried.")
              (mark! retried-error-meter)
              (Thread/sleep time)
              (exponential-backoff (* time rate) rate max matcher f))
            (throw t)))))
    (finally
      (mark! thrown-error-meter))))

(defmacro guarded
  [& body]
  `(exponential-backoff backoff-base-time backoff-rate backoff-maximum retryable-error? (fn [] ~@body)))
