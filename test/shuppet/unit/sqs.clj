(ns shuppet.unit.sqs
  (:use [shuppet.sqs]
        [midje.util]
        [midje.sweet]))

(testable-privates shuppet.sqs elb-created-message)

(fact-group :unit
            (fact "sqs message has a funny format"
                  (elb-created-message "message") => "{\"Message\":\"{\\\"Event\\\":\\\"autoscaling:ELB_LAUNCH\\\",\\\"LoadbalancerName\\\":\\\"message\\\"}\"}"))
