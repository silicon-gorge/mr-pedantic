(ns shuppet.git-test
  (:require [midje.sweet :refer :all]
            [shuppet.git :refer :all]
            [tentacles
             [repos :as repos]]))

(fact "that getting the data for the poke environment uses the master branch"
      (get-data "poke" "application")
      => "poke-content"
      (provided
       (repos/contents "shuppet" "application" "application.clj" {:ref "master"
                                                                  :str? true})
       => {:content "poke-content"}))

(fact "that getting the data for the prod environment doesn't use the master branch"
      (get-data "prod" "application")
      => "prod-content"
      (provided
       (repos/contents "shuppet" "application" "application.clj" {:ref "prod"
                                                                  :str? true})
       => {:content "prod-content"}))
