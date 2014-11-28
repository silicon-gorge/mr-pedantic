(ns shuppet.middleware-test
  (:require [environ.core :refer [env]]
            [midje.sweet :refer :all]
            [shuppet.middleware :refer :all]))

(fact "that a listed environment is allowed"
      ((wrap-check-env (fn [req] req)) {:uri "/envs/prod"}) => {:uri "/envs/prod"}
      (provided
       (env :environments) => "prod,poke"))

(fact "that a non-listed environment is not found"
      ((wrap-check-env (fn [req] req)) {:uri "/envs/something"}) => (contains {:status 404})
      (provided
       (env :environments) => "prod,poke"))

(fact "that a non-environment path is always allowed"
      ((wrap-check-env (fn [req] req)) {:uri "/something"}) => {:uri "/something"})
