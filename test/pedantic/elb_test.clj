(ns pedantic.elb-test
  (:require [amazonica.aws
             [ec2 :as ec2]
             [elasticloadbalancing :as elb]]
            [cheshire.core :as json]
            [midje.sweet :refer :all]
            [pedantic
             [elb :refer :all]
             [securitygroups :as sg]
             [subnets :as subnets]])
  (:import [com.amazonaws.services.elasticloadbalancing.model LoadBalancerNotFoundException]))

(fact "that we convert local configuration correctly"
      (convert-local-config {:CrossZone true
                             :HealthCheck {:HealthyThreshold 2
                                           :Interval 6
                                           :Target "HTTP:8080/ping"
                                           :Timeout 5
                                           :UnhealthyThreshold 4}
                             :Listeners [{:InstancePort 8080
                                          :InstanceProtocol "http"
                                          :LoadBalancerPort 80
                                          :Protocol "http"
                                          :SSLCertificateId "cert-id"}]
                             :LoadBalancerName "loadbalancer"
                             :Scheme "internal"
                             :SecurityGroups ["sg-1" "sg-2"]
                             :Subnets ["subnet-id-1" "subnet-id-2"]
                             :VpcId "vpc-id"})
      => {:cross-zone true
          :health-check {:healthy-threshold 2
                         :interval 6
                         :target "HTTP:8080/ping"
                         :timeout 5
                         :unhealthy-threshold 4}
          :listener-descriptions [{:listener {:instance-port 8080
                                              :instance-protocol "HTTP"
                                              :load-balancer-port 80
                                              :protocol "HTTP"
                                              :ssl-certificate-id "cert-id"}}]
          :load-balancer-name "loadbalancer"
          :scheme "internal"
          :security-groups ["sg-1" "sg-2"]
          :subnets ["subnet-id-1" "subnet-id-2"]})

(fact "that we handle already converted local configuration"
      (convert-local-config {:cross-zone true
                             :health-check {:healthy-threshold 2
                                            :interval 6
                                            :target "HTTP:8080/ping"
                                            :timeout 5
                                            :unhealthy-threshold 4}
                             :listener-descriptions [{:listener {:instance-port 8080
                                                                 :instance-protocol "HTTP"
                                                                 :load-balancer-port 80
                                                                 :protocol "HTTP"
                                                                 :ssl-certificate-id "cert-id"}}]
                             :load-balancer-name "loadbalancer"
                             :scheme "internal"
                             :security-groups ["sg-1" "sg-2"]
                             :subnets ["subnet-id-1" "subnet-id-2"]})
      => {:cross-zone true
          :health-check {:healthy-threshold 2
                         :interval 6
                         :target "HTTP:8080/ping"
                         :timeout 5
                         :unhealthy-threshold 4}
          :listener-descriptions [{:listener {:instance-port 8080
                                              :instance-protocol "HTTP"
                                              :load-balancer-port 80
                                              :protocol "HTTP"
                                              :ssl-certificate-id "cert-id"}}]
          :load-balancer-name "loadbalancer"
          :scheme "internal"
          :security-groups ["sg-1" "sg-2"]
          :subnets ["subnet-id-1" "subnet-id-2"]})

(fact "that working out security IDs by name works"
      (security-group-names-to-ids ["sg-name1" "sg-name2"] "vpc-id") => ["sg-id1" "sg-id2"]
      (provided
       (sg/security-group-by-name "sg-name1" "vpc-id") => {:group-id "sg-id1"}
       (sg/security-group-by-name "sg-name2" "vpc-id") => {:group-id "sg-id2"}))

(fact "that working out availability zones from subnets works"
      (subnets-to-availability-zones ["subnet-id-1" "subnet-id-2"]) => ["zone-a" "zone-b"]
      (provided
       (subnets/availability-zone "subnet-id-1") => "zone-a"
       (subnets/availability-zone "subnet-id-2") => "zone-b"))

