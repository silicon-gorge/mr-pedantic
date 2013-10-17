(ns shuppet.securitygroups
  (:require
   [shuppet.aws :refer [ec2-request]]
   [clj-http.client :as client]
   [clojure.tools.logging :as log]
   [clojure.xml :as xml]
   [clojure.zip :as zip]
   [clojure.data.zip.xml :refer [xml1-> attr xml-> text text= attr=]]))


(defn xml-to-map [xml-string]
  (zip/xml-zip (xml/parse (java.io.ByteArrayInputStream. (.getBytes xml-string)))))

(defn create-sg
  "Creates a security group and returns the id"
  [params]
  (if-let [response (xml-to-map (ec2-request (merge params {"Action" "CreateSecurityGroup"})))]
    (first (xml-> response :groupId text))))

(defn delete-sg
  "Deletes a given security group"
  [params]
  (ec2-request (merge params {"Action" "DeleteSecurityGroup"})))

(defn describe-sg [params]
  (let [response (xml-to-map (ec2-request (merge params {"Action" "DescribeSecurityGroups"})))]
                                        ;TODO
    (prn "Description = " response)))


;(delete-sg {"GroupName" "test-sg3"})
;(create-sg {"GroupName" "test-sg3" "GroupDescription" "test description"})
;(create-sg {"GroupName" "test-sg3" "GroupDescription" "test description" "VpcId" "vpc-7bc88713" })
;(delete-sg {"GroupName" "test-sg3"})
;(describe-sg {"GroupId.1" "sg-9e7187f1"})
;(describe-sg {"GroupId" "sg-f457b09b"})
