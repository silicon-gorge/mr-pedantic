(ns shuppet.aws
  (:require
   [clj-http.client :as client]
   [environ.core :refer [env]]
   [shuppet.urlbuilder :as urlbuilder]
   [clojure.tools.logging :as log]))

(def ^:const ec2-url (or (env :service-aws-ec2-url) "https://ec2.eu-west-1.amazonaws.com"))
(def ^:const ec2-version (or (env :service-aws-ec2-api-version) "2013-10-01"))

(def ^:const sts-url (or (env :service-aws-sts-url) "https://sts.amazonaws.com"))
(def ^:const sts-version (or (env :service-aws-sts-api-version) "2011-06-15"))

(defn ec2-request [params]
  (let [url (urlbuilder/build-url ec2-url (merge {"Version" ec2-version} params))
        response (client/get url {:as :xml
                                  :throw-exceptions false})]
  ;  (prn "response = " response)
    (if (= 200 (get response :status))
      (get response :body)
      (log/info (str "EC2 request : " url "\n failed with response : " response)))))

(defn elb-request [params]
  (client/get (urlbuilder/build-url
               (env :service-aws-elb-url)
               (merge {"Version" (env  :service-aws-elb-version)}  params))
              {:as :stream
               :throw-exceptions false}))

(defn decode-message [encoded-message]
  (let [url (urlbuilder/build-url "post" sts-url {"Action" "DecodeAuthorizationMessage"
                                                  "EncodedMessage" encoded-message
                                                  "Version" sts-version})
        response (client/post url { :content-type "application/x-www-form-urlencoded; charset=utf-8" :throw-exceptions false})]
    (prn "Decoded message response = " response)
    response))