(fact "that creating a healthcheck does what we need"
      (let [config {:health-check ..health-check..
                    :load-balancer-name ..load-balancer-name..}]
        (create-healthcheck config) => config
        (provided
         (elb/configure-health-check anything
                                     :health-check ..health-check..
                                     :load-balancer-name ..load-balancer-name..)
         => ..configure-result..)))

(fact "that creating an ELB does what we need"
      (let [config {:availability-zones [..av-1.. ..av-2..]
                    :listener-descriptions [{:listener ..l-1..}
                                            {:listener ..l-2..}]
                    :load-balancer-name ..load-balancer-name..
                    :scheme ..scheme..
                    :security-groups [..sg-1.. ..sg-2..]
                    :subnets [..sn-1.. ..sn-2..]}]
        (create-elb config) => config
        (provided
         (elb/create-load-balancer anything
                                   :availability-zones [..av-1.. ..av-2..]
                                   :listeners [..l-1.. ..l-2..]
                                   :load-balancer-name ..load-balancer-name..
                                   :scheme ..scheme..
                                   :security-groups [..sg-1.. ..sg-2..]
                                   :subnets [..sn-1.. ..sn-2..]
                                   :tags [{:key "ManagedBy"
                                           :value "Pedantic"}])
         => ..create-result..)))

(fact "that finding an ELB by name works"
      (find-elb "elb") => {:listener-descriptions [{:listener ..listener..}]}
      (provided
       (elb/describe-load-balancers anything
                                    :load-balancer-names ["elb"]) => {:load-balancer-descriptions [{:listener-descriptions [{:listener ..listener..
                                                                                                                             :policy-names ..policies..}]}]}))

(fact "that finding an unknown ELB by name returns nil"
      (find-elb "elb") => nil
      (provided
       (elb/describe-load-balancers anything :load-balancer-names ["elb"]) =throws=> (LoadBalancerNotFoundException. "Busted")))

(fact "that getting load balancer attributes works for an ELB which exists"
      (get-load-balancer-attributes "elb") => ..attributes..
      (provided
       (elb/describe-load-balancer-attributes anything :load-balancer-name "elb") => {:load-balancer-attributes ..attributes..}))

(fact "that getting load balancer attributes for an ELB which doesn't exist gives nil"
      (get-load-balancer-attributes "elb") => nil
      (provided
       (elb/describe-load-balancer-attributes anything :load-balancer-name "elb") =throws=> (LoadBalancerNotFoundException. "Busted")))

(fact "that getting load balancer tags works for an ELB which exists"
      (get-load-balancer-tags "elb") => ..tags..
      (provided
       (elb/describe-tags anything :load-balancer-names ["elb"]) => {:tag-descriptions [{:tags ..tags..}]}))

(fact "that getting load balancer tags works for an ELB which doesn't exist gives nil"
      (get-load-balancer-tags "elb") => nil
      (provided
       (elb/describe-tags anything :load-balancer-names ["elb"]) =throws=> (LoadBalancerNotFoundException. "Busted")))

(def config
  {:availability-zones ["zone-a" "zone-b"]
   :cross-zone true
   :health-check {:healthy-threshold 2
                  :interval 6
                  :target "HTTP:8080/ping"
                  :timeout 5
                  :unhealthy-threshold 4}
   :load-balancer-name "elb-name"
   :listener-descriptions [{:listener {:instance-port 8080
                                       :instance-protocol "http"
                                       :load-balancer-port 80
                                       :protocol "http"}}
                           {:listener {:instance-port 8080
                                       :instance-protocol "http"
                                       :load-balancer-port 8080
                                       :protocol "http"}}]
   :scheme "internal"
   :security-groups ["sg-id-1" "sg-id-2"]
   :subnets ["subnet-id-1" "subnet-id-2"]})

(fact "that healthcheck is not created when configurations are identical"
      (ensure-healthcheck {:local config :remote config}) => {:local config :remote config}
      (provided
       (create-healthcheck anything) => nil :times 0))

(fact "that healthcheck is created when configurations are different"
      (let [new-config (assoc-in config [:health-check :target] "different")]
        (ensure-healthcheck {:local new-config :remote config}) => {:local new-config :remote config}
        (provided
         (create-healthcheck new-config) => ..create-result..)))

