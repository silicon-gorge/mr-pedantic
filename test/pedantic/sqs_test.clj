(ns pedantic.sqs-test
  (:require [midje
             [sweet :refer :all]
             [util :refer :all]]
            [pedantic.sqs :refer :all]))

(testable-privates pedantic.sqs elb-created-message)

(fact "that an SQS message has the correct format"
      (elb-created-message "message") => "{\"Message\":\"{\\\"Event\\\":\\\"autoscaling:ELB_LAUNCH\\\",\\\"LoadbalancerName\\\":\\\"message\\\"}\"}")
