(ns shuppet.setup
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :refer [info warn error]]
            [environ.core :refer [env]]
            [nokia.adapter.instrumented-jetty :refer [run-jetty]]
            [shuppet
             [scheduler :as scheduler]
             [web :as web]])
  (:import (com.ovi.common.metrics.graphite GraphiteReporterFactory GraphiteName ReporterState)
           (com.ovi.common.metrics HostnameFactory)
           (java.util.logging LogManager)
           (java.util.concurrent TimeUnit)
           (org.slf4j.bridge SLF4JBridgeHandler))
  (:gen-class))

(defn- read-file-to-properties
  [file-name]
  (with-open [^java.io.Reader reader (io/reader file-name)]
    (let [props (java.util.Properties.)]
      (.load props reader)
      (into {} (for [[k v] props] [k v])))))

(defn- configure-logging
  []
  (.reset (LogManager/getLogManager))
  (SLF4JBridgeHandler/install))

(defn- start-graphite-reporting
  []
  (let [graphite-prefix (new GraphiteName (into-array Object
                                                      [(env :environment-name)
                                                       (env :service-name)
                                                       (HostnameFactory/getHostname)]))]
    (GraphiteReporterFactory/create
     (env :environment-entertainment-graphite-host)
     (Integer/valueOf (env :environment-entertainment-graphite-port))
     graphite-prefix
     (Integer/valueOf (env :service-graphite-post-interval))
     (TimeUnit/valueOf (env :service-graphite-post-unit))
     (ReporterState/valueOf (env :service-graphite-enabled)))))

(def ^:private version
  (delay (if-let [path (.getResource (ClassLoader/getSystemClassLoader) "META-INF/maven/shuppet/shuppet/pom.properties")]
           ((read-file-to-properties path) "version")
           "localhost")))

(defn setup
  []
  (web/set-version! @version)
  (configure-logging)
  (scheduler/start)
  (start-graphite-reporting))

(def ^:private server
  (atom nil))

(defn- start-server
  []
  (run-jetty #'web/app {:port (Integer. (env :service-port))
                        :join? false
                        :stacktraces? (not (Boolean/valueOf (env :service-production)))
                        :auto-reload? (not (Boolean/valueOf (env :service-production)))}))

(defn start
  []
  (do
    (setup)
    (reset! server (start-server))))

(defn stop
  []
  (when-let [server @server]
    (.stop server))
  (shutdown-agents))

(defn -main
  [& args]
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop))
  (start))