(fact "that nothing happens when listeners are identical"
      (ensure-listeners {:local config :remote config}) => {:local config :remote config}
      (provided
       (elb/delete-load-balancer-listeners anything
                                           :load-balancer-name anything
                                           :load-balancer-ports anything)
       => nil :times 0
       (elb/create-load-balancer-listeners anything
                                           :listeners anything
                                           :load-balancer-name anything)
       => nil :times 0))

(fact "that a missing listener is added and an extra one removed"
      (let [new-config (assoc config :listener-descriptions [{:listener {:instance-port 8080
                                                                         :instance-protocol "http"
                                                                         :load-balancer-port 80
                                                                         :protocol "http"}}
                                                             {:listener {:instance-port 3333
                                                                         :instance-protocol "http"
                                                                         :load-balancer-port 3333
                                                                         :protocol "http"}}])]
        (ensure-listeners {:local new-config :remote config})=> {:local new-config :remote config}
        (provided
         (elb/delete-load-balancer-listeners anything
                                             :load-balancer-name "elb-name"
                                             :load-balancer-ports [8080])
         => ..delete-result..
         (elb/create-load-balancer-listeners anything
                                             :listeners [{:instance-port 3333
                                                          :instance-protocol "http"
                                                          :load-balancer-port 3333
                                                          :protocol "http"}]
                                             :load-balancer-name "elb-name")
         => ..create-result..)))

(fact "that listeners protocol is case insensitive"
      (let [new-config (assoc config :Listeners [{:LoadBalancerPort 8080
                                                  :InstancePort 8080
                                                  :Protocol "hTTp"
                                                  :InstanceProtocol "httP"}
                                                 {:LoadBalancerPort 80
                                                  :InstancePort 8080
                                                  :SSLCertificateId "arn:aws:iam::123:server-certificate/Sonos.crt"
                                                  :Protocol "http"
                                                  :InstanceProtocol "http"}])]
        (ensure-listeners {:local new-config :remote config})=> {:local new-config :remote config}
        (provided
         (elb/delete-load-balancer-listeners anything
                                             :load-balancer-name anything
                                             :load-balancer-ports anything)
         => nil :times 0
         (elb/create-load-balancer-listeners anything
                                             :listeners anything
                                             :load-balancer-name anything)
         => nil :times 0)))

(fact "that nothing done when subnets are identical"
      (ensure-subnets {:local config :remote config})=> {:local config :remote config}
      (provided
       (elb/detach-load-balancer-from-subnets anything
                                              :load-balancer-name anything
                                              :subnets anything)
       => nil :times 0
       (elb/attach-load-balancer-to-subnets anything
                                            :load-balancer-name anything
                                            :subnets anything)
       => nil :times 0))

(fact "that missing subnets are added and extras are removed"
      (let [new-config (assoc config :subnets ["subnet-id-1" "subnet-id-3"])]
        (ensure-subnets {:local new-config :remote config})=> {:local new-config :remote config}
        (provided
         (elb/detach-load-balancer-from-subnets anything
                                                :load-balancer-name "elb-name"
                                                :subnets ["subnet-id-2"])
         => ..detach-result..
         (elb/attach-load-balancer-to-subnets anything
                                              :load-balancer-name "elb-name"
                                              :subnets ["subnet-id-3"])
         => ..attach-result..)))

(fact "that security groups aren't created when configurations are identical"
      (ensure-security-groups {:local config :remote config})
      => {:local config :remote config}
      (provided
       (elb/apply-security-groups-to-load-balancer anything
                                                   :load-balancer-name anything
                                                   :security-groups anything)
       => nil :times 0))

(fact  "that local security groups are applied when configurations are different"
       (let [new-config (assoc config :security-groups (conj (:security-groups config) "sg-id-3"))]
         (ensure-security-groups {:local new-config :remote config}) => {:local new-config :remote config}
         (provided
          (elb/apply-security-groups-to-load-balancer anything
                                                      :load-balancer-name "elb-name"
                                                      :security-groups ["sg-id-1" "sg-id-2" "sg-id-3"])
          => ..apply-result..)))

