(ns shuppet.unit.campfire
  (:require  [slingshot.slingshot :refer [try+ throw+]]
             [environ.core :as env]
             [clj-campfire.core :as cf])
  (:use [shuppet.campfire]
        [midje.sweet]))

(fact-group :unit
            (fact "info message can be sent"
                  (info {:env "env"
                         :app "app"
                         :report [{:action ..action..
                                   :message "message1"}
                                  {:action ..action..
                                   :message "message2"}]}) => anything
                  (provided
                   (cf/message ..room.. #"message1") => nil
                   (cf/message ..room.. #"message2") => nil
                   (env/env anything) => nil
                   (room anything) => ..room..
                   (cf/message anything #"env") => nil
                   (cf/message anything #"app") => nil))
            (fact "error message can be sent"
                  (error {:title "title"
                          :message "message"}) => anything
                  (provided
                   (env/env anything) => nil
                   (room anything) => ..room..
                   (cf/message ..room.. #"title") => nil
                   (cf/message ..room.. #"message") => nil))
            (fact "500s are never sent to campfire"
                  (error {:status 500}) => anything
                  (provided
                   (env/env anything) => nil
                   (cf/message anything anything) => nil :times 0)))
