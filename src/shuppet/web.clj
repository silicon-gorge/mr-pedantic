(ns shuppet.web
  (:require
   [shuppet
    [core :as core]
    [middleware :as middleware]
    [util :refer [rfc2616-time]]]
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
   [metrics.ring.instrument :refer [instrument]]
   [overtone.at-at :as at-at]))

(def ^:dynamic *version* "none")
(def ^:private cf-info-room (env :service-campfire-default-info-room))
(def ^:private cf-error-room (env :service-campfire-default-error-room))
(def ^:private environments (env :service-environments))
(def ^:private default-interval (Integer/parseInt (env :service-default-update-interval)))
(def ^:private schedule-pools (atom {}))

(defn- create-pool
  [env]
  {env (at-at/mk-pool)})

(defn- get-pool
  [env]
  (let [env (keyword env)
        pool (env @schedule-pools)]
    (if pool
      pool
      (env (swap! schedule-pools merge (create-pool env))))))

(defn set-version!
  [version]
  (alter-var-root #'*version* (fn [_] version)))

(defn- response
  ([body status]
     (merge
      {:status status
       :headers {"Content-Type" "application/json"}}
      (if (nil? body)
        {}
        {:body body})))
  ([body]
     (response body 200)))

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
  [body name]
  (->  (ring-response/response (-> (core/validate-config body name) (write-str)))
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

(defn- configure-apps
  [env]
  (core/apply-config env)
  (core/update-configs env))

(defn- apply-apps-config
  [env]
  (configure-apps env)
  (response  {:message (str "Started applying the configuration for all applications in environment " env "."
                            "Please check the campfire room '" cf-error-room  "' for any error cases.")}))

(defn- create-app-config
  [name local master-only]
  (let [env (if local "local" "")
        resp (core/create-config env name master-only)]
    (response (dissoc resp :status) (:status resp))))

(defn- send-error
  [code message]
  (throw+ {:type :_
           :status code
           :message message}))

(defn- schedule
  [env action interval]
  (at-at/stop-and-reset-pool! (get-pool env) :strategy :kill)
  (if (= action "stop")
    (response {:message "Succefully stopped the shuppet scheduler"})
    (do
      (let [interval (if interval (Integer/parseInt interval) default-interval)]
        (at-at/every (* interval 60 1000) #(configure-apps env) (get-pool env) :desc (rfc2616-time))
         (response {:message (str "Succesfully started the shuppet scheduler. The scheduler will run in every " interval " minutes.") })))))

(defn- get-schedule
  [env]
  (if-let [job (first (at-at/scheduled-jobs (get-pool env)))]
    (response {:created (:desc job)
               :interval (str (/ (:ms-period job) (* 60 1000)) " minutes")})
    (response {:message "No jobs are currently scheduled"} 404)))

(def ^:private resources
  {:GET
   (array-map
    "/healthcheck" "Healthcheck"
    "/1.x/icon" "Icon"
    "/1.x/ping" "Pong"
    "/1.x/status" "Status"
    "/1.x/envs" "All available environments"
    "/1.x/envs/:env-name" "Read and evaluate the environment configuration :env-name.clj from GIT repository :env-name, return the configuration in JSON"
    "/1.x/envs/:env-name/apply" "Apply the environment configuration"
    "/1.x/envs/:env-name/apps" "All available applications for the given environment"
    "/1.x/envs/:env-name/schedule" "Shows the current shuppet schedule, if any"
    "/1.x/envs/:env-name/apps/apply" "Apply configuration for all applications listed in Onix"
    "/1.x/envs/:env-name/apps/:app-name" "Read the application configuration :app-name.clj from GIT repository :app-name and evaluate it with the environment configuration, return the configuration in JSON. Master branch is used for all environments except for production where prod branch is used instead."
    "/1.x/envs/:env-name/apps/:app-name/apply" "Apply the application configuration for the given environment")
   :POST
   (array-map
    "/1.x/apps/:app-name" "Create an application configuration, QS Parameter masteronly=true, just creates the master branch"
    "/1.x/validate/:name" "Validate the configuration passed in the body"
    )})

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

  (GET "/:env/apps/apply"
       [env]
       (apply-apps-config env))

  (GET "/:env/schedule"
        [env]
        (get-schedule env))

  (POST "/:env/schedule"
        [env action interval]
        (schedule env action interval))

  (GET "/:env/apps/:name"
       [env name]
       (show-app-config env name))

  (GET "/:env/apps/:name/apply"
       [env name]
       (apply-app-config env name))

  (GET "/:env/apps/:name/clean"
       [env name]
       (clean-app-config env name))
)

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
         [name local masteronly]
         (create-app-config (lower-case name) local masteronly))

   (POST "/validate/:name"
         [:as {body :body} name]
         (validate-config (slurp body) name))

   (context "/envs"
            [] applications-routes))

  (GET "/healthcheck"
       []
       (->  (ring-response/response "I am healthy. Thank you for asking.")
            (ring-response/content-type  "text/plain;charset=utf-8")))

  (GET "/resources"
       []
       (->  (ring-response/response (-> resources (write-str)))
            (ring-response/content-type  "application/json")))

  (route/not-found (error-response "Resource not found" 404)))

(def app
  (-> routes
      (middleware/wrap-check-env)
      (middleware/wrap-shuppet-error)
      (instrument)
      (wrap-error-handling)
      (wrap-ignore-trailing-slash)
      (wrap-json-params)
      (wrap-keyword-params)
      (wrap-params)
      (wrap-json-response)
      (wrap-per-resource-metrics [replace-guid replace-mongoid replace-number (replace-outside-app "/1.x")])
      (expose-metrics-as-json)))
