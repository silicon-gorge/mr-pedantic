(ns pedantic.aws
  (:require [amazonica.aws.securitytoken :as sts]
            [clojure.core.memoize :as memo]
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

(def ^:private role-name
  (env :aws-role-name "pedantic"))

(def ^:private credentials-ttl
  (Integer/valueOf (env :credentials-ttl-minutes 25)))

(def ^:private credentials-ttl-enabled?
  (Boolean/valueOf (env :credentials-ttl-enabled)))

(defn- to-millis
  [minutes]
  (* minutes 60 1000))

(defn- role-arn
  [account-id]
  (format "arn:aws:iam::%s:role/%s" account-id role-name))

(defn- alternative-credentials-if-necessary*
  [environment-name]
  (let [account-id (environments/account-id environment-name)]
    (when-not (= account-id (id/current-account-id))
      (:credentials (guarded (sts/assume-role :duration-seconds (to-millis (+ credentials-ttl 5)) :role-arn (role-arn account-id) :role-session-name "pedantic"))))))

(def alternative-credentials-if-necessary
  (if credentials-ttl-enabled?
    (memo/ttl alternative-credentials-if-necessary* :ttl/threshold (to-millis credentials-ttl))
    alternative-credentials-if-necessary*))

(defn config
  []
  (merge (alternative-credentials-if-necessary environment)
         {:endpoint region}))
