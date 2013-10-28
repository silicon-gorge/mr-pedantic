(ns shuppet.aws
  (:require
   [clj-http.client :as client]
   [slingshot.slingshot :refer [throw+ try+]]
   [environ.core :refer [env]]
   [shuppet.urlbuilder :as urlbuilder]
   [clojure.data.zip.xml :refer [xml1-> text]]
   [clojure.xml :as xml]
   [clojure.zip :as zip]
   [clojure.tools.logging :as log]))

(def ^:const ec2-url (env :service-aws-ec2-url))
(def ^:const ec2-version (env :service-aws-ec2-api-version))

(def ^:const sts-url (env :service-aws-sts-url))
(def ^:const sts-version (env :service-aws-sts-api-version))

(defn ec2-request
  [params]
  (let [url (urlbuilder/build-url ec2-url (merge {"Version" ec2-version} params))
        response (client/get url {:as :stream
                                  :throw-exceptions false})
        status (:status response)
        body (-> (:body response)
                 (xml/parse)
                 (zip/xml-zip))]
    (if (= 200 status)
      body
      (throw+ {:type ::clj-http-ec2
               :action (get params "Action")
               :status status
               :url url
               :message (str (xml1-> body :Errors :Error :Code text)
                             "\n"
                             (xml1-> body :Errors :Error :Message text))}))))

(defn elb-request
  [params]
  (let [response (client/get (urlbuilder/build-url
                              (env :service-aws-elb-url)
                              (merge {"Version" (env  :service-aws-elb-version)}  params))
                             {:as :stream
                              :throw-exceptions false})
        status (:status response)
        body (-> (:body response)
                 (xml/parse)
                 (zip/xml-zip))]
    (if (= 200 status)
      body
      (throw+ {:type ::clj-http-elb
               :status status
               :code (xml1-> body :Error :Code text)
               :message (xml1-> body :Error :Message text) }))))

(defn decode-message
  [encoded-message]
  (let [url (urlbuilder/build-url "post" sts-url {"Action" "DecodeAuthorizationMessage"
                                                  "EncodedMessage" encoded-message
                                                  "Version" sts-version})
        response (client/post url { :content-type "application/x-www-form-urlencoded; charset=utf-8" :throw-exceptions false})]
    (prn "Decoded message response = " response)
    response))

(defn security-group-id [group-name]
  (xml1-> (ec2-request {"Action" "DescribeSecurityGroups"
                        "Filter.1.Name" "group-name"
                        "Filter.1.Value" group-name})
          :securityGroupInfo :item :groupId text))
