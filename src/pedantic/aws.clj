(ns pedantic.aws
  (:require [amazonica.aws.securitytoken :as sts]
            [environ.core :refer [env]]
            [pedantic
             [environments :as environments]
             [guard :refer [guarded]]
             [identity :as id]]))

(def ^:dynamic application
  nil)

(def ^:dynamic environment
  nil)

(def ^:dynamic region
  nil)

(def ^:dynamic role-name
  (env :aws-role-name "pedantic"))

(defn- role-arn
  [account-id]
  (format "arn:aws:iam::%s:role/%s" account-id role-name))

(defn alternative-credentials-if-necessary
  [environment-name]
  (let [account-id (environments/account-id environment-name)]
    (when-not (= account-id (id/current-account-id))
      (:credentials (guarded (sts/assume-role {:role-arn (role-arn account-id) :role-session-name "pedantic"}))))))

(defn config
  []
  (merge (alternative-credentials-if-necessary environment)
         {:endpoint region}))
