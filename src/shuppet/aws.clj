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

(def ^:const ec2-url (or (env :service-aws-ec2-url) "https://ec2.eu-west-1.amazonaws.com"))
(def ^:const ec2-version (or (env :service-aws-ec2-api-version) "2013-10-01"))

(def ^:const sts-url (or (env :service-aws-sts-url) "https://sts.amazonaws.com"))
(def ^:const sts-version (or (env :service-aws-sts-api-version) "2011-06-15"))

(defn ec2-request [params]
  (let [url (urlbuilder/build-url ec2-url (merge {"Version" ec2-version} params))
        response (client/get url {:as :stream
                                  :throw-exceptions false})
        status (:status response)
        body (-> (:body response)
                 (xml/parse)
                 (zip/xml-zip))]
    (if (= 200 status)
      body
      (throw+ {:type ::clj-http :status status :url url :body body}))))

(defn elb-request [params]
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
      (throw+ {:type ::clj-http :status status :code (xml1-> body :Error :Code text)}))))

(defn decode-message [encoded-message]
  (let [url (urlbuilder/build-url "post" sts-url {"Action" "DecodeAuthorizationMessage"
                                                  "EncodedMessage" encoded-message
                                                  "Version" sts-version})
        response (client/post url { :content-type "application/x-www-form-urlencoded; charset=utf-8" :throw-exceptions false})]
    (prn "Decoded message response = " response)
    response))
