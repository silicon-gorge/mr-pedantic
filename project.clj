(defproject shuppet "0.68-SNAPSHOT"
  :description "Shuppet service"
  :url "http://wikis.in.nokia.com/NokiaMusicArchitecture/Shuppet"

  :dependencies [[bouncer "0.3.0"]
                 [ch.qos.logback/logback-classic "1.1.2"]
                 [cheshire "5.3.1"]
                 [clj-campfire "2.2.0"]
                 [clj-http "0.9.1"]
                 [clj-time "0.8.0"]
                 [cluppet "0.0.9"]
                 [com.ovi.common.logging/logback-appender "0.0.47"]
                 [com.ovi.common.metrics/metrics-graphite "2.1.25"]
                 [com.yammer.metrics/metrics-logback "2.2.0"]
                 [compojure "1.1.8" :exclusions [javax.servlet/servlet-api]]
                 [environ "1.0.0"]
                 [io.clj/logging "0.8.1"]
                 [metrics-clojure "1.1.0"]
                 [metrics-clojure-ring "1.1.0"]
                 [nokia/instrumented-ring-jetty-adapter "0.1.10"]
                 [nokia/ring-utils "1.2.4"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.0"]
                 [org.slf4j/jcl-over-slf4j "1.7.7"]
                 [org.slf4j/jul-to-slf4j "1.7.7"]
                 [org.slf4j/log4j-over-slf4j "1.7.7"]
                 [org.slf4j/slf4j-api "1.7.7"]
                 [overtone/at-at "1.2.0"]
                 [ring-middleware-format "0.4.0"]
                 [tentacles.custom "0.2.8"]]

  :exclusions [commons-logging
               log4j]

  :profiles {:dev {:dependencies [[midje "1.6.3"]]
                   :plugins [[lein-rpm "0.0.5"]
                             [lein-midje "3.1.3"]
                             [jonase/kibit "0.0.8"]]}}

  :plugins [[codox "0.8.10"]
            [lein-environ "1.0.0"]
            [lein-marginalia "0.7.1"]
            [lein-release "1.0.73"]
            [lein-ring "0.8.11"]]

  :env {:environment-entertainment-graphite-host "carbon.brislabs.com"
        :environment-entertainment-graphite-port "2003"
        :environment-entertainment-onix-url "http://onix/1.x"
        :environment-music-errorlogging1java-baseurl "http://errorlogging.music.cq3.brislabs.com:8080/ErrorLogging/1.x"
        :environment-name "local"
        :github-auth-token "github-auth-token"
        :github-base-url "http://github/api/v3/"
        :github-organisation "shuppet"
        :service-aws-access-key-id-poke "poke-access-key-id"
        :service-aws-ddb-api-version "2012-08-10"
        :service-aws-ddb-url "https://dynamodb.eu-west-1.amazonaws.com"
        :service-aws-ec2-api-version "2013-10-01"
        :service-aws-ec2-url "https://ec2.eu-west-1.amazonaws.com"
        :service-aws-elb-api-version "2012-06-01"
        :service-aws-elb-url "https://elasticloadbalancing.eu-west-1.amazonaws.com"
        :service-aws-iam-api-version "2010-05-08"
        :service-aws-iam-url "https://iam.amazonaws.com"
        :service-aws-s3-url "https://s3-eu-west-1.amazonaws.com"
        :service-aws-secret-access-key-poke "poke-access-key-secret"
        :service-aws-sqs-api-version "2012-11-05"
        :service-campfire-api-token "campfire-api-token"
        :service-campfire-default-error-room "error-room"
        :service-campfire-default-info-room "info-room"
        :service-campfire-on false
        :service-campfire-sub-domain "campfire-sub-domain"
        :service-environments "poke"
        :service-graphite-enabled "DISABLED"
        :service-graphite-post-interval 1
        :service-graphite-post-unit "MINUTES"
        :service-local-app-names "localtest"
        :service-local-config-path "test/shuppet/resources"
        :service-logging-consolethreshold "off"
        :service-logging-filethreshold "info"
        :service-logging-level "info"
        :service-logging-path "/tmp"
        :service-logging-servicethreshold "off"
        :service-name "shuppet"
        :service-port "8080"
        :service-production false
        :service-scheduler-interval 120
        :service-scheduler-on false
        :service-sqs-autoscale-announcements-poke "http://autoscale/announcements"
        :service-sqs-enabled false
        :service-tooling-applications "ditto,exploud,numel,tyranitar,onix,garbodor,shuppet"}

  :lein-release {:release-tasks [:clean :uberjar :pom :rpm]
                 :clojars-url "clojars@clojars.brislabs.com:"}

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
        :copyright "Nokia 2013"
        :preinstall {:scriptFile "scripts/rpm/preinstall.sh"}
        :postinstall {:scriptFile "scripts/rpm/postinstall.sh"}
        :preremove {:scriptFile "scripts/rpm/preremove.sh"}
        :postremove {:scriptFile "scripts/rpm/postremove.sh"}
        :requires ["jdk >= 2000:1.6.0_31-fcs"]
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
