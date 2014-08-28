(ns shuppet.util-test
  (:require [clj-time
             [core :as time]
             [local :as local]]
            [midje.sweet :refer :all]
            [shuppet.util :refer :all]))

(fact "that creating an rfc2616 time works"
      (rfc2616-time) => "Thu, 21 Aug 1997 08:00:00 GMT"
      (provided
       (local/local-now) => (time/date-time 1997 8 21 8 0 0)))

(fact "that getting the stack trace string from an exception works"
      (str-stacktrace (Exception. "woo")) => (contains "java.lang.Exception"))
