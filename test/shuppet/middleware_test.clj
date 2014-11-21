(ns shuppet.middleware-test
  (:require [environ.core :refer [env]]
            [midje.sweet :refer :all]
            [shuppet.middleware :refer :all]))

(fact "that a listed environment is allowed"
      ((wrap-check-env (fn [req] req)) {:uri "/1.x/envs/prod"}) => {:uri "/1.x/envs/prod"}
      (provided
       (env :environments) => "prod,poke"))

(fact "that a non-listed environment is not found"
      ((wrap-check-env (fn [req] req)) {:uri "/1.x/envs/something"}) => (contains {:status 404})
      (provided
       (env :environments) => "prod,poke"))

(fact "that a non-environment path is always allowed"
      ((wrap-check-env (fn [req] req)) {:uri "/1.x/something"}) => {:uri "/1.x/something"})
