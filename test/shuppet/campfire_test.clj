(ns shuppet.campfire-test
  (:require [clj-campfire.core :as cf]
            [environ.core :refer [env]]
            [midje.sweet :refer :all]
            [shuppet.campfire :refer :all]))

(def campfire-before
  (intern 'shuppet.campfire 'campfire-on?))

(intern 'shuppet.campfire 'campfire-on? true)

(fact "that we can get a room"
      (room "the-room") => "room-id"
      (provided
       (cf/room-by-name {:api-token "campfire-api-token"
                         :ssl true
                         :sub-domain "campfire-sub-domain"} "the-room") => "room-id"))

(fact "that info messages can be sent"
      (info {:environment "env" :application "app" :report [{:message "message1"}
                                                            {:message "message2"}]})
      => nil
      (provided
       (room "info-room") => ..room..
       (cf/message ..room.. #"env") => nil
       (cf/message ..room.. #"app") => nil
       (cf/message ..room.. #"message1") => nil
       (cf/message ..room.. #"message2") => nil))

(fact "that error messages can be sent"
      (error {:message "message"})
      => nil
      (provided
       (room "info-room") => ..room..
       (cf/message ..room.. #"message") => nil))

(fact "that 500s are never sent to Campfire"
      (error {:status 500})
      => nil
      (provided
       (cf/message anything anything) => nil :times 0))

(intern 'shuppet.campfire 'campfire-on? false)

(fact "that info messages can be sent"
      (info {:environment "env" :application "app" :report [{:message "message1"}
                                                            {:message "message2"}]})
      => nil
      (provided
       (cf/message anything anything) => nil :times 0))

(fact "that error messages are not sent when Campfire is off"
      (error {:message "message"})
      => nil
      (provided
       (cf/message anything anything) => nil :times 0))

(intern 'shuppet.campfire 'campfire-on? campfire-before)
