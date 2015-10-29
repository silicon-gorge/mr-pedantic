(ns pedantic.securitygroups-test
  (:require [amazonica.aws.ec2 :as ec2]
            [midje.sweet :refer :all]
            [pedantic.securitygroups :refer :all])
  (:import [clojure.lang ExceptionInfo]))

(fact "that getting a security group by name works"
      (security-group-by-name "name" "vpc-id") => ..security-group..
      (provided
       (ec2/describe-security-groups anything
                                     :filters [{:name "group-name" :values ["name"]}
                                               {:name "vpc-id" :values ["vpc-id"]}])
       => {:security-groups [..security-group..]}))

(fact "that getting a security group which doesn't exist gives nil"
      (security-group-by-name "name" "vpc-id") => nil
      (provided
       (ec2/describe-security-groups anything
                                     :filters [{:name "group-name" :values ["name"]}
                                               {:name "vpc-id" :values ["vpc-id"]}])
       => {:security-groups []}))

(fact "that determining whether something is a security group ID works"
      (is-security-group-id? "something") => falsey
      (is-security-group-id? "sg-12345678") => truthy)

(fact "that determining whether something is a CIDR range works"
      (is-cidr? "not cidr") => falsey
      (is-cidr? "10.0.0.0/8") => truthy)

(fact "that replacing security group names works when we find a name"
      (replace-security-group-name "some-name" "vpc-id")
      => "sg-12345678"
      (provided
       (security-group-by-name "some-name" "vpc-id") => {:group-id "sg-12345678"}))

(fact "that replacing security group names doesn't do anything when given a CIDR"
      (replace-security-group-name "192.168.0.0/16" "vpc-id")
      => "192.168.0.0/16"
      (provided
       (security-group-by-name anything anything) => nil :times 0))

(fact "that replacing security group names doesn't do anything when given a security group ID"
      (replace-security-group-name "sg-12345678" "vpc-id")
      => "sg-12345678"
      (provided
       (security-group-by-name anything anything) => nil :times 0))

(fact "that attempting to replace a security group name when that group doesn't exist throws an exception"
      (replace-security-group-name "unknown" "vpc-id")
      => (throws ExceptionInfo)
      (provided
       (security-group-by-name "unknown" "vpc-id") => nil))

(fact "that switching an IP range to a group ID works if we find a security group ID"
      (switch-ip-range-to-group-id {:ip-ranges ["sg-12345678"]})
      => {:ip-ranges []
          :prefix-list-ids []
          :user-id-group-pairs [{:group-id "sg-12345678"}]})

(fact "that switching an IP range to a group ID does nothing if we couldn't find a security group ID"
      (switch-ip-range-to-group-id {:ip-ranges ["10.0.0.0/8"]})
      => {:ip-ranges ["10.0.0.0/8"]
          :prefix-list-ids []
          :user-id-group-pairs []})

(fact "that expanding IP ranges handles a single IP range"
      (expand-ip-ranges {:ip-protocol "tcp"
                         :ip-ranges "10.0.0.0/8"} "vpc-id")
      => [{:ip-protocol "tcp"
           :ip-ranges ["10.0.0.0/8"]}])

(fact "that expanding IP ranges handles a collection of IP ranges"
      (expand-ip-ranges {:ip-protocol "tcp"
                         :ip-ranges ["10.0.0.0/8" "192.168.0.0/16"]}
                        "vpc-id")
      => [{:ip-protocol "tcp"
           :ip-ranges ["10.0.0.0/8"]}
          {:ip-protocol "tcp"
           :ip-ranges ["192.168.0.0/16"]}])

(fact "that expanding IP ranges goes off in search of security group IDs if necessary"
      (expand-ip-ranges {:ip-protocol "tcp"
                         :ip-ranges ["some-name" "some-other-name"]}
                        "vpc-id")
      => [{:ip-protocol "tcp"
           :ip-ranges ["sg-12345678"]}
          {:ip-protocol "tcp"
           :ip-ranges ["sg-87654321"]}]
      (provided
       (security-group-by-name "some-name" "vpc-id") => {:group-id "sg-12345678"}
       (security-group-by-name "some-other-name" "vpc-id") => {:group-id "sg-87654321"}))

