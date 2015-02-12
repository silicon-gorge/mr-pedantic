(defproject pedantic "0.74-SNAPSHOT"
  :description "Pedantic service"
  :license  "https://github.com/mixradio/mr-pedantic/blob/master/LICENSE"

  :dependencies [[bouncer "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.2"]
                 [cheshire "5.3.1"]
                 [clj-campfire "2.2.0"]
                 [clj-http "0.9.1"]
                 [clj-time "0.8.0"]
                 [cluppet "0.0.9"]
                 [compojure "1.2.1"]
                 [environ "1.0.0"]
                 [io.clj/logging "0.8.1"]
                 [mixradio/graphite-filter "1.0.0"]
                 [mixradio/instrumented-ring-jetty-adapter "1.0.4"]
                 [mixradio/radix "1.0.9"]
                 [net.logstash.logback/logstash-logback-encoder "3.4"]
                 [ninjakoala/tentacles.custom "0.2.9"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [overtone/at-at "1.2.0"]
                 [ring-middleware-format "0.4.0"]]

  :exclusions [commons-logging
               log4j
               org.clojure/clojure]

  :profiles {:dev {:dependencies [[midje "1.6.3"]]
                   :plugins [[lein-kibit "0.0.8"]
                             [lein-midje "3.1.3"]
                             [lein-rpm "0.0.5"]]}}

  :plugins [[codox "0.8.10"]
            [lein-cloverage "1.0.2"]
            [lein-environ "1.0.0"]
            [lein-marginalia "0.8.0"]
            [lein-release "1.0.5"]
            [lein-ring "0.8.13"]]

  :env {:aws-access-key-id-poke "poke-access-key-id"
        :aws-secret-access-key-poke "poke-access-key-secret"
        :aws-sqs-autoscale-announcements-poke "http://autoscale/announcements"
        :aws-sqs-enabled false
        :environment-name "dev"
        :environments "poke"
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
        :ignored-applications ""
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
        :threads 254
        :tooling-applications "tooling"}

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
