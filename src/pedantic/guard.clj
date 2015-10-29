(ns pedantic.guard
  (:require [clojure.tools.logging :refer [warn]]
            [environ.core :refer [env]])
  (:import [com.amazonaws AmazonServiceException]))

(def backoff-enabled?
  (Boolean/valueOf (env :backoff-enabled)))

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
  (if (or (not backoff-enabled?)
          (>= time max))
    (f)
    (try
      (f)
      (catch Throwable t
        (if (matcher t)
          (do
            (warn t "Caught retryable error which will be retried.")
            (Thread/sleep time)
            (exponential-backoff (* time rate) rate max matcher f))
          (throw t))))))

(defmacro guarded
  [& body]
  `(exponential-backoff 100 2 10000 retryable-error? (fn [] ~@body)))