(fact "that converting cross-zone gives back the exact thing we provided if it's a map"
      (convert-cross-zone {:a "map"}) => {:a "map"})

(fact "that converting cross-zone gives back anything not a map as the 'enabled' parameter of a map"
      (convert-cross-zone false) => {:enabled false}
      (convert-cross-zone "even broken") => {:enabled "even broken"})

(fact "that converting cross-zone defaults to true if nothing is provided"
      (convert-cross-zone nil) => {:enabled true})

(fact "that converting local attributes populates the correct defaults when nothing is provided"
      (convert-local-attributes nil)
      => {:access-log {:enabled false}
          :additional-attributes []
          :connection-draining {:enabled true
                                :timeout 300}
          :connection-settings {:idle-timeout 60}
          :cross-zone-load-balancing {:enabled true}})

(fact "that converting local attributes weaves in our custom values"
      (convert-local-attributes {:access-log {:enabled true}
                                 :connection-draining {:enabled true
                                                       :timeout 10}
                                 :connection-settings {:idle-timeout 5}
                                 :cross-zone {:enabled false}})
      => {:access-log {:enabled true}
          :additional-attributes []
          :connection-draining {:enabled true
                                :timeout 10}
          :connection-settings {:idle-timeout 5}
          :cross-zone-load-balancing {:enabled false}})

(fact "that ensuring attributes doesn't do anything when configurations are the same"
      (def local {:load-balancer-name ..name..})
      (ensure-attributes {:local local}) => {:local local}
      (provided
       (convert-local-attributes local) => {:some "attributes"}
       (get-load-balancer-attributes ..name..) => {:some "attributes"}
       (elb/modify-load-balancer-attributes anything :load-balancer-name anything :load-balancer-attributes anything) => nil :times 0))

(fact "that ensuring attributes modifies attributes when configurations are different"
      (def local {:load-balancer-name ..name..})
      (ensure-attributes {:local local}) => {:local local}
      (provided
       (convert-local-attributes local) => {:some "attributes"}
       (get-load-balancer-attributes ..name..) => {:other "attributes"}
       (elb/modify-load-balancer-attributes anything :load-balancer-name ..name.. :load-balancer-attributes {:some "attributes"}) => ..modify-result..))

(fact "that ensuring tags when no tags exist and none have been provided works"
      (def local {:load-balancer-name ..name..})
      (ensure-tags {:local local} "application") => {:local local}
      (provided
       (get-load-balancer-tags ..name..) => nil
       (elb/remove-tags anything :load-balancer-names anything :tags anything) => nil :times 0
       (elb/add-tags anything :load-balancer-names [..name..] :tags [{:key "PedanticApplication" :value "application"}]) => ..add-result..))

(fact "that ensuring tags and having to change the value of an existing tag works"
      (def local {:load-balancer-name ..name..
                  :tags [{:key "one" :value "new-value"}]})
      (ensure-tags {:local local} "application") => {:local local}
      (provided
       (get-load-balancer-tags ..name..) => [{:key "PedanticApplication" :value "application"}
                                             {:key "one" :value "old-value"}]
       (elb/remove-tags anything :load-balancer-names anything :tags anything) => nil :times 0
       (elb/add-tags anything :load-balancer-names [..name..] :tags [{:key "one" :value "new-value"}]) => ..add-result..))

(fact "that ensuring tags and removing an existing tag works"
      (def local {:load-balancer-name ..name..
                  :tags []})
      (ensure-tags {:local local} "application") => {:local local}
      (provided
       (get-load-balancer-tags ..name..) => [{:key "PedanticApplication" :value "application"}
                                             {:key "two" :value "value"}]
       (elb/remove-tags anything :load-balancer-names [..name..] :tags [{:key "two"}]) => ..remove-result..
       (elb/add-tags anything :load-balancer-names anything :tags anything) => nil :times 0))

