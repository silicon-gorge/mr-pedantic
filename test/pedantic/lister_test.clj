(ns pedantic.lister-test
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [midje.sweet :refer :all]
            [pedantic.lister :refer :all])
  (:import clojure.lang.ExceptionInfo))

(fact "that when getting an application we do the right thing in the happy case"
      (application "app-name")
      => ..details..
      (provided
       (http/get "http://lister/applications/app-name" {:throw-exceptions false})
       => {:status 200
           :body ..body..}
       (json/parse-string ..body.. true)
       => {:metadata ..details..}))

(fact "that when getting an application that doesn't exist we get `nil`"
      (application "app-name")
      => nil
      (provided
       (http/get "http://lister/applications/app-name" {:throw-exceptions false})
       => {:status 404}))

(fact "that getting applications works properly"
      (applications)
      => ["name1" "name2"]
      (provided
       (http/get "http://lister/applications" {:throw-exceptions false})
       => {:status 200
           :body "{\"applications\":[\"name1\",\"name2\"]}"}))

(fact "that getting an environment works properly when the environment exists"
      (environment "env") => ..env..
      (provided
       (http/get "http://lister/environments/env" {:throw-exceptions false}) => {:status 200
                                                                                 :body ..body..}
       (json/parse-string ..body.. true) => ..env..))

(fact "that getting an environment works properly when using a keyword"
      (environment :env) => ..env..
      (provided
       (http/get "http://lister/environments/env" {:throw-exceptions false}) => {:status 200
                                                                                 :body ..body..}
       (json/parse-string ..body.. true) => ..env..))

(fact "that getting an environment which doesn't exist gives nil"
      (environment "env") => nil
      (provided
       (http/get "http://lister/environments/env" {:throw-exceptions false}) => {:status 404}))

(fact "that getting environments works properly"
      (environments) => #{"one" "two"}
      (provided
       (http/get "http://lister/environments" {:throw-exceptions false}) => {:status 200
                                                                             :body ..body..}
       (json/parse-string ..body.. true) => {:environments ["one" "two"]}))