(fact "that converting ingress permissions does what we need it to"
      (convert-ingress-permissions {:ingress [{:ip-ranges "10.0.0.0/8"}
                                              {:ip-ranges "192.168.0.0/16"}]
                                    :vpc-id "vpc-id"})
      => {:ip-permissions [{:ip-ranges ["10.0.0.0/8"]
                            :prefix-list-ids []
                            :user-id-group-pairs []}
                           {:ip-ranges ["192.168.0.0/16"]
                            :prefix-list-ids []
                            :user-id-group-pairs []}]
          :vpc-id "vpc-id"})

(fact "that converting egress permissions does what we need it to"
      (convert-egress-permissions {:egress [{:ip-ranges "10.0.0.0/8"}
                                            {:ip-ranges "192.168.0.0/16"}]
                                   :vpc-id "vpc-id"})
      => {:ip-permissions-egress [{:ip-ranges ["10.0.0.0/8"]
                                   :prefix-list-ids []
                                   :user-id-group-pairs []}
                                  {:ip-ranges ["192.168.0.0/16"]
                                   :prefix-list-ids []
                                   :user-id-group-pairs []}]
          :vpc-id "vpc-id"})

(fact "that converting local configuration works"
      (convert-local-config {:GroupName "group-name"
                             :GroupDescription "Some description"
                             :VpcId "vpc-id"
                             :Ingress [{:FromPort 1337
                                        :IpProtocol "tcp"
                                        :IpRanges "some-target-group"
                                        :ToPort 1337}
                                       {:FromPort 80
                                        :IpProtocol "tcp"
                                        :IpRanges ["10.0.0.0/8" "192.168.0.0/16"]
                                        :ToPort 80}]
                             :Egress [{:IpProtocol "-1"
                                       :IpRanges ["10.0.0.0/8"]}]})
      => {:description "Some description"
          :group-name "group-name"
          :ip-permissions [{:from-port 1337
                            :ip-protocol "tcp"
                            :ip-ranges []
                            :prefix-list-ids []
                            :to-port 1337
                            :user-id-group-pairs [{:group-id "sg-12345678"}]}
                           {:from-port 80
                            :ip-protocol "tcp"
                            :ip-ranges ["10.0.0.0/8"]
                            :prefix-list-ids []
                            :to-port 80
                            :user-id-group-pairs []}
                           {:from-port 80
                            :ip-protocol "tcp"
                            :ip-ranges ["192.168.0.0/16"]
                            :prefix-list-ids []
                            :to-port 80
                            :user-id-group-pairs []}]
          :ip-permissions-egress [{:ip-protocol "-1"
                                   :ip-ranges ["10.0.0.0/8"]
                                   :prefix-list-ids []
                                   :user-id-group-pairs []}]
          :vpc-id "vpc-id"}
      (provided
       (security-group-by-name "some-target-group" "vpc-id") => {:group-id "sg-12345678"}))

(fact "that expanding user ID group pairs works if faced with a collection"
      (expand-user-id-group-pairs {:user-id-group-pairs [{:group-id "sg-12345678"
                                                          :owner-id "owner-id"}]})
      => [{:user-id-group-pairs [{:group-id "sg-12345678"}]}])

(fact "that converting IP permissions does what we need it to"
      (convert-ip-permissions {:ip-permissions [{:ip-ranges ["10.0.0.0/8" "192.168.0.0/16"]}
                                                {:user-id-group-pairs [{:group-id "sg-12345678"}
                                                                       {:group-id "sg-87654321"}]}]
                               :vpc-id "vpc-id"})
      => {:ip-permissions [{:ip-ranges ["10.0.0.0/8"]}
                           {:ip-ranges ["192.168.0.0/16"]}
                           {:user-id-group-pairs [{:group-id "sg-12345678"}]}
                           {:user-id-group-pairs [{:group-id "sg-87654321"}]}]
          :vpc-id "vpc-id"})

(fact "that converting IP permissions egress does what we need it to"
      (convert-ip-permissions-egress {:ip-permissions-egress [{:ip-ranges ["10.0.0.0/8" "192.168.0.0/16"]}
                                                              {:user-id-group-pairs [{:group-id "sg-12345678"}]}]
                                      :vpc-id "vpc-id"})
      => {:ip-permissions-egress [{:ip-ranges ["10.0.0.0/8"]}
                                  {:ip-ranges ["192.168.0.0/16"]}
                                  {:user-id-group-pairs [{:group-id "sg-12345678"}]}]
          :vpc-id "vpc-id"})

