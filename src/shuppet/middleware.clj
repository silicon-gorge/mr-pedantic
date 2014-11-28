(ns shuppet.middleware
  (:require [cheshire.core :as json]
            [clojure.string :refer [split]]
            [environ.core :refer [env]]
            [ring.util.response :as ring-response]
            [slingshot.slingshot :refer [try+ throw+]]))

(defn wrap-shuppet-error
  [handler]
  (fn [req]
    (try+
     (handler req)
     (catch [:type :shuppet.validator/validator] e
       (->  (ring-response/response (json/generate-string {:message "Validation Failure"
                                                           :details (e :details)}))
            (ring-response/content-type "application/json")
            (ring-response/status 400)))
     (catch [:type :shuppet.git/git] e
       (->  (ring-response/response (json/generate-string {:message (e :message)}))
            (ring-response/content-type "application/json")
            (ring-response/status (e :status))))
     (catch [:type :cluppet.util/aws] e
       (->  (ring-response/response (json/generate-string e))
            (ring-response/content-type "application/json")
            (ring-response/status 409)))
     (catch [:type :_] e
       (->  (ring-response/response (json/generate-string {:message (e :message)}))
            (ring-response/content-type "application/json")
            (ring-response/status (e :status))))
     (catch [:type :cluppet.core/invalid-config] e
       (->  (ring-response/response (json/generate-string (select-keys e [:message])))
            (ring-response/content-type "application/json")
            (ring-response/status 400)))
     (catch java.io.FileNotFoundException e
       (->  (ring-response/response (json/generate-string {:message "Cannot find this one"}))
            (ring-response/content-type "application/json")
            (ring-response/status 404))))))

(defn- valid-env?
  [uri envs]
  (not-empty (filter identity (map
                               #(re-matches (re-pattern (str "/envs/" % ".*")) uri)
                               envs))))

(defn wrap-check-env
  [handler]
  (fn [{:keys [uri] :as req}]
    (if (re-matches #"/envs/.*" uri)
      (if (valid-env? uri (split (env :environments) #","))
        (handler req)
        {:message "Unknown environment"
         :status 404})
      (handler req))))
