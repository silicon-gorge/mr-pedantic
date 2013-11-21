(ns shuppet.middleware
  (:require
   [slingshot.slingshot :refer [try+ throw+]]
   [clojure.data.json :refer [write-str]]
   [ring.util.response :as ring-response]))

(defn wrap-shuppet-error
  [handler]
  (fn [req]
    (try+
     (handler req)
     (catch [:type :shuppet.git/git] e
       (->  (ring-response/response (write-str {:message (e :message)}))
            (ring-response/content-type "application/json")
            (ring-response/status (e :status))))
     (catch [:type :shuppet.util/aws] e
       (->  (ring-response/response (write-str e))
            (ring-response/content-type "application/json")
            (ring-response/status 409)))
     (catch [:type :_] e
       (->  (ring-response/response (write-str {:message (e :message)}))
            (ring-response/content-type "application/json")
            (ring-response/status (e :status))))
     (catch  clojure.lang.Compiler$CompilerException e
       (->  (ring-response/response (write-str {:message (.getMessage e)}))
            (ring-response/content-type "application/json")
            (ring-response/status 400)))
      (catch java.io.FileNotFoundException e
       (->  (ring-response/response (write-str {:message "Cannot find this one"}))
            (ring-response/content-type "application/json")
            (ring-response/status 404))))))