(fact "that converting remote configuration works"
      (convert-remote-config {:description "Some description"
                              :group-id "group-id"
                              :group-name "group-name"
                              :ip-permissions [{:ip-protocol "tcp"
                                                :from-port 1337
                                                :to-port 1337
                                                :user-id-group-pairs [{:group-id "sg-12345678"
                                                                       :user-id "user-id"}]}
                                               {:ip-protocol "tcp"
                                                :from-port 80
                                                :to-port 80
                                                :ip-ranges ["10.0.0.0/8" "192.168.0.0/16"]}]
                              :ip-permissions-egress [{:ip-protocol "-1"
                                                       :ip-ranges ["10.0.0.0/8"]}]
                              :owner-id "owner-id"
                              :tags [{:key "name"
                                      :value "value"}]
                              :vpc-id "vpc-id"})
      => {:description "Some description"
          :group-id "group-id"
          :group-name "group-name"
          :ip-permissions [{:from-port 1337
                            :ip-protocol "tcp"
                            :to-port 1337
                            :user-id-group-pairs [{:group-id "sg-12345678"}]}
                           {:from-port 80
                            :ip-protocol "tcp"
                            :ip-ranges ["10.0.0.0/8"]
                            :to-port 80}
                           {:from-port 80
                            :ip-protocol "tcp"
                            :ip-ranges ["192.168.0.0/16"]
                            :to-port 80}]
          :ip-permissions-egress [{:ip-protocol "-1"
                                   :ip-ranges ["10.0.0.0/8"]}]
          :tags [{:key "name"
                  :value "value"}]
          :vpc-id "vpc-id"})

(fact "that creating a security group works"
      (create-security-group {:description "Some description"
                              :group-name "group-name"
                              :vpc-id "vpc-id"})
      => "group-id"
      (provided
       (ec2/create-security-group anything
                                  :description "Some description"
                                  :group-name "group-name"
                                  :vpc-id "vpc-id")
       => {:group-id "group-id"}))

(fact "that ensuring ingress rules works"
      (ensure-ingress "group-name" "group-id" [[{:from-port 80
                                                 :ip-protocol "tcp"
                                                 :ip-ranges ["10.0.0.0/8"]
                                                 :to-port 80}
                                                {:from-port 80
                                                 :ip-protocol "tcp"
                                                 :ip-ranges ["192.168.0.0/16"]
                                                 :to-port 80}]
                                               [{:from-port 1337
                                                 :ip-protocol "tcp"
                                                 :to-port 1337
                                                 :user-id-group-pairs [{:group-id "sg-12345678"}]}]])
      => nil
      (provided
       (ec2/authorize-security-group-ingress anything
                                             :group-id "group-id"
                                             :ip-permissions [{:from-port 1337
                                                               :ip-protocol "tcp"
                                                               :to-port 1337
                                                               :user-id-group-pairs [{:group-id "sg-12345678"}]}])
       => ..authorize-ingress-result..
       (ec2/revoke-security-group-ingress anything
                                          :group-id "group-id"
                                          :ip-permissions [{:from-port 80
                                                            :ip-protocol "tcp"
                                                            :ip-ranges ["10.0.0.0/8"]
                                                            :to-port 80}
                                                           {:from-port 80
                                                            :ip-protocol "tcp"
                                                            :ip-ranges ["192.168.0.0/16"]
                                                            :to-port 80}])
       => ..revoke-ingress-result..))

(fact "that ensuring egress rules works"
      (ensure-egress "group-name" "group-id" [[{:ip-protocol "-1"
                                                :ip-ranges ["10.0.0.0/8"]}]
                                              [{:ip-protocol "-1"
                                                :ip-ranges ["192.168.0.0/16"]}]])
      => nil
      (provided
       (ec2/authorize-security-group-egress anything
                                            :group-id "group-id"
                                            :ip-permissions [{:ip-protocol "-1"
                                                              :ip-ranges ["192.168.0.0/16"]}])
       => ..authorize-egress-result..
       (ec2/revoke-security-group-egress anything
                                         :group-id "group-id"
                                         :ip-permissions [{:ip-protocol "-1"
                                                           :ip-ranges ["10.0.0.0/8"]}])
       => ..revoke-egress-result..))

(fact "that ensuring tags does nothing if nothing needs to change"
      (ensure-tags "group-name" "group-id" [[] []]) => nil
      (provided
       (ec2/delete-tags anything :resources anything :tags anything) => nil :times 0
       (ec2/create-tags anything :resources anything :tags anything) => nil :times 0))

