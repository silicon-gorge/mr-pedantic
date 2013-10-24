(ns shuppet.core
  (:require [clojure.string :refer [lower-case]]
            [clojure.data.json :refer [json-str]]
            [shuppet.util :refer :all]
            [shuppet.securitygroups :refer :all]
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
                                                          :throw-exceptions true})]
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

(defrecord GitConfiguration [^String file-name]
  Configuration
  (contents
    [this]
   ; (git/contents file-name) todo
    ))

(defrecord FileConfiguration [^String file-name]
  Configuration
  (contents
    [this]
    (slurp file-name)))


(defn execute-string
  [default-vars clojure-string]
  (let [ns (-> (java.util.UUID/randomUUID) (str) (symbol) (create-ns))]
    (try
      (binding [*ns* ns
                default-config default-vars]
        (clojure.core/refer 'clojure.core)
        (refer 'clojure.data.json)
        (refer 'shuppet.core)
        (refer 'shuppet.util)
        (refer 'shuppet.securitygroups)
        (eval (load-string clojure-string)))
      (finally (remove-ns (symbol (ns-name ns)))))))

(defn- get-default-config
  [app-name env print-json defaults]
  (merge defaults {:Application (lower-case app-name)
                   :Environment (keyword (lower-case env))
                   :PrintJson print-json}))

(defn configure
  ([app-name env print-json]
     (let [defaults (execute-string nil (contents (FileConfiguration.
                                                   (str (lower-case env) file-ext))))]
       (execute-string (get-default-config app-name
                                           env
                                           print-json
                                           defaults)
                       (contents (FileConfiguration.
                                  (str (lower-case app-name) file-ext)))))))

(defn update
  [env]
  (let [names (list-names (NamesFromService. onix-url))]
    (map #(configure % env false) names)))

(configure  "test" "dev" false)


;(update "dev")
