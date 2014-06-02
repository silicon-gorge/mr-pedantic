(ns shuppet.acceptance
  (:require [shuppet.test-util :refer :all]
            [clj-http.client :as client]
            [clojure.java.io :refer [input-stream]]
            [midje.sweet :refer :all]
            [cheshire.core :as json]
            [clojure.data.zip.xml :as xml]
            [environ.core :refer [env]])
  (:import [java.util UUID]))

(defn content-type
  [response]
  (if-let [ct ((:headers response) "content-type")]
    (first (clojure.string/split ct #";"))
    :none))

(defmulti read-body content-type)

(defmethod read-body "application/xml" [http-response]
  (-> http-response
      :body
      .getBytes
      java.io.ByteArrayInputStream.
      clojure.xml/parse clojure.zip/xml-zip))

(defmethod read-body "application/json" [http-response]
  (json/parse-string (:body http-response) true))


(lazy-fact-group :acceptance
                 (fact "Ping resource returns 200 HTTP response"
                       (let [response (client/get (url+ "/ping") {:throw-exceptions false})]
                         response => (contains {:status 200})))

                 (fact "Status returns all required elements"
                       (let [response (client/get (url+ "/status") {:throw-exceptions false})
                             body (read-body response)]
                         response => (contains {:status 200})))

                 (fact "envs can be listed"
                       (let [r (http-get "/envs")]
                         r => (contains {:status 200})
                         (:body r) => {:environments ["local"]}))

                 (fact "env config can be read"
                       (:body (http-get "/envs/local"))
                       => {:DefaultRolePolicies [{:PolicyName "default"}]})

                 (fact "app config can be read"
                       (:body (http-get "/envs/local/apps/localtest"))
                       => {:Role {:Policies [{:PolicyName "default"}]
                                  :RoleName "localtest"}})

                 (fact "env config can be validated"
                       (let [config (input-stream "test/shuppet/resources/local/local.clj")
                             res (http-post "/validate" {:query-params {"env" "local"}
                                                         :body config})]
                         res => (contains {:status 200})
                         res => (contains {:body {:DefaultRolePolicies [{:PolicyName "default"}]}})))

                 (fact "app config is correctly validated"
                       (let [config (input-stream "test/shuppet/resources/local/localtest.clj")
                             res (http-post "/validate" {:query-params {"env" "local"
                                                                        "app-name" "localtest"}
                                                         :body config})]
                         res => (contains {:status 200})
                         res => (contains {:body {:Role {:RoleName "localtest"}}})))

                 (fact "invalid app config is rejected"
                       (let [res (http-post "/validate" {:query-params {"env" "local"}
                                                         :body "{}"})]
                         res => (contains {:status 400}))))
