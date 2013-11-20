(ns shuppet.unit.core
  (:require  [slingshot.slingshot :refer [try+ throw+]]
             [shuppet.core-shuppet :as shuppet])
  (:use [shuppet.core]
        [midje.sweet])
   (:import [shuppet.core_shuppet LocalConfig]
            [shuppet.core_shuppet LocalAppNames]
            [shuppet.core OnixAppNames]
            [shuppet.core GitConfig]))

(def env-config?  @#'shuppet.core/env-config?)

(fact-group :unit
            (fact "onix is used to get app names"
                  (with-ent-bindings nil
                    shuppet/*application-names*) => (fn [result] (instance? OnixAppNames result)))

            (fact "local app names are uesd"
                  (with-ent-bindings "local"
                    shuppet/*application-names*) => (fn [result] (instance? LocalAppNames result)))


            (fact "git is used to get config"
                  (with-ent-bindings nil
                    shuppet/*configuration*) => (fn [result] (instance? GitConfig result)))

            (fact "local config is used"
                  (with-ent-bindings "local"
                    shuppet/*configuration*) => (fn [result] (instance? LocalConfig result)))

            (fact "can tell when environment config"
                  (env-config? "(def $var \"value\")") => truthy
                  (env-config? "(def var \"value\")") => falsey))
