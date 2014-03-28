(ns shuppet.middleware
  (:require
   [environ.core :refer [env]]
   [clojure.string :refer [split]]
   [slingshot.slingshot :refer [try+ throw+]]
   [clojure.data.json :refer [write-str]]
   [ring.util.response :as ring-response]))

(defn wrap-shuppet-error
  [handler]
  (fn [req]
    (try+
     (handler req)
     (catch [:type :shuppet.validator/validator] e
       (->  (ring-response/response (write-str {:message "Validation Failure"
                                                :details (e :details)}))
            (ring-response/content-type "application/json")
            (ring-response/status 400)))
     (catch [:type :shuppet.git/git] e
       (->  (ring-response/response (write-str {:message (e :message)}))
            (ring-response/content-type "application/json")
            (ring-response/status (e :status))))
     (catch [:type :cluppet.util/aws] e
       (->  (ring-response/response (write-str e))
            (ring-response/content-type "application/json")
            (ring-response/status 409)))
     (catch [:type :_] e
       (->  (ring-response/response (write-str {:message (e :message)}))
            (ring-response/content-type "application/json")
            (ring-response/status (e :status))))
     (catch [:type :cluppet.core/invalid-config] e
       (->  (ring-response/response (write-str (select-keys e [:message])))
            (ring-response/content-type "application/json")
            (ring-response/status 400)))
     (catch java.io.FileNotFoundException e
       (->  (ring-response/response (write-str {:message "Cannot find this one"}))
            (ring-response/content-type "application/json")
            (ring-response/status 404))))))

(defn- valid-env? [uri envs]
  (not-empty (filter identity (map
                               #(re-matches (re-pattern (str "/1.x/envs/" % ".*")) uri)
                               envs))))

(defn wrap-check-env
  [handler]
  (fn [{:keys [uri] :as req}]
    (if (re-matches #"/1.x/envs/.*" uri)
      (if (valid-env? uri (split (env :service-environments) #","))
        (handler req)
        (-> (ring-response/response (write-str {:message "Environment not allowed"}))
            (ring-response/content-type "application/json")
            (ring-response/status 403)))
      (handler req))))