(fact "that trying to overwrite one of the required tags fails miserably"
      (def local {:load-balancer-name ..name..
                  :tags [{:key "PedanticApplication" :value "haxx"}]})
      (ensure-tags {:local local} "application") => {:local local}
      (provided
       (get-load-balancer-tags ..name..) => [{:key "PedanticApplication" :value "application"}]
       (elb/remove-tags anything :load-balancer-names anything :tags anything) => nil :times 0
       (elb/add-tags anything :load-balancer-names anything :tags anything) => nil :times 0))

(fact "that identifying a VPC via its subnets works"
      (subnets-to-vpc ["subnet-id1" "subnet-id2"]) => "vpc-id"
      (provided
       (ec2/describe-subnets anything :subnet-ids ["subnet-id1" "subnet-id2"]) => {:subnets [{:vpc-id "vpc-id"} {:vpc-id "vpc-id"}]}))

(fact "that identifying a VPC via subnets which aren't all in the same VPC fails"
      (subnets-to-vpc ["subnet-id1" "subnet-id2"]) => (throws clojure.lang.ExceptionInfo "Multiple VPCs identified")
      (provided
       (ec2/describe-subnets anything :subnet-ids ["subnet-id1" "subnet-id2"]) => {:subnets [{:vpc-id "vpc-id1"} {:vpc-id "vpc-id2"}]}))

(fact "that ensuring an ELB only creates a load balancer if there's no remote configuration"
      (let [config {:load-balancer-name ..elb-name..
                    :security-groups ..security-group-names..
                    :subnets ..subnets..}]
        (ensure-elb "application" config) => nil
        (provided
         (subnets-to-vpc ..subnets..) => ..vpc-id..
         (security-group-names-to-ids ..security-group-names.. ..vpc-id..) => ..security-group-ids..
         (find-elb ..elb-name..) => nil
         (ensure-healthcheck anything) => nil :times 0
         (ensure-security-groups anything) => nil :times 0
         (ensure-subnets anything) => nil :times 0
         (ensure-listeners anything) => nil :times 0
         (create-elb {:load-balancer-name ..elb-name..
                      :security-groups ..security-group-ids..
                      :subnets ..subnets..})
         => ..create-elb-result..
         (create-healthcheck ..create-elb-result..)
         => ..create-healthcheck-result..
         (ensure-attributes ..create-healthcheck-result..)
         => ..ensure-attributes-result..
         (ensure-tags ..ensure-attributes-result.. "application")
         => ..ensure-tags-result..)))

(fact "that ensuring an ELB updates an existing load balancer if one is found"
      (let [config {:load-balancer-name ..elb-name..
                    :security-groups ..security-group-names..
                    :subnets ..subnets..}]
        (ensure-elb "application" config) => nil
        (provided
         (subnets-to-vpc ..subnets..) => ..vpc-id..
         (security-group-names-to-ids ..security-group-names.. ..vpc-id..) => ..security-group-ids..
         (find-elb ..elb-name..) => ..remote..
         (ensure-healthcheck {:local {:load-balancer-name ..elb-name..
                                      :security-groups ..security-group-ids..
                                      :subnets ..subnets..} :remote ..remote..})
         => ..ensure-healthcheck-result..
         (ensure-security-groups ..ensure-healthcheck-result..) => ..ensure-security-groups-result..
         (ensure-subnets ..ensure-security-groups-result..) => ..ensure-subnets-result..
         (ensure-listeners ..ensure-subnets-result..) => ..ensure-listeners-result..
         (ensure-attributes ..ensure-listeners-result..) => ..ensure-attributes-result..
         (ensure-tags ..ensure-attributes-result.. "application") => ..ensure-tags-result..
         (create-elb anything) => nil :times 0
         (create-healthcheck anything) => nil :times 0)))

(fact "that ensuring multiple load balancers goes through each one"
      (ensure-elbs {:LoadBalancer [..elb-1.. ..elb-2..]} "application") => nil
      (provided
       (convert-local-config ..elb-1..) => ..converted-1..
       (convert-local-config ..elb-2..) => ..converted-2..
       (ensure-elb "application" ..converted-1..) => ..ensure-1-result..
       (ensure-elb "application" ..converted-2..) => ..ensure-2-result..))
