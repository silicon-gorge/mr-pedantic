(ns shuppet.ec2
  (:require
   [shuppet
    [util :refer :all]
    [campfire :as cf]
    [core-shuppet :refer :all]]
   [environ.core :refer [env]]
   [clj-http.client :as client]
   [clojure.tools.logging :as log]
   [clojure.data.zip.xml :refer [xml1-> text xml->]]
   [clojure.xml :as xml]
   [clojure.zip :as zip]
   [slingshot.slingshot :refer [try+ throw+]]
   [nephelai.core :as nephelai]))

(defn get-request
  [params]
  (let [response (nephelai/process-request {:url (env :service-aws-ec2-url)
                                            :params (merge
                                                     {:Version (env :service-aws-ec2-api-version)}
                                                     params)
                                            :aws-key (*aws-credentials* :key)
                                            :aws-secret (*aws-credentials* :secret)})
        status (:status response)
        body (xml-to-map (:body response))]
    (log/info "EC2 request: " (env :service-aws-ec2-url))
    (if (= 200 status)
      body
      (throw-aws-exception "EC2" (get params "Action") (env :service-aws-ec2-url) status body))))
