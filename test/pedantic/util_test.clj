(ns pedantic.util-test
  (:require [cheshire.core :as json]
            [clj-time
             [core :as time]
             [local :as local]]
            [midje.sweet :refer :all]
            [pedantic.util :refer :all])
  (:import (java.io ByteArrayInputStream)))

(fact "that getting the stack trace string from an exception works"
      (str-stacktrace (Exception. "woo")) => (contains "java.lang.Exception"))

(fact "that URL decoding is done correctly"
      (url-decode "~test%20string%2A") => "~test string*")

(fact "that without-nils removes nils from a map"
      (without-nils {:nil nil :not-nil "something"}) => {:not-nil "something"})

(fact "that in? works"
      (in? ["one" "two" "three"] "three") => truthy
      (in? ["one" "two" "three"] "bob") => falsey)

(fact "that compare-config does what it should"
      (compare-config {:local "value" :same "value"} {:remote "value" :same "value"}) => [[[:remote "value"]] [[:local "value"]]])

(fact "that creating a policy uses all parameters"
      (create-policy "sid" "effect" "principal" "action" "resource" "conditions")
      => {:Statement [{:Action ["action"]
                       :Conditions "conditions"
                       :Effect "effect"
                       :Principal "principal"
                       :Resource ["resource"]
                       :Sid "sid"}]})

(fact "that creating a policy with no effect substitutes 'Allow'"
      (create-policy "sid" nil "principal" "action" "resource" "conditions")
      => {:Statement [{:Action ["action"]
                       :Conditions "conditions"
                       :Effect "Allow"
                       :Principal "principal"
                       :Resource ["resource"]
                       :Sid "sid"}]})

(fact "that creating a policy removes nil parameters"
      (create-policy nil nil nil nil nil nil)
      => {:Statement [{:Effect "Allow"}]})

(fact "that one-or-more handles nothing"
      (one-or-more create-policy nil) => nil
      (one-or-more create-policy []) => nil
      (provided
       (create-policy anything) => nil :times 0))

(fact "that one-or-more does what we need when given a single item"
      (one-or-more create-policy ..policy..) => nil
      (provided
       (create-policy ..policy..) => nil))

(fact "that one-or-more does what we need when given multiple items"
      (one-or-more create-policy [..policy-1.. ..policy-2..]) => nil
      (provided
       (create-policy ..policy-1..) => nil
       (create-policy ..policy-2..) => nil))
