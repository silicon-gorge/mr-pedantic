(defproject shuppet "0.69"
  :description "Shuppet service"
  :url "http://wikis.in.nokia.com/NokiaMusicArchitecture/Shuppet"

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
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [overtone/at-at "1.2.0"]
                 [ring-middleware-format "0.4.0"]
                 [tentacles.custom "0.2.8"]]

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
        :campfire-api-token "campfire-api-token"
        :campfire-default-error-room "error-room"
        :campfire-default-info-room "info-room"
        :campfire-on false
        :campfire-sub-domain "campfire-sub-domain"
        :environment-name "dev"
        :environments "poke"
        :github-auth-token "github-auth-token"
        :github-base-url "http://github/api/v3/"
        :github-organisation "shuppet"
        :graphite-enabled false
        :graphite-host "carbon.brislabs.com"
        :graphite-port 2003
        :graphite-post-interval-seconds 60
        :ignored-applications ""
        :logging-consolethreshold "off"
        :logging-filethreshold "info"
        :logging-level "info"
        :logging-path "/tmp"
        :logging-stashthreshold "warn"
        :onix-baseurl "http://onix/1.x"
        :production false
        :requestlog-enabled false
        :requestlog-retainhours 24
        :scheduler-interval 120
        :scheduler-on false
        :service-jvmargs ""
        :service-name "shuppet"
        :service-port 8080
        :shutdown-timeout-millis 5000
        :start-timeout-seconds 120
        :threads 254
        :tooling-applications "ditto,exploud,numel,tyranitar,onix,garbodor,shuppet"}

  :lein-release {:deploy-via :shell
                 :shell ["lein" "do" "clean," "uberjar," "pom," "rpm"]}

  :jvm-opts ["-Djava.security.policy=./.java.policy"]

  :ring {:handler shuppet.web/app
         :main shuppet.setup
         :port ~(Integer/valueOf (get (System/getenv) "SERVICE_PORT" "8080"))
         :init shuppet.setup/setup
         :browser-uri "/healthcheck"
         :nrepl {:start? true}}

  :repositories {"internal-clojars"
                 "http://clojars.brislabs.com/repo"
                 "rm.brislabs.com"
                 "http://rm.brislabs.com/nexus/content/groups/all-releases"}

  :uberjar-name "shuppet.jar"

  :eastwood {:namespaces [:source-paths]}

  :rpm {:name "shuppet"
        :summary "RPM for Shuppet service"
        :copyright "MixRadio 2014"
        :preinstall {:scriptFile "scripts/rpm/preinstall.sh"}
        :postinstall {:scriptFile "scripts/rpm/postinstall.sh"}
        :preremove {:scriptFile "scripts/rpm/preremove.sh"}
        :postremove {:scriptFile "scripts/rpm/postremove.sh"}
        :requires ["jdk >= 2000:1.7.0_55-fcs"]
        :mappings [{:directory "/usr/local/shuppet"
                    :filemode "444"
                    :username "shuppet"
                    :groupname "shuppet"
                    :sources {:source [{:location "target/shuppet.jar"}]}}
                   {:directory "/usr/local/shuppet"
                    :filemode "444"
                    :username "shuppet"
                    :groupname "shuppet"
                    :sources {:source [{:location ".java.policy"}]}}
                   {:directory "/usr/local/shuppet/bin"
                    :filemode "744"
                    :username "shuppet"
                    :groupname "shuppet"
                    :sources {:source [{:location "scripts/bin"}]}}
                   {:directory "/etc/rc.d/init.d"
                    :filemode "755"
                    :sources {:source [{:location "scripts/service/shuppet"
                                        :destination "shuppet"}]}}]}

  :main shuppet.setup)
