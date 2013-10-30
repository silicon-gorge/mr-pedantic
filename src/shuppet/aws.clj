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

(def ^:const iam-url (env :service-aws-iam-url))
(def ^:const iam-version (env :service-aws-iam-api-version))

(def ^:const sts-url (env :service-aws-sts-url))
(def ^:const sts-version (env :service-aws-sts-api-version))

(defn throw-aws-exception
  [title action url status message]
  (throw+ {:type ::aws
           :title (str title " request failed while performing the action '" action "'")
           :url url
           :status status
           :message message}))

(defn- get-message
  [body]
  (str (xml1-> body :Errors :Error :Code text)
       "\n"
       (xml1-> body :Errors :Error :Message text)))

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
      (throw-aws-exception "EC2" (get params "Action") url status (get-message body)))))

(defn iam-request
  [params]
  (let [url (urlbuilder/build-url iam-url (merge {"Version" iam-version} params))
        response (client/get url {:as :stream
                                  :throw-exceptions false})
        status (:status response)
        body (-> (:body response)
                 (xml/parse)
                 (zip/xml-zip))]
    (condp = status
      200 body
      404 nil
      (throw-aws-exception "IAM" (get params "Action") url status (get-message body)))))

(defn elb-request
  [params]
  (let [url (urlbuilder/build-url
             (env :service-aws-elb-url)
             (merge {"Version" (env  :service-aws-elb-version)}  params))
        response (client/get url
                             {:as :stream
                              :throw-exceptions false})
        status (:status response)
        body (-> (:body response)
                 (xml/parse)
                 (zip/xml-zip))]
    (if (= 200 status)
      body
      (throw-aws-exception "ELB" (get params "Action") url status (get-message body))))

  (defn decode-message
    [encoded-message]
    (let [url (urlbuilder/build-url "post" sts-url {"Action" "DecodeAuthorizationMessage"
                                                    "EncodedMessage" encoded-message
                                                    "Version" sts-version})
          response (client/post url { :content-type "application/x-www-form-urlencoded; charset=utf-8" :throw-exceptions false})]
      (prn "Decoded message response = " response)
      response)))

(defn security-group-id [group-name]
  (xml1-> (ec2-request {"Action" "DescribeSecurityGroups"
                        "Filter.1.Name" "group-name"
                        "Filter.1.Value" group-name})
          :securityGroupInfo :item :groupId text))
