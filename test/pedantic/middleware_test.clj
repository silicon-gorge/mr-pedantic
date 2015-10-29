(ns pedantic.middleware-test
  (:require [environ.core :refer [env]]
            [midje.sweet :refer :all]
            [pedantic
             [environments :as environments]
             [middleware :refer :all]]))

(fact "that a listed environment is allowed"
      ((wrap-check-env (fn [req] req)) {:uri "/envs/env2"}) => {:uri "/envs/env2"}
      (provided
       (environments/environment-names) => #{"env2" "env1"}))

(fact "that a non-listed environment is not found"
      ((wrap-check-env (fn [req] req)) {:uri "/envs/something"}) => (contains {:status 404})
      (provided
       (environments/environment-names) => #{"env2" "env1"}))

(fact "that a non-environment path is always allowed"
      ((wrap-check-env (fn [req] req)) {:uri "/something"}) => {:uri "/something"})
