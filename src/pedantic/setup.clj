(ns pedantic.setup
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :refer [info warn error]]
            [environ.core :refer [env]]
            [mixradio.instrumented-jetty :refer [run-jetty]]
            [ninjakoala.ttlr :as ttlr]
            [pedantic
             [environments :as environments]
             [identity :as id]
             [scheduler :as scheduler]
             [web :as web]]
            [radix.setup :as setup])
  (:gen-class))

(defonce server
  (atom nil))

(defn configure-server
  "Configures the server."
  [server]
  (doto server
    (.setStopAtShutdown true)
    (.setStopTimeout setup/shutdown-timeout)))

(defn start-server
  "Starts the server."
  []
  (run-jetty #'web/app {:port setup/service-port
                        :join? false
                        :stacktraces? (not setup/production?)
                        :auto-reload? (not setup/production?)
                        :configurator configure-server
                        :send-server-version false}))

(defn start
  []
  (setup/configure-logging)
  (setup/start-graphite-reporting {:graphite-prefix (str/join "." [(env :environment-name) (env :service-name) (env :box-id setup/hostname)])})
  (scheduler/start)
  (ttlr/init :cpu-count 1)
  (environments/init)
  (id/init)
  (reset! server (start-server)))

(defn stop
  []
  (when-let [s @server]
    (.stop s)
    (reset! server nil))
  (shutdown-agents))

(defn -main
  [& args]
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop))
  (start))
