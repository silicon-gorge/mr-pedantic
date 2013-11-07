(ns shuppet.aws
  (:require
   [clj-http.client :as client]
   [slingshot.slingshot :refer [throw+ try+]]
   [environ.core :refer [env]]
   [shuppet.signature :as sign]
   [shuppet.util :refer :all]
   [clojure.data.zip.xml :refer [xml1-> text]]
   [clojure.xml :as xml]
   [clojure.zip :as zip]
   [clojure.tools.logging :as log]))

(def ^:const ec2-url (env :service-aws-ec2-url))
(def ^:const ec2-version (env :service-aws-ec2-api-version))

(def ^:const iam-url (env :service-aws-iam-url))
(def ^:const iam-version (env :service-aws-iam-api-version))

(defn ec2-request
  [params]
  (let [url (sign/v2-url ec2-url (merge {"Version" ec2-version} params))
        response (client/get url {:as :stream
                                  :throw-exceptions false})
        status (:status response)
        body (-> (:body response)
                 (xml/parse)
                 (zip/xml-zip))]
    (if (= 200 status)
      body
      (throw-aws-exception "EC2" (get params "Action") url status body))))

(defn iam-post-request
  [params]
  (let [q-string (map-to-query-string (merge {"Version" iam-version} params))
        url (str iam-url "/?" q-string)
        auth-headers (sign/v4-auth-headers {:url url
                                            :method :post} )
        response (client/post url {:headers auth-headers
                                   :as :stream
                                   :throw-exceptions false})
        status (:status response)
        body (-> (:body response)
                 (xml/parse)
                 (zip/xml-zip))]
    (condp = status
      200 body
      (throw-aws-exception "IAM" (get params "Action") url status body))))

(defn iam-request
  [params]
  (let [q-string (map-to-query-string (merge {"Version" iam-version} params))
        url (str iam-url "/?" q-string)
        auth-headers (sign/v4-auth-headers {:url url} )
        response (client/get url {:headers auth-headers
                                  :as :stream
                                  :throw-exceptions false})
        status (:status response)
        body (-> (:body response)
                 (xml/parse)
                 (zip/xml-zip))]
    (condp = status
      200 body
      404 nil
      (throw-aws-exception "IAM" (get params "Action") url status body))))

(defn elb-request
  [params]
  (let [q-string (map-to-query-string (merge {"Version" (env  :service-aws-elb-version)} params))
        url (str (env :service-aws-elb-url) "/?" q-string)
        auth-headers (sign/v4-auth-headers {:url url} )
        response (client/get url
                             {:headers auth-headers
                              :as :stream
                              :throw-exceptions false})
        status (:status response)
        body (-> (:body response)
                 (xml/parse)
                 (zip/xml-zip))]
    (if (= 200 status)
      body
      (throw-aws-exception "ELB" (get params "Action") url status body))))
