(ns shuppet.core
  (:require [clojure.string :refer [lower-case]]
            [clojure.data.json :refer [json-str]]
            [shuppet.util :refer :all]
            [shuppet
             [git :as git]
             [securitygroups :refer [ensure-sg]]
             [elb :refer [ensure-elb]]]
            [clj-http.client :as client]
            [environ.core :refer [env]]))

(def ^:const onix-url (or (env :environment-onix-url) "http://onix.brislabs.com:8080/1.x")) ;todo check
(def ^:const file-ext ".clj")

(declare ^:dynamic default-config)

(defprotocol ApplicationNames
  (list-names
    [apps]
    "Gets a list of the application names"))

(defrecord NamesFromService [^String url]
  ApplicationNames
  (list-names
    [this]
    (let [response (client/get (str url "/applications") {:as :json
                                                          :throw-exceptions false})]
      (if (= 200 (:status response))
        (get-in response [:body :applications])
        (prn "Cant get a proper response from onix application " response)))))

(defrecord NamesFromFile [^String path]
  ApplicationNames
  (list-names
    [this]
    (prn "Get service names from local file system here")))


(defprotocol Configuration
  (contents
    [this]
    "Gets the configuration file contents for evaluation"))

(defrecord FromGit [^String name]
  Configuration
  (contents
    [this]
    (git/get-data (lower-case name))))

(defrecord FromFile [^String file-name]
  Configuration
  (contents
    [this]
    (slurp file-name)))


(defn- execute-string
  [clojure-string name environment]
  (let [ns (-> (java.util.UUID/randomUUID) (str) (symbol) (create-ns))]
    (binding [*ns* ns]
      (clojure.core/refer 'clojure.core)
      (refer 'clojure.data.json)
      (refer 'shuppet.core)
      (refer 'shuppet.util)
      (refer 'shuppet.securitygroups)
      (def app-name name)
      (def environment (keyword environment))
      (let [config (load-string clojure-string)]
        (remove-ns (symbol (ns-name ns)))
        config))))

(defn configure
  ([env app-name print-json]
     (let [defaults (contents (FromGit.
                               env))
           config (contents (FromGit.
                             app-name))
           config (execute-string (str defaults "\n" config) app-name env)
           ]
       (if print-json
         (json-str config)
         (do
           (ensure-sg (:sg config))
           (ensure-elb (:elb config)))))))

(defn update
  [env]
  (let [names (list-names (NamesFromService. onix-url))]
    (map #(configure % env false) names)))

;(configure  "shuppet-test" "dev" true)
;(update "dev")
