(ns shuppet.unit.core-shuppet
  (:require  [slingshot.slingshot :refer [try+ throw+]])
  (:use [shuppet.core-shuppet]
        [midje.sweet]))

(deftype TestAppNames []
  ApplicationNames
  (list-names
    [_]
    ["test-app"]))

(deftype TestConfig []
  Configuration
  (as-string
    [_ environment name]
    "this is a test config"))

(def configuration @#'shuppet.core-shuppet/configuration)

(fact-group :unit
            (fact "app list can be customized"
                  (binding [*application-names*  (TestAppNames.)]
                    (app-names) => ["test-app"]))

            (fact "configuration source can be customized"
                  (binding [*configuration*  (TestConfig.)]
                    (configuration ..env.. ..name..) => "this is a test config")))
