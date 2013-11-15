(ns shuppet.unit.campfire
  (:require  [slingshot.slingshot :refer [try+ throw+]]
             [environ.core :refer [env]]
             [clj-campfire.core :as cf])
  (:use [shuppet.campfire]
        [midje.sweet]))

(fact-group :unit

            (fact "campfire message is sent when aws error"
                  (with-messages {:env "env" :app-name "app-name"} (throw+  {:type :shuppet.util/aws})) => (throws clojure.lang.ExceptionInfo)
                  (provided
                   (error {:env "env" :app-name "app-name" :type :shuppet.util/aws}) => nil))

            (fact "campfire message is sent when compilation error"
                  (with-messages {:env "env" :app-name "app-name"} (throw (clojure.lang.Compiler$CompilerException. "message" 0 0 (Throwable.)))) => (throws clojure.lang.Compiler$CompilerException)
                  (provided
                   (error anything) => nil))

            (fact "messages are sent to default rooms"
                  (with-messages {:env "env"
                                  :app-name "app-name"}
                    (info "info")
                    (error {})) => anything
                    (provided
                     (env :service-campfire-off) => nil
                     (env :service-campfire-default-info-room) => "default info"
                     (env :service-campfire-default-error-room) => "default error"
                     (#'shuppet.campfire/room "default info") => ..default-info..
                     (#'shuppet.campfire/room "default error") => ..default-error..
                     (#'shuppet.campfire/error-messages {}) => ["error"]
                     (cf/message ..default-info.. "info") => ..response..
                     (cf/message ..default-info.. "error") => ..response..
                     (cf/message ..default-error.. "error") => ..response..))

            (fact "messages are sent to all the rooms"
                  (with-messages {:env "env"
                                  :app-name "app-name"
                                  :config {:Campfire {:Info "info"
                                                      :Error "error"}}}
                    (info "info")
                    (error {})) => anything
                    (provided
                     (env :service-campfire-off) => nil
                     (env :service-campfire-default-info-room) => "default info"
                     (env :service-campfire-default-error-room) => "default error"
                     (#'shuppet.campfire/room "default info") => ..default-info..
                     (#'shuppet.campfire/room "default error") => ..default-error..
                     (#'shuppet.campfire/room "info") => ..info..
                     (#'shuppet.campfire/room "error") => ..error..
                     (#'shuppet.campfire/error-messages {}) => ["error"]
                     (cf/message ..default-info.. "info") => ..response..
                     (cf/message ..info.. "info") => ..response..

                     (cf/message ..default-info.. "error") => ..response..
                     (cf/message ..default-error.. "error") => ..response..
                     (cf/message ..info.. "error") => ..response..
                     (cf/message ..error.. "error") => ..response..)))
