(ns shuppet.acceptance
  (:require [shuppet.test-util :refer :all]
            [clj-http.client :as client]
            [clojure.java.io :refer [input-stream]]
            [midje.sweet :refer :all]
            [cheshire.core :as json]
            [clojure.data.zip.xml :as xml]
            [environ.core :refer [env]])
  (:import [java.util UUID]))

(defn url+ [& suffix] (apply str
                             (format (env :service-url) (env :service-port))
                             suffix))

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
                       (let [response (client/get (url+ "/ping")  {:throw-exceptions false})]
                         response => (contains {:status 200})))

                 (fact "Status returns all required elements"
                       (let [response (client/get (url+ "/status") {:throw-exceptions false})
                             body (read-body response)]
                         response => (contains {:status 200})))

                 (fact "envs can be listed"
                       (client/get (url+ "/envs")) => (contains {:status 200}))

                 (fact "env config can be read"
                       (client/get (url+ "/envs/local")) => (contains {:status 200}))

                 (fact "app config can be read"
                       (client/get (url+ "/envs/local/apps/localtest")) => (contains {:status 200}))

                 (fact "env config can be validated"
                       (let [res (client/post (url+ "/validate") {:body "(def $some-var \"value\") {}"})]
                         res => (contains {:status 200})
                         res => (contains {:body "{}"})))

                 (fact "app config can is correctly validated"
                       (let [config (input-stream "test/shuppet/resources/local/localtest.clj")
                             res (client/post (url+ "/validate") {:query-params {"env" "local"}
                                                                  :body config
                                                                  :throw-exceptions false})]
                         res => (contains {:status 200})))

                 (fact "invalid app config is rejected"
                       (let [res (client/post (url+ "/validate") {:query-params {"env" "local"}
                                                                  :body "{}"
                                                                  :throw-exceptions false})]
                         res => (contains {:status 400}))))
