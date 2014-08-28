(ns shuppet.campfire-test
  (:require [clj-campfire.core :as cf]
            [environ.core :as env]
            [midje.sweet :refer :all]
            [shuppet.campfire :refer :all]))

(fact "that info messages can be sent"
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

(fact "that error messages can be sent"
      (error {:title "title"
              :message "message"}) => anything
              (provided
               (env/env anything) => nil
               (room anything) => ..room..
               (cf/message ..room.. #"title") => nil
               (cf/message ..room.. #"message") => nil))

(fact "that 500s are never sent to Campfire"
      (error {:status 500}) => anything
      (provided
       (env/env anything) => nil
       (cf/message anything anything) => nil :times 0))
