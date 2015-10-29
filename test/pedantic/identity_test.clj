(ns pedantic.identity-test
  (:require [midje.sweet :refer :all]
            [ninjakoala.instance-metadata :as im]
            [pedantic.identity :refer :all]))

(fact "that identity is unhealthy if there's nothing stored"
      (do (init) (healthy?)) => falsey
      (provided
       (im/instance-identity) => nil))

(fact "that identity is healthy if something is stored"
      (do (init) (healthy?)) => truthy
      (provided
       (im/instance-identity) => {}))
