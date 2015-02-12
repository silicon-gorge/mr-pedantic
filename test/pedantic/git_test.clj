(ns pedantic.git-test
  (:require [midje.sweet :refer :all]
            [pedantic.git :refer :all]
            [tentacles
             [repos :as repos]]))

(fact "that getting the data for an application uses the master branch"
      (get-data "application")
      => "content"
      (provided
       (repos/contents "pedantic" "application" "application.clj" {:ref "master"
                                                                   :str? true
                                                                   :oauth-token "github-auth-token"})
       => {:content "content"}))
