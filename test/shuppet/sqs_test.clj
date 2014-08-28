(ns shuppet.sqs-test
  (:require [midje
             [sweet :refer :all]
             [util :refer :all]]
            [shuppet.sqs :refer :all]))

(testable-privates shuppet.sqs elb-created-message)

(fact "that an SQS message has the correct format"
      (elb-created-message "message") => "{\"Message\":\"{\\\"Event\\\":\\\"autoscaling:ELB_LAUNCH\\\",\\\"LoadbalancerName\\\":\\\"message\\\"}\"}")
