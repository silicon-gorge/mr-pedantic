(ns shuppet.git-test
  (:require [midje.sweet :refer :all]
            [shuppet.git :refer :all]
            [tentacles
             [repos :as repos]]))

(fact "that getting the data for an application uses the master branch"
      (get-data "application")
      => "content"
      (provided
       (repos/contents "shuppet" "application" "application.clj" {:ref "master"
                                                                  :str? true})
       => {:content "content"}))
