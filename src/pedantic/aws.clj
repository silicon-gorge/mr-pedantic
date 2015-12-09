(ns pedantic.aws
  (:require [amazonica.aws.securitytoken :as sts]
            [clojure.core.memoize :as memo]
            [clojure.tools.logging :refer [info]]
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

(defn- to-seconds
  [minutes]
  (* minutes 60))

(defn- to-millis
  [minutes]
  (* (to-seconds minutes) 1000))

(defn- role-arn
  [account-id]
  (format "arn:aws:iam::%s:role/%s" account-id role-name))

(defn- assume-role*
  [account-id]
  (info "Assuming role")
  (:credentials (guarded (sts/assume-role :duration-seconds (to-seconds (+ credentials-ttl 5)) :role-arn (role-arn account-id) :role-session-name "pedantic"))))

(def assume-role
  (if credentials-ttl-enabled?
    (memo/ttl assume-role* :ttl/threshold (to-millis credentials-ttl))
    assume-role*))

(defn alternative-credentials-if-necessary
  [environment-name]
  (let [account-id (environments/account-id environment-name)]
    (when-not (= account-id (id/current-account-id))
      (assume-role account-id))))

(defn config
  []
  (merge (alternative-credentials-if-necessary environment)
         {:endpoint region}))

(defn switch-amazonica-memoization!
  []
  (when credentials-ttl-enabled?
    (let [new-client-fn (memo/ttl #'amazonica.core/amazon-client* :ttl/threshold (to-millis (+ credentials-ttl 5)))]
      (alter-var-root (var amazonica.core/amazon-client) (fn [_] #(new-client-fn %1 %2 %3))))))

(defn init
  []
  (switch-amazonica-memoization!))
