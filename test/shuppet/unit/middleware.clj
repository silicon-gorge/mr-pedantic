(ns shuppet.unit.middleware
  (:require  [environ.core :refer [env]])
  (:use [shuppet.middleware]
        [midje.sweet]))

(fact-group :unit
            (fact "listed environment allowed"
                  ((wrap-check-env (fn [req] req)) {:uri "/1.x/envs/prod"}) => {:uri "/1.x/envs/prod"}
                  (provided
                   (env :service-environments) => "prod,poke"))

            (fact "non listed environment not allowed"
                  ((wrap-check-env (fn [req] req)) {:uri "/1.x/envs/something"}) => (contains {:status 403})
                  (provided
                   (env :service-environments) => "prod,poke"))

            (fact "non environment path always allowed"
                  ((wrap-check-env (fn [req] req)) {:uri "/1.x/something"}) => {:uri "/1.x/something"}))
