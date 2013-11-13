(ns shuppet.web
  (:require
   [shuppet.core :as core]
   [slingshot.slingshot :refer [try+ throw+]]
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
   [nokia.ring-utils.error :refer [wrap-error-handling error-response]]
   [nokia.ring-utils.metrics :refer [wrap-per-resource-metrics replace-outside-app
                                     replace-guid replace-mongoid replace-number]]
   [nokia.ring-utils.ignore-trailing-slash :refer [wrap-ignore-trailing-slash]]
   [metrics.ring.expose :refer [expose-metrics-as-json]]
   [metrics.ring.instrument :refer [instrument]]))

(def ^:dynamic *version* "none")

(defn set-version!
  [version]
  (alter-var-root #'*version* (fn [_] version)))

(defn- response [body]
  (merge
   {:status 200
    :headers {"Content-Type" "application/json"}}
   (if (nil? body)
     {}
     {:body body})))

(def ^:private cf-info-room (env :service-campfire-default-info-room))
(def ^:private cf-error-room (env :service-campfire-default-error-room))
(def ^:private environments (env :service-environments))

(defroutes applications-routes

  (GET "/" []
       (response {:environments (split environments #",")}))

  (GET "/:env"
       [env]
       (->  (ring-response/response (-> (core/get-config env) (write-str)))
            (ring-response/content-type "application/json")))

  (GET "/:env/apply"
       [env]
       (core/apply-config env)
       (response  {:message (str "Succesfully applied the configuration for environment " env "."
                                 "Please check the campfire room '" cf-info-room "' for a detailed report.")}))

  (GET "/:env/apps"
       [env]
       (response {:applications (core/get-app-names env)}))

  (GET "/:env/apps/apply"
       [env]
       (core/update-configs env)
       (response  {:message (str "Started applying the configuration for all applications in environment " env "."
                                 "Please check the campfire room '" cf-error-room  "' for any error cases.")}))

  (GET "/:env/apps/:app-name"
       [env app-name]
       (->  (ring-response/response (-> (core/get-config env app-name) (write-str)))
            (ring-response/content-type "application/json")))


  (GET "/:env/apps/:name/apply"
       [env name]
       (core/apply-config env name)
       (response {:message (str "Succesfully applied the configuration for application " name "."
                                "Please check the campfire room '" cf-info-room "' for a detailed report.")}))

  (GET "/:env/apps/:name/clean"
       [env name]
       (core/clean-config env name)
       (response {:message (str "Succesfully cleaned the configuration for application " name)})))

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

   (context "/envs"
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
