(ns shuppet.unit.core-shuppet
  (:require  [slingshot.slingshot :refer [try+ throw+]]
             [shuppet.campfire :as cf])
  (:use [shuppet.core-shuppet]
        [midje.sweet]))


(fact-group :unit

            (fact "campfire message is sent when aws error"
                  (with-cf-message {:env "env" :app-name "app-name"} (throw+  {:type :shuppet.util/aws})) => (throws clojure.lang.ExceptionInfo)
                  (provided
                   (cf/error {:env "env" :app-name "app-name" :type :shuppet.util/aws}) => nil))

            (fact "campfire message is sent when compilation error"
                  (with-cf-message {:env "env" :app-name "app-name"} (throw (clojure.lang.Compiler$CompilerException. "message" 0 0 (Throwable.)))) => (throws clojure.lang.Compiler$CompilerException)
                  (provided
                   (cf/error anything) => nil)))
