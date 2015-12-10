(defproject pedantic "0.86-SNAPSHOT"
  :description "Pedantic service"
  :license  "https://github.com/mixradio/mr-pedantic/blob/master/LICENSE"

  :dependencies [[amazonica "0.3.38"]
                 [bouncer "0.3.3"]
                 [ch.qos.logback/logback-classic "1.1.3"]
                 [cheshire "5.5.0"]
                 [clj-http "2.0.0"]
                 [clj-time "0.11.0"]
                 [clojail "1.0.6"]
                 [com.ninjakoala/aws-instance-metadata "1.0.0"]
                 [com.ninjakoala/ttlr "1.0.1"]
                 [compojure "1.4.0"]
                 [environ "1.0.1"]
                 [io.clj/logging "0.8.1"]
                 [mixradio/graphite-filter "1.0.0"]
                 [mixradio/instrumented-ring-jetty-adapter "1.0.4"]
                 [mixradio/radix "1.0.9"]
                 [net.logstash.logback/logstash-logback-encoder "4.5.1"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/core.memoize "0.5.7"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.tobereplaced/lettercase "1.0.0"]
                 [overtone/at-at "1.2.0"]
                 [ring-middleware-format "0.6.0"]
                 [tentacles "0.4.0"]]

  :exclusions [commons-logging
               log4j
               org.clojure/clojure]

  :profiles {:dev {:dependencies [[midje "1.8.1"]]
                   :plugins [[lein-kibit "0.0.8"]
                             [lein-midje "3.1.3"]
                             [lein-rpm "0.0.5"]]}}

  :plugins [[codox "0.8.10"]
            [lein-cloverage "1.0.2"]
            [lein-environ "1.0.0"]
            [lein-marginalia "0.8.0"]
            [lein-release "1.0.5"]
            [lein-ring "0.8.13"]]

  :env {:aws-role-name "pedantic"
        :aws-sqs-enabled false
        :backoff-enabled false
        :backoff-maximum-millis 10000
        :credentials-ttl-enabled false
        :credentials-ttl-minutes 25
        :default-access-log-enabled false
        :default-connection-draining-enabled true
        :default-connection-draining-timeout-seconds 300
        :default-cross-zone-enabled true
        :default-idle-timeout-seconds 60
        :environment-name "dev"
        :github-auth-token "github-auth-token"
        :github-base-url "http://github/api/v3/"
        :github-organisation "pedantic"
        :graphite-enabled false
        :graphite-host "carbon"
        :graphite-port 2003
        :graphite-post-interval-seconds 60
        :hubot-error-room "pedantic-error"
        :hubot-info-room "pedantic-info"
        :hubot-on false
        :hubot-url "http://hubot"
        :lister-baseurl "http://lister"
        :logging-consolethreshold "off"
        :logging-filethreshold "info"
        :logging-level "info"
        :logging-path "/tmp"
        :logging-stashthreshold "warn"
        :production false
        :requestlog-enabled false
        :requestlog-retainhours 24
        :scheduler-interval 120
        :scheduler-on false
        :service-jvmargs ""
        :service-name "pedantic"
        :service-port 8080
        :shutdown-timeout-millis 5000
        :start-timeout-seconds 120
        :threads 254}

  :lein-release {:deploy-via :shell
                 :shell ["lein" "do" "clean," "uberjar," "pom," "rpm"]}

  :jvm-opts ["-Djava.security.policy=./.java.policy"]

  :ring {:handler pedantic.web/app
         :main pedantic.setup
         :port ~(Integer/valueOf (get (System/getenv) "SERVICE_PORT" "8080"))
         :init pedantic.setup/setup
         :browser-uri "/healthcheck"
         :nrepl {:start? true}}

  :uberjar-name "pedantic.jar"

  :eastwood {:namespaces [:source-paths]}

  :rpm {:name "pedantic"
        :summary "RPM for Pedantic service"
        :copyright "MixRadio 2014"
        :preinstall {:scriptFile "scripts/rpm/preinstall.sh"}
        :postinstall {:scriptFile "scripts/rpm/postinstall.sh"}
        :preremove {:scriptFile "scripts/rpm/preremove.sh"}
        :postremove {:scriptFile "scripts/rpm/postremove.sh"}
        :requires ["jdk >= 2000:1.7.0_55-fcs"]
        :mappings [{:directory "/usr/local/pedantic"
                    :filemode "444"
                    :username "pedantic"
                    :groupname "pedantic"
                    :sources {:source [{:location "target/pedantic.jar"}]}}
                   {:directory "/usr/local/pedantic"
                    :filemode "444"
                    :username "pedantic"
                    :groupname "pedantic"
                    :sources {:source [{:location ".java.policy"}]}}
                   {:directory "/usr/local/pedantic/bin"
                    :filemode "744"
                    :username "pedantic"
                    :groupname "pedantic"
                    :sources {:source [{:location "scripts/bin"}]}}
                   {:directory "/etc/rc.d/init.d"
                    :filemode "755"
                    :sources {:source [{:location "scripts/service/pedantic"
                                        :destination "pedantic"}]}}]}

  :main pedantic.setup)
