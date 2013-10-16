(ns shuppet.aws
  (:require
   [clj-http.client :as client]
   [environ.core :refer [env]]
   [shuppet.urlbuilder :as urlbuilder]))

(def ^:const ec2-url (env :service-aws-ec2-url))

(defn ec2-request [params]
  (let [url (urlbuilder/build-url ec2-url params)]
    (client/get url {:as :xml
                     :throw-exceptions false})))
