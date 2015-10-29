(ns pedantic.sqs-test
  (:require [amazonica.aws.sqs :as sqs]
            [environ.core :refer [env]]
            [midje
                [sweet :refer :all]
                [util :refer :all]]
            [pedantic
             [environments :as environments]
             [sqs :refer :all]]))

(fact "that we've read the correct property for determining whether SQS is enabled"
      (sqs-enabled?) => false)

(fact "that sending a message only occurs if it's been enabled"
      (send-message "queue-url" "message") => nil
      (provided
       (sqs-enabled?) => false
       (sqs/send-message anything
                         :queue-url anything
                         :delay-seconds anything
                         :message-body anything) => nil :times 0))

(fact "that sending a message occurs if it's been enabled"
      (send-message "queue-url" "message") => nil
      (provided
       (sqs-enabled?) => true
       (sqs/send-message anything
                         :queue-url "queue-url"
                         :delay-seconds 0
                         :message-body "message") => ..send-result..))

(fact "that an SQS message has the correct format"
      (elb-created-message "message") => "{\"Message\":\"{\\\"Event\\\":\\\"autoscaling:ELB_LAUNCH\\\",\\\"LoadbalancerName\\\":\\\"message\\\"}\"}")

(fact "that announcing an ELB does nothing when there is no announcements queue configured for the environment"
      (announce-elb "elb" "environment") => nil
      (provided
       (environments/autoscaling-queue "environment") => nil
       (send-message anything anything) => nil :times 0))

(fact "that announcing an ELB sends a message when there is an announcements queue for the environment"
      (announce-elb "elb" "environment") => nil
      (provided
       (environments/autoscaling-queue "environment") => "queue-url"
       (elb-created-message "elb") => "message"
       (send-message "queue-url" "message") => ..send-result..))
