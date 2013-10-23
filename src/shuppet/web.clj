(ns shuppet.web
  (:require
   [shuppet.core :as core]
   [compojure.core :refer [defroutes context GET PUT POST DELETE]]
   [compojure.route :as route]
   [compojure.handler :as handler]
   [ring.middleware.format-response :refer [wrap-json-response]]
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
(defn set-version! [version]
  (alter-var-root #'*version* (fn [_] version)))

(defn response [data content-type & [status]]
  {:status (or status 200)
   :headers {"Content-Type" content-type}
   :body data}

  (defroutes routes
    (context
     "/1.x" []

     (GET "/ping"
          [] {:status 200 :body "pong"})

     (GET "/status"
          [] {:status 200 :body {:name "shuppet"
                                 :version *version*
                                 :status true}})

     (GET "/icon" []
          {:status 200
           :headers {"Content-Type" "image/jpeg"}
           :body (-> (clojure.java.io/resource "shuppet.jpg")
                     (clojure.java.io/input-stream))})

     (GET "/:env/service/:name"
          [env name action]
          (if (or (empty? action)
                  (= action "print"))
            (core/configure env name action)
           {:status 400 :body (str "Invalid action " action "specified.")})))

    (GET "/healthcheck" []
         (response "I am healthy. Thank you for asking." "text/plain;charset=utf-8"))

    (route/not-found (error-response "Resource not found" 404))))


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
