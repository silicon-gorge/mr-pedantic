(ns shuppet.web-test
  (:require [cheshire.core :as json]
            [midje.sweet :refer :all]
            [ring.util.io :refer [string-input-stream]]
            [shuppet
             [core :as core]
             [web :refer :all]]
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
  (s/get-throwable (s/make-context exception-map (str "throw+: " map) (s/stack-trace) {})))

(fact "that ping pongs with a 200 response code"
      (request :get "/ping") => (contains {:body "pong"
                                           :status 200}))

(fact "that the healthcheck comes back with a 200 response code"
      (request :get "/healthcheck") => (contains {:status 200}))

(fact "that we can list environments"
      (request :get "/1.x/envs") => (contains {:body {:environments ["poke"]}
                                               :status 200}))

(fact "that we can read environment configuration"
      (request :get "/1.x/envs/poke") => (contains {:body {:some "config"}
                                                    :status 200})
      (provided
       (core/get-config "poke") => {:some "config"}))

(fact "that we get a 404 for an unknown environment"
      (request :get "/1.x/envs/unknown") => (contains {:status 404}))

(fact "that we can read application configuration"
      (request :get "/1.x/envs/poke/apps/application") => (contains {:body {:some "config"}
                                                                     :status 200})
      (provided
       (core/get-config "poke" "application") => {:some "config"}))

(fact "that we can validate environment configuration"
      (request :post "/1.x/validate" (merge (json-body {:some "config"})
                                            {:params {"env" "poke"}}))
      => (contains {:body {:the "result"}
                    :status 200})
      (provided
       (core/validate-config "poke" nil "{\"some\":\"config\"}") => {:the "result"}))

(fact "that we can validate application configuration"
      (request :post "/1.x/validate" (merge (json-body {:some "config"})
                                            {:params {"env" "poke"
                                                      "app-name" "application"}}))
      => (contains {:body {:the "result"}
                    :status 200})
      (provided
       (core/validate-config "poke" "application" "{\"some\":\"config\"}") => {:the "result"}))

(fact "that an invalid application configuration is rejected"
      (request :post "/1.x/validate" (merge (json-body {:some "config"})
                                            {:params {"env" "poke"
                                                      "app-name" "application"}}))
      => (contains {:status 400})
      (provided
       (core/validate-config "poke" "application" "{\"some\":\"config\"}") =throws=> (slingshot-exception {:type :shuppet.validator/validator
                                                                                                           :details {:the "result"}})))

(fact "that we can apply an application configuration"
      (request :get "/1.x/envs/poke/apps/application/apply")
      => (contains {:body {:report "something"}
                    :status 200})
      (provided
       (core/apply-config "poke" "application") => {:report "something"}))
