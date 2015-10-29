(ns pedantic.guard-test
  (:require [environ.core :refer [env]]
            [midje.sweet :refer :all]
            [pedantic.guard :refer :all])
  (:import [com.amazonaws AmazonServiceException]))

(fact "that we spot retryable errors correctly"
      (retryable-error? (doto (AmazonServiceException. "Busted") (.setErrorCode "RequestLimitExceeded"))) => truthy
      (retryable-error? (doto (AmazonServiceException. "Busted") (.setErrorCode "Throttling"))) => truthy
      (retryable-error? (Exception. "Busted")) => falsey)

(def original-backoff-enabled?
  backoff-enabled?)

(defn sample-matcher
  [e]
  true)

(defn sample-function
  []
  nil)

(alter-var-root #'backoff-enabled? (fn [_] false))

(fact "that exponential backoff does nothing if it isn't enabled"
      (exponential-backoff 1 2 2 sample-matcher (fn [] (sample-function)))
      => (throws Exception)
      (provided
       (sample-function) =throws=> (Exception. "Busted") :times 1
       (sample-matcher anything) => nil :times 0))

(fact "that exponential backoff returns the result of the function if it succeeds when it isn't enabled"
      (exponential-backoff 1 2 2 sample-matcher (fn [] (sample-function)))
      => 1
      (provided
       (sample-function) => 1
       (sample-matcher anything) => nil :times 0))

(alter-var-root #'backoff-enabled? (fn [_] true))

(fact "that exponential backoff returns the result of the function if it succeeds when it is enabled"
      (exponential-backoff 1 2 2 sample-matcher (fn [] (sample-function)))
      => 1
      (provided
       (sample-function) => 1
       (sample-matcher anything) => nil :times 0))

(fact "that exponential backoff doesn't retry if we haven't matched the exception"
      (exponential-backoff 1 2 2 sample-matcher (fn [] (sample-function)))
      => (throws Exception)
      (provided
       (sample-function) =throws=> (Exception. "Busted") :times 1
       (sample-matcher anything) => false))

(fact "that exponential backoff retries if we match the exception"
      (exponential-backoff 1 2 2 sample-matcher (fn [] (sample-function)))
      => (throws Exception)
      (provided
       (sample-function) =throws=> (Exception. "Busted") :times 2
       (sample-matcher anything) => true))

(alter-var-root #'backoff-enabled? (fn [_] original-backoff-enabled?))
