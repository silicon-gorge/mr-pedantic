(ns shuppet.web
  (:require
   [shuppet.core :as core]
   [clojure.data.json :refer [write-str]]
   [compojure.core :refer [defroutes context GET PUT POST DELETE]]
   [compojure.route :as route]
   [compojure.handler :as handler]
   [ring.middleware.format-response :refer [wrap-json-response]]
   [ring.util.response :as ring-response]
   [ring.middleware.json-params :refer [wrap-json-params]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [clojure.data.xml :refer [element emit-str]]
   [clojure.string :refer [split]]
   [clojure.tools.logging :refer [info warn error]]
   [environ.core :refer [env]]
   [shuppet.errorhandler :refer [wrap-error-handling error-response]]
   [nokia.ring-utils.metrics :refer [wrap-per-resource-metrics replace-outside-app
                                     replace-guid replace-mongoid replace-number]]
   [nokia.ring-utils.ignore-trailing-slash :refer [wrap-ignore-trailing-slash]]
   [metrics.ring.expose :refer [expose-metrics-as-json]]
   [metrics.ring.instrument :refer [instrument]]))

(def ^:dynamic *version* "none")
(defn set-version!
  [version]
  (alter-var-root #'*version* (fn [_] version)))

(defroutes applications-routes

  (GET "/:env/app/:name/apply"
       [env name]
       (core/apply-config env name)
       {:status 200})

  (GET "/:env/app/:name/clean"
       [env name]
       (core/clean-config env name)
       {:status 200})

  (GET "/:env"
       [env]
       (->  (ring-response/response (-> (core/get-config env) (write-str)))
            (ring-response/content-type "application/json")))

  (GET "/:env/apply"
       [env]
       (core/apply-config env)
       {:status 200})

  (GET "/:env/app/:app-name"
       [env app-name]
       (->  (ring-response/response (-> (core/get-config env app-name) (write-str)))
            (ring-response/content-type "application/json")))

  (GET "/:env/app/apply"
       [env]
       (core/update-configs env)
       {:status 200}))

(defroutes routes
  (context
   "/1.x" []

   (GET "/ping"
        [] {:status 200 :body "pong"})

   (GET "/status"
        [] {:status 200 :body {:name "shuppet"
                               :version *version*
                               :status true}})

   (GET "/icon"
        []
        {:status 200
         :headers {"Content-Type" "image/jpeg"}
         :body (-> (clojure.java.io/resource "shuppet.jpg")
                   (clojure.java.io/input-stream))})

   (context "/env"
            [] applications-routes))

  (GET "/healthcheck"
       []
       (->  (ring-response/response "I am healthy. Thank you for asking.")
            (ring-response/content-type  "text/plain;charset=utf-8")))

  (route/not-found (error-response "Resource not found" 404)))


(def app
  (-> routes
      (instrument)
      (wrap-error-handling)
      (wrap-ignore-trailing-slash)
      (wrap-json-params)
      (wrap-keyword-params)
      (wrap-params)
      (wrap-json-response)
      (wrap-per-resource-metrics [replace-guid replace-mongoid replace-number (replace-outside-app "/1.x")])
      (expose-metrics-as-json)))
