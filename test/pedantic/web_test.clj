(ns pedantic.web-test
  (:require [cheshire.core :as json]
            [midje.sweet :refer :all]
            [pedantic
             [core :as core]
             [environments :as environments]
             [web :refer :all]]
            [ring.util.io :refer [string-input-stream]]
            [slingshot.support :as s]))

(defn- json-body
  [raw-body]
  {:body (string-input-stream (json/encode raw-body))
   :headers {"content-type" "application/json"}})

(defn- streamed-body?
  [{:keys [body]}]
  (instance? java.io.InputStream body))

(defn- json-response?
  [{:keys [headers]}]
  (when-let [content-type (get headers "Content-Type")]
    (re-find #"^application/(vnd.+)?json" content-type)))

(defn request
  "Creates a compojure request map and applies it to our application.
  Accepts method, resource and optionally an extended map"
  [method resource & [{:keys [body headers params]
                       :or {:body nil
                            :headers {}
                            :params {}}}]]
  (let [{:keys [body headers] :as response} (app {:body body
                                                  :headers headers
                                                  :params params
                                                  :request-method method
                                                  :uri resource})]
    (cond-> response
            (streamed-body? response) (update-in [:body] slurp)
            (json-response? response) (update-in [:body] (fn [b] (json/parse-string b true))))))

(defn slingshot-exception
  [exception-map]
  (s/get-throwable (s/make-context exception-map (str "throw+: " map) (Exception. "Busted") (s/stack-trace))))

(fact "that ping pongs with a 200 response code"
      (request :get "/ping") => (contains {:body "pong"
                                           :status 200}))

(fact "that the healthcheck comes back with a 200 response code"
      (request :get "/healthcheck") => (contains {:status 200}))

(fact "that we can list environments"
      (request :get "/envs") => (contains {:body {:environments ["env1" "env2"]}
                                           :status 200})
      (provided
       (environments/environment-names) => #{"env1" "env2"}))

(fact "that we can read environment configuration"
      (request :get "/envs/env1") => (contains {:body {:some "config"}
                                                :status 200})
      (provided
       (environments/environment-names) => #{"env1"}
       (core/get-config "env1") => {:some "config"}))

(fact "that we get a 404 for an unknown environment"
      (request :get "/envs/unknown") => (contains {:status 404})
      (provided
       (environments/environment-names) => #{}))

(fact "that we can read application configuration"
      (request :get "/envs/env1/apps/application") => (contains {:body {:some "config"}
                                                                 :status 200})
      (provided
       (environments/environment-names) => #{"env"}
       (core/get-config "env1" "application") => {:some "config"}))

(fact "that we can validate environment configuration"
      (request :post "/validate" (merge (json-body {:some "config"})
                                        {:params {"env" "env1"}}))
      => (contains {:body {:the "result"}
                    :status 200})
      (provided
       (core/validate-config "env1" nil "{\"some\":\"config\"}") => {:the "result"}))

(fact "that we can validate application configuration"
      (request :post "/validate" (merge (json-body {:some "config"})
                                        {:params {"env" "env1"
                                                  "app-name" "application"}}))
      => (contains {:body {:the "result"}
                    :status 200})
      (provided
       (core/validate-config "env1" "application" "{\"some\":\"config\"}") => {:the "result"}))

(fact "that an invalid application configuration is rejected"
      (request :post "/validate" (merge (json-body {:some "config"})
                                        {:params {"env" "env1"
                                                  "app-name" "application"}}))
      => (contains {:status 400})
      (provided
       (core/validate-config "env1" "application" "{\"some\":\"config\"}") =throws=> (slingshot-exception {:type :pedantic.validator/validator
                                                                                                           :details {:the "result"}})))

(fact "that we can apply an application configuration"
      (request :get "/envs/env1/apps/application/apply")
      => (contains {:body {:report "something"}
                    :status 200})
      (provided
       (environments/environment-names) => #{"env1"}
       (core/apply-config "env1" "application") => {:report "something"}))
