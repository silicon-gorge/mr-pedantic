(ns pedantic.hubot-test
  (:require [clj-http.client :as http]
            [environ.core :refer [env]]
            [midje.sweet :refer :all]
            [pedantic.hubot :refer :all]))

(def hubot-before
  (intern 'pedantic.hubot 'hubot-on?))

(intern 'pedantic.hubot 'hubot-on? true)

(fact "that info messages can be sent"
      (info {:environment "env" :application "app" :report [{:message "message1"}
                                                            {:message "message2"}]})
      => nil
      (provided
       (http/post "http://hubot/hubot/say" {:content-type :json
                                            :body "{\"room\":\"pedantic-info\",\"message\":\"Environment: env\"}"
                                            :socket-timeout 5000}) => nil
       (http/post "http://hubot/hubot/say" {:content-type :json
                                            :body "{\"room\":\"pedantic-info\",\"message\":\"App: app\"}"
                                            :socket-timeout 5000}) => nil
       (http/post "http://hubot/hubot/say" {:content-type :json
                                            :body "{\"room\":\"pedantic-info\",\"message\":\"message1\"}"
                                            :socket-timeout 5000}) => nil
       (http/post "http://hubot/hubot/say" {:content-type :json
                                            :body "{\"room\":\"pedantic-info\",\"message\":\"message2\"}"
                                            :socket-timeout 5000}) => nil))

(fact "that error messages can be sent"
      (error {:message "message"})
      => nil
      (provided
       (http/post "http://hubot/hubot/say" {:content-type :json
                                            :body "{\"room\":\"pedantic-error\",\"message\":\":message message\"}"
                                            :socket-timeout 5000}) => nil))

(fact "that 500s are never sent to Campfire"
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
