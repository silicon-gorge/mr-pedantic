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
(def execute-string @#'shuppet.core-shuppet/execute-string)

(fact-group :unit
            (fact "app list can be customized"
                  (binding [*application-names*  (TestAppNames.)]
                    (app-names) => ["test-app"]))

            (fact "configuration source can be customized"
                  (binding [*configuration*  (TestConfig.)]
                    (configuration ..env.. ..name..) => "this is a test config"))

            (fact "string is correctly evaluated"
                  (execute-string "(def var-val \"ok\")var-val") => "ok")

            (fact "long running code times out"
                  (execute-string "(Thread/sleep 600000000)") => (throws java.util.concurrent.TimeoutException))
            (fact "global variables are accessible"
                  (execute-string "{:env $env :app-name $app-name}" "env" "app-name") => {:env "env"
                                                                                          :app-name "app-name"})

            )
