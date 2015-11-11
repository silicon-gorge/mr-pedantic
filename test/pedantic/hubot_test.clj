(ns pedantic.hubot-test
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [environ.core :refer [env]]
            [midje.sweet :refer :all]
            [pedantic.hubot :refer :all]))

(def hubot-before
  (intern 'pedantic.hubot 'hubot-on?))

(intern 'pedantic.hubot 'hubot-on? true)

(fact "that a singular info message can be sent"
      (info {:environment "env" :application "app" :report [{:message "message"}]})
      => nil
      (provided
       (json/generate-string {:room "pedantic-info" :message "A Pedantic change has been made for *app* in *env*\n>>>\nmessage"}) => ..json..
       (http/post "http://hubot/hubot/say" {:body ..json..
                                            :content-type :json
                                            :socket-timeout 5000
                                            :throw-exceptions false}) => nil :times 1))

(fact "that a pluralised info message can be sent"
      (info {:environment "env" :application "app" :report [{:message "message1"}
                                                            {:message "message2"}]})
      => nil
      (provided
       (json/generate-string {:room "pedantic-info" :message "Some Pedantic changes have been made for *app* in *env*\n>>>\nmessage1\nmessage2"}) => ..json..
       (http/post "http://hubot/hubot/say" {:body ..json..
                                            :content-type :json
                                            :socket-timeout 5000
                                            :throw-exceptions false}) => nil :times 1))

(fact "that error messages can be sent"
      (error {:code "code" :message "message" :status 400 :title "title" :type :pedantic.util/aws :environment "env" :application "app"})
      => nil
      (provided
       (json/generate-string {:room "pedantic-error" :message "An error occurred while synchronizing configuration for *app* in *env*\n>>>\nStatus 400 - code\ntitle - message"}) => ..json..
       (http/post "http://hubot/hubot/say" {:body ..json..
                                            :content-type :json
                                            :socket-timeout 5000
                                            :throw-exceptions false}) => nil :times 1))

(fact "that 500s are never sent to Hubot"
      (error {:status 500})
      => nil
      (provided
       (http/post anything anything) => nil :times 0))

(intern 'pedantic.hubot 'hubot-on? false)

(fact "that info messages are not sent when Hubot is disabled"
      (info {:environment "env" :application "app" :report [{:message "message1"}
                                                            {:message "message2"}]})
      => nil
      (provided
       (http/post anything anything) => nil :times 0))

(fact "that error messages are not sent when Hubot is disabled"
      (error {:message "message"})
      => nil
      (provided
       (http/post anything anything) => nil :times 0))

(intern 'pedantic.hubot 'hubot-on? hubot-before)
