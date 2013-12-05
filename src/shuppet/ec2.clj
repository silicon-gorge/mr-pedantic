(ns shuppet.ec2
  (:require
   [shuppet
    [signature :refer [v2-url]]
    [util :refer :all]
    [campfire :as cf]]
   [environ.core :refer [env]]
   [clj-http.client :as client]
   [clojure.tools.logging :as log]
   [clojure.data.zip.xml :refer [xml1-> text xml->]]
   [clojure.xml :as xml]
   [clojure.zip :as zip]
   [slingshot.slingshot :refer [try+ throw+]]))

(defn get-request
  [params]
  (let [url (v2-url (env :service-aws-ec2-url) (merge
                                                {"Version" (env :service-aws-ec2-api-version)}
                                                params))
        response (client/get url {:as :stream
                                  :throw-exceptions false})
        status (:status response)
        body (-> (:body response)
                 (xml/parse)
                 (zip/xml-zip))]
    (log/info "EC2 request: " url)
    (if (= 200 status)
      body
      (throw-aws-exception "EC2" (get params "Action") url status body))))
