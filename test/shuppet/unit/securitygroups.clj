(ns shuppet.unit.securitygroups
  (:require [midje.util :refer [testable-privates]]
             [clojure.data.zip.xml :refer [xml1->]])
  (:use [shuppet.securitygroups]
        [midje.sweet]))

(testable-privates shuppet.securitygroups update-sg-ids)

(fact-group :unit
            (fact "only sg names are retrieved by ids"
                  (sg-id "sg-id" "vpc") => "sg-id"
                  (sg-id "1234" "vpc") => "1234"
                  (sg-id "name" "vpc") => ..id..
                  (provided
                   (#'shuppet.securitygroups/retrieve-sg-id "name" "vpc")=> ..id.. :times 1))

            (fact "sg names are replaced"
                  (update-sg-ids [{:IpRanges "name"}] "vpc") => `({:IpRanges ..id..})
                  (provided
                   (sg-id "name" "vpc") => ..id..)))
