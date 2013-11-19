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
   [clojure.string :refer [split lower-case]]
   [environ.core :refer [env]]
   [nokia.ring-utils.error :refer [wrap-error-handling error-response]]
   [nokia.ring-utils.metrics :refer [wrap-per-resource-metrics replace-outside-app
                                     replace-guid replace-mongoid replace-number]]
   [nokia.ring-utils.ignore-trailing-slash :refer [wrap-ignore-trailing-slash]]
   [metrics.ring.expose :refer [expose-metrics-as-json]]
   [metrics.ring.instrument :refer [instrument]]))

(def ^:dynamic *version* "none")
(def ^:private cf-info-room (env :service-campfire-default-info-room))
(def ^:private cf-error-room (env :service-campfire-default-error-room))
(def ^:private environments (env :service-environments))
(def ^:private can-post? (not (Boolean/valueOf (env :service-production))))

(defn set-version!
  [version]
  (alter-var-root #'*version* (fn [_] version)))

(defn- response
  [body]
  (merge
   {:status 200
    :headers {"Content-Type" "application/json"}}
   (if (nil? body)
     {}
     {:body body})))

(defn- list-envs
  []
  (response {:environments (split environments #",")}))

(defn- show-env-config
  [env]
  (->  (ring-response/response (-> (core/get-config env) (write-str)))
       (ring-response/content-type "application/json")))

(defn- apply-env-config
  [env]
  (core/apply-config env)
  (response  {:message (str "Succesfully applied the configuration for environment " env "."
                            "Please check the campfire room '" cf-info-room "' for a detailed report.")}))

(defn- list-apps
  [env]
  (response {:applications (core/app-names env)}))

(defn- show-app-config
  [env name]
  (->  (ring-response/response (-> (core/get-config env name) (write-str)))
       (ring-response/content-type "application/json")))

(defn- validate-config
  [body & [app-name]]
  (->  (ring-response/response (-> (core/validate-config body app-name) (write-str)))
       (ring-response/content-type "application/json")))

(defn- apply-app-config
  [env name]
  (core/apply-config env name)
  (response {:message (str "Succesfully applied the configuration for application " name "."
                           "Please check the campfire room '" cf-info-room "' for a detailed report.")}))

(defn- clean-app-config
  [env name]
  (core/clean-config env name)
  (response {:message (str "Succesfully cleaned the configuration for application " name)}))

(defn- apply-apps-config
  [env]
  (core/update-configs env)
  (response  {:message (str "Started applying the configuration for all applications in environment " env "."
                            "Please check the campfire room '" cf-error-room  "' for any error cases.")}))

(defn- create-app-config
  [name local]
  (let [env (if local "local" "")]
    (response (core/create-config env name))))

(defn- send-error
  [code message]
  (throw+ {:type :_
           :status code
           :message message}))

(defroutes applications-routes

  (GET "/"
       []
       (list-envs))

  (GET "/:env"
       [env]
       (show-env-config env))

  (GET "/:env/apply"
       [env]
       (apply-env-config env))

  (GET "/:env/apps"
       [env]
       (list-apps env))

  (GET "/:env/apps/:name"
       [env name]
       (show-app-config env name))

  (GET "/:env/apps/:name/apply"
       [env name]
       (apply-app-config env name))

  (GET "/:env/apps/:name/clean"
       [env name]
       (clean-app-config env name))

  (GET "/:env/apps/apply"
       [env]
       (apply-apps-config env)))

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

   (POST "/apps/:name"
         [name local]
         (if can-post?
           (create-app-config (lower-case name) local)
           (send-error 405 "POST requests on resources are not allowed in production environment.")))

   (POST "/apps/:name/validate"
         [:as {body :body} name]
         (if can-post?
           (validate-config (slurp body) name)
           (send-error 405 "POST requests on resources are not allowed in production environment.")))

   (POST "/envs/validate"
         [:as {body :body}]
         (if can-post?
           (validate-config (slurp body))
           (send-error 405 "POST requests on resources are not allowed in production environment.")))

   (context "/envs"
            [] applications-routes))

  (GET "/healthcheck"
       []
       (->  (ring-response/response "I am healthy. Thank you for asking.")
            (ring-response/content-type  "text/plain;charset=utf-8")))

  (route/not-found (error-response "Resource not found" 404)))

(defn format-shuppet-exceptions
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

(def app
  (-> routes
      (format-shuppet-exceptions)
      (instrument)
      (wrap-error-handling)
      (wrap-ignore-trailing-slash)
      (wrap-json-params)
      (wrap-keyword-params)
      (wrap-params)
      (wrap-json-response)
      (wrap-per-resource-metrics [replace-guid replace-mongoid replace-number (replace-outside-app "/1.x")])
      (expose-metrics-as-json)))