(fact "that ensuring tags does not delete tags whose value has changed"
      (ensure-tags "group-name" "group-id" [[{:key "key" :value "old-value"}]
                                            [{:key "key" :value "new-value"}]])
      => nil
      (provided
       (ec2/delete-tags anything :resources anything :tags anything) => nil :times 0
       (ec2/create-tags anything :resources ["group-id"] :tags [{:key "key" :value "new-value"}]) => ..create-result..))

(fact "that ensuring tags removes and adds tags"
      (ensure-tags "group-name" "group-id" [[{:key "to-remove" :value "value"}]
                                            [{:key "to-add" :value "value"}]])
      => nil
      (provided
       (ec2/delete-tags anything :resources ["group-id"] :tags [{:key "to-remove" :value "value"}]) => ..delete-result..
       (ec2/create-tags anything :resources ["group-id"] :tags [{:key "to-add" :value "value"}]) => ..create-result..))

(def realistic-group
  {:description "Some description"
   :group-id "group-id"
   :group-name "group-name"
   :ip-permissions [{:from-port 80
                     :ip-protocol "tcp"
                     :ip-ranges ["192.168.0.0/16"]
                     :to-port 80}
                    {:from-port 80
                     :ip-protocol "tcp"
                     :ip-ranges ["10.0.0.0/8"]
                     :to-port 80}]
   :ip-permissions-egress [{:ip-protocol "-1"
                            :ip-ranges ["0.0.0.0/0"]}]
   :tags [{:key "PedanticApplication" :value "application"}
          {:key "three" :value "three-value"}]})

(fact "that comparing security groups determines the correct actions"
      (compare-security-group realistic-group {:description "Some description"
                                               :group-name "group-name"
                                               :ip-permissions [{:from-port 1337
                                                                 :ip-protocol "tcp"
                                                                 :to-port 1337
                                                                 :user-id-group-pairs [{:group-id "sg-12345678"}]}]
                                               :ip-permissions-egress [{:ip-protocol "-1"
                                                                        :ip-ranges ["192.168.0.0/16"]}]
                                               :tags [{:key "one" :value "one-value"}
                                                      {:key "two" :value "two-value"}]
                                               :vpc-id "vpc-id"} "application")
      => nil
      (provided
       (ensure-ingress "group-name" "group-id" [[{:from-port 80
                                                  :ip-protocol "tcp"
                                                  :ip-ranges ["192.168.0.0/16"]
                                                  :to-port 80}
                                                 {:from-port 80
                                                  :ip-protocol "tcp"
                                                  :ip-ranges ["10.0.0.0/8"]
                                                  :to-port 80}]
                                                [{:from-port 1337
                                                  :ip-protocol "tcp"
                                                  :to-port 1337
                                                  :user-id-group-pairs [{:group-id "sg-12345678"}]}]])
       => ..ensure-ingress-result..
       (ensure-egress "group-name" "group-id" [[{:ip-protocol "-1"
                                                 :ip-ranges ["0.0.0.0/0"]}]
                                               [{:ip-protocol "-1"
                                                 :ip-ranges ["192.168.0.0/16"]}]])
       => ..ensure-egress-result..
       (ensure-tags "group-name" "group-id" [[{:key "three" :value "three-value"}]
                                             [{:key "two" :value "two-value"}
                                              {:key "one" :value "one-value"}]])
       => ..ensure-tags-result..))

(fact "that deleting a security group works"
      (delete-security-group "group-name" "vpc-id") => nil
      (provided
       (security-group-by-name "group-name" "vpc-id") => {:group-id "sg-12345678"}
       (ec2/delete-security-group anything :group-id "sg-12345678") => ..delete-result..))

(fact "that deleting a security group which doesn't exist does nothing"
      (delete-security-group "group-name" "vpc-id") => nil
      (provided
       (security-group-by-name "group-name" "vpc-id") => nil
       (ec2/delete-security-group anything :group-id anything) => nil :times 0))

(fact "that determining whether an egress rule is the default works"
      (is-default-egress? {:ip-protocol "-1" :ip-ranges ["0.0.0.0/0"]}) => truthy
      (is-default-egress? {:ip-protocol "tcp" :ip-ranges ["0.0.0.0/0"]}) => falsey
      (is-default-egress? {:ip-protocol "-1" :ip-ranges ["10.0.0.0/8"]}) => falsey
      (is-default-egress? {:ip-protocol "tcp" :ip-ranges ["10.0.0.0/8"]}) => falsey)

