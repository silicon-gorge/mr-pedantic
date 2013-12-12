(ns shuppet.signature
  (:require
   [shuppet.util :refer :all]
   [nephelai.core :refer [signed-request]]
   [environ.core :refer [env]]))

(def default-keys-map
  {:key (env :service-aws-access-key-id-poke)
   :secret (env :service-aws-secret-access-key-poke)})

(def ^:dynamic *aws-credentials* default-keys-map)

(def ^:private endpoints {:ec2-url (env :service-aws-ec2-url)
                          :ec2-version (env :service-aws-ec2-api-version)
                          :elb-url (env :service-aws-elb-url)
                          :elb-version (env :service-aws-elb-api-version)
                          :iam-url (env :service-aws-iam-url)
                          :iam-version (env :service-aws-iam-api-version)
                          :ddb-url (env :service-aws-ddb-url)
                          :s3-url (env :service-aws-s3-url)
                          :sqs-version (env :service-aws-sqs-api-version)})

(defn get-signed-request
  [suffix opts]
  (let [opts (merge {:url (endpoints (keyword (str suffix "-url")))}
                     opts
                     *aws-credentials*)
        opts (if (opts :params)
               (merge opts
                      {:params (without-nils (assoc (:params opts)
                                               :Version (endpoints (keyword (str suffix "-version")))))})
               opts)]
    (signed-request opts)))
