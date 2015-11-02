(ns pedantic.aws-test
  (:require [amazonica.aws.securitytoken :as sts]
            [midje.sweet :refer :all]
            [pedantic
             [aws :refer :all]
             [environments :as environments]
             [identity :as id]]))

(binding [application "application"
          environment "environment"
          region "region"]

  (fact "that asking for configuration for an environment which is based in the current account uses default credentials"
        (config) => {:endpoint "region"}
        (provided
         (environments/account-id "environment") => "account-id"
         (id/current-account-id) => "account-id"))

  (fact "that asking for configuration for an environment which is based in a different account attempts to assume a role"
        (config) => {:endpoint "region"
                     :some ..credentials..}
        (provided
         (environments/account-id "environment") => "other-account-id"
         (id/current-account-id) => "account-id"
         (sts/assume-role :duration-seconds 1800
                          :role-arn "arn:aws:iam::other-account-id:role/pedantic"
                          :role-session-name "pedantic")
         => {:credentials {:some ..credentials..}})))