(fact "that building a security group with just the default egress doesn't attempt to ensure that egress"
      (def group {:group-name "group-name"
                  :ip-permissions [{:ip-protocol "tcp"
                                    :ip-ranges ["10.0.0.0/8"]}]
                  :ip-permissions-egress [{:ip-protocol "-1"
                                           :ip-ranges ["0.0.0.0/0"]}]
                  :tags [{:key "one" :value "one-value"}]})
      (build-security-group group "application")
      => nil
      (provided
       (create-security-group group) => "sg-12345678"
       (ensure-ingress "group-name" "sg-12345678" [[] [{:ip-protocol "tcp"
                                                        :ip-ranges ["10.0.0.0/8"]}]])
       => ..ensure-ingress-result..
       (ensure-egress anything anything anything) => nil :times 0
       (ensure-tags "group-name" "sg-12345678" [[] [{:key "one" :value "one-value"} {:key "PedanticApplication" :value "application"}]]) => ..ensure-tags-result..))

(fact "that building a security group with custom egress rules removes the default egress rule which would have already been created"
      (def group {:group-name "group-name"
                  :ip-permissions [{:ip-protocol "tcp"
                                    :ip-ranges ["10.0.0.0/8"]}]
                  :ip-permissions-egress [{:ip-protocol "tcp"
                                           :ip-ranges ["10.0.0.0/8"]}]})
      (build-security-group group "application")
      => nil
      (provided
       (create-security-group group) => "sg-12345678"
       (ensure-ingress "group-name" "sg-12345678" [[] [{:ip-protocol "tcp"
                                                        :ip-ranges ["10.0.0.0/8"]}]])
       => ..ensure-ingress-result..
       (ensure-egress "group-name" "sg-12345678" [[{:ip-protocol "-1"
                                                    :ip-ranges ["0.0.0.0/0"]}]
                                                  [{:ip-protocol "tcp"
                                                    :ip-ranges ["10.0.0.0/8"]}]])
       => ..ensure-egress-result..
       (ensure-tags "group-name" "sg-12345678" [[] [{:key "PedanticApplication" :value "application"}]])
       => ..ensure-tags-result..))

(fact "that an error while building a security group removes that security group"
      (def group {:group-name "group-name"
                  :ip-permissions [{:ip-protocol "tcp"
                                    :ip-ranges ["10.0.0.0/8"]}]
                  :ip-permissions-egress [{:ip-protocol "-1"
                                           :ip-ranges ["0.0.0.0/0"]}]
                  :vpc-id "vpc-id"})
      (build-security-group group "application") => (throws clojure.lang.ExceptionInfo)
      (provided
       (create-security-group group) => "sg-12345678"
       (ensure-ingress anything anything anything) =throws=> (ex-info "Busted" {})
       (delete-security-group "group-name" "vpc-id") => ..delete-result..))

(fact "that ensuring a security group which doesn't exist builds that group"
      (ensure-security-group "application"
                             {:group-name "group-name"
                              :vpc-id "vpc-id"})
      => nil
      (provided
       (security-group-by-name "group-name" "vpc-id") => nil
       (build-security-group {:group-name "group-name"
                              :vpc-id "vpc-id"}
                             "application")
       => ..build-result..))

(fact "that ensuring a security group which already exists compares the configuration"
      (ensure-security-group "application"
                             {:group-name "group-name"
                              :vpc-id "vpc-id"})
      => nil
      (provided
       (security-group-by-name "group-name" "vpc-id") => ..remote-group..
       (convert-remote-config ..remote-group..) => {:group-id "group-id"}
       (compare-security-group {:group-id "group-id"} {:group-name "group-name"
                                                       :vpc-id "vpc-id"}
                               "application")
       => ..build-result..))

(fact "that ensuring security groups ensures each one in turn"
      (ensure-security-groups {:SecurityGroups [..sg-1.. ..sg-2..]} "application") => nil
      (provided
       (convert-local-config ..sg-1..) => ..converted-1..
       (convert-local-config ..sg-2..) => ..converted-2..
       (ensure-security-group "application" ..converted-1..) => ..ensure-1-result..
       (ensure-security-group "application" ..converted-2..) => ..ensure-2-result..))
