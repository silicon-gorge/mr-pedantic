(ns pedantic.iam-test
  (:require [amazonica.aws.identitymanagement :as im]
            [cheshire.core :as json]
            [midje.sweet :refer :all]
            [pedantic
             [iam :refer :all]
             [util :as util]])
  (:import [com.amazonaws.services.identitymanagement.model NoSuchEntityException]))

(fact "that role-exists? returns true when the specified role exists"
      (role-exists? "role") => true
      (provided
       (im/get-role anything :role-name "role") => {}))

(fact "that role-exists? returns false when nothing comes back"
      (role-exists? "role") => false
      (provided
       (im/get-role anything :role-name "role") => nil))

(fact "that role-exists? returns false when NoSuchEntityException is thrown"
      (role-exists? "role") => false
      (provided
       (im/get-role anything :role-name "role") =throws=> (NoSuchEntityException. "Busted")))

(fact "that create-role does just that"
      (create-role "role") => nil
      (provided
       (im/create-role anything
                       :role-name "role"
                       :path "/"
                       :assume-role-policy-document anything)
       => ..create-result..))

(fact "that ensuring a role does nothing if that role already exists"
      (ensure-role {:RoleName "role"}) => {:RoleName "role"}
      (provided
       (role-exists? "role") => true
       (create-role anything) => nil :times 0))

(fact "that ensuring a role creates that role if it doesn't exist"
      (ensure-role {:RoleName "role"}) => {:RoleName "role"}
      (provided
       (role-exists? "role") => false
       (create-role "role") => ..create-result..))

(fact "that checking whether a profile with a given role exists is truthy if one exists"
      (profile-with-role-exists? "role") => truthy
      (provided
       (im/list-instance-profiles-for-role anything
                                           :role-name "role"
                                           :max-items 1)
       => {:instance-profiles [{}]}))

(fact "that checking whether a profile with a given role exists is falsey if nothing exists"
      (profile-with-role-exists? "role") => falsey
      (provided
       (im/list-instance-profiles-for-role anything
                                           :role-name "role"
                                           :max-items 1)
       => {:instance-profiles []}))

(fact "that adding a role to a profile does just that"
      (add-role-to-profile "role" "profile") => nil
      (provided
       (im/add-role-to-instance-profile anything
                                        :role-name "role"
                                        :instance-profile-name "profile") => ..add-result..))

(fact "that ensuring a profile with a role does nothing if a profile with the role already exists"
      (ensure-profile-with-role "role" "profile") => nil
      (provided
       (profile-with-role-exists? "role") => true
       (add-role-to-profile anything anything) => nil :times 0))

(fact "that ensuring a profile with a role adds that role if the profile doesn't exist with that role"
      (ensure-profile-with-role "role" "profile") => nil
      (provided
       (profile-with-role-exists? "role") => false
       (add-role-to-profile "role" "profile") => ..add-result..))

(fact "that creating an instance profile does just that"
      (create-instance-profile "profile") => nil
      (provided
       (im/create-instance-profile anything :instance-profile-name "profile") => ..create-result..))

(fact "that checking whether an instance profile exists is truthy if it exists"
      (instance-profile-exists? "profile") => truthy
      (provided
       (im/get-instance-profile anything :instance-profile-name "profile") => {}))

(fact "that checking whether an instance profile exists is falsey if nothing comes back"
      (instance-profile-exists? "profile") => falsey
      (provided
       (im/get-instance-profile anything :instance-profile-name "profile") => nil))

(fact "that checking whether an instance profile exists is falsey if it doesn't exist"
      (instance-profile-exists? "profile") => falsey
      (provided
       (im/get-instance-profile anything :instance-profile-name "profile") =throws=> (NoSuchEntityException. "Busted")))

(fact "that ensuring an instance profile does not try to create it if that profile already exists"
      (ensure-instance-profile {:RoleName "profile"}) => nil
      (provided
       (instance-profile-exists? "profile") => true
       (create-instance-profile anything) => nil :times 0
       (ensure-profile-with-role "profile" "profile") => ..ensure-result..))

(fact "that ensuring an instance profile creates the profile if it doesn't exist"
      (ensure-instance-profile {:RoleName "profile"}) => nil
      (provided
       (instance-profile-exists? "profile") => false
       (create-instance-profile "profile") => ..create-result..
       (ensure-profile-with-role "profile" "profile") => ..ensure-result..))

(fact "that creating a policy statement works"
      (create-policy-document "version" [{:Action ["action-1"]
                                          :Resource ["resource-1"]}
                                         {:Action ["action-2"]
                                          :Resource ["resource-2"]}])
      => {:Version "version"
          :Statement [{:Action ["action-1"]
                       :Effect "Allow"
                       :Resource ["resource-1"]}
                      {:Action ["action-2"]
                       :Effect "Allow"
                       :Resource ["resource-2"]}]})

(fact "that creating an IAM policy works"
      (create-iam-policy {:PolicyName "policy-name"
                          :PolicyDocument "document"
                          :Version "version"})
      => {:PolicyName "policy-name"
          :PolicyDocument ..policy-document..}
      (provided
       (create-policy-document "version" "document") => ..policy-document..))

(fact "that creating IAM policies works"
      (create-iam-policies [..policy-1.. ..policy-2..]) => [..mapped-policy-1.. ..mapped-policy-2..]
      (provided
       (create-iam-policy ..policy-1..) => ..mapped-policy-1..
       (create-iam-policy ..policy-2..) => ..mapped-policy-2..))

(fact "that getting a policy document does just that"
      (get-policy-document "role" "policy") => {:PolicyName ..policy-name..
                                                :PolicyDocument ..json..}
      (provided
       (im/get-role-policy anything :role-name "role" :policy-name "policy")
       => {:policy-name ..policy-name..
           :policy-document ..document..}
       (util/url-decode ..document..) => ..decoded..
       (json/parse-string ..decoded.. true) => ..json..))

(fact "that getting remote policies works"
      (get-remote-policies "role") => [..policy-1..
                                       ..policy-2..
                                       ..policy-3..]
      (provided
       (im/list-role-policies anything
                              :role-name "role"
                              :max-items 100)
       => {:policy-names ["policy-1" "policy-2" "policy-3"]}
       (get-policy-document "role" "policy-1") => ..policy-1..
       (get-policy-document "role" "policy-2") => ..policy-2..
       (get-policy-document "role" "policy-3") => ..policy-3..))

(fact "that deleting a role policy does just that"
      (delete-role-policy "role" {:PolicyName "policy"}) => nil
      (provided
       (im/delete-role-policy anything :role-name "role" :policy-name "policy") => ..delete-result..))

(fact "that upserting a role policy does just that"
      (upsert-role-policy "role" {:PolicyName "policy"
                                  :PolicyDocument ..policy-document..})
      => nil
      (provided
       (json/generate-string ..policy-document..) => ..policy-json..
       (im/put-role-policy anything
                           :role-name "role"
                           :policy-name "policy"
                           :policy-document ..policy-json..)
       => ..put-result..))

(fact "that ensuring policies does nothing when everything exists already"
      (ensure-policies {:RoleName "role" :Policies [{:PolicyName "policy-1"}
                                                    {:PolicyName "policy-2"}]})
      => nil
      (provided
       (create-iam-policies [{:PolicyName "policy-1"}
                             {:PolicyName "policy-2"}])
       => ..local-policies..
       (get-remote-policies "role") => ..remote-policies..
       (util/compare-config ..local-policies.. ..remote-policies..) => [nil nil]
       (upsert-role-policy anything anything) => nil :times 0
       (delete-role-policy anything anything) => nil :times 0))

(fact "that ensuring policies updates existing policies if they different"
      (ensure-policies {:RoleName "role" :Policies [{:PolicyName "policy-1"}
                                                    {:PolicyName "policy-2"}]})
      => nil
      (provided
       (create-iam-policies [{:PolicyName "policy-1"}
                             {:PolicyName "policy-2"}])
       => ..local-policies..
       (get-remote-policies "role") => ..remote-policies..
       (util/compare-config ..local-policies.. ..remote-policies..) => [nil [..different-policy..]]
       (upsert-role-policy "role" ..different-policy..) => ..upsert-result..
       (delete-role-policy anything anything) => nil :times 0))

(fact "that ensuring policies deletes existing policies if they aren't in the local configuration"
      (ensure-policies {:RoleName "role" :Policies [{:PolicyName "policy-1"}
                                                    {:PolicyName "policy-2"}]})
      => nil
      (provided
       (create-iam-policies [{:PolicyName "policy-1"}
                             {:PolicyName "policy-2"}])
       => ..local-policies..
       (get-remote-policies "role") => ..remote-policies..
       (util/compare-config ..local-policies.. ..remote-policies..) => [[{:PolicyName "policy-3"}] nil]
       (upsert-role-policy anything anything) => nil :times 0
       (delete-role-policy "role" {:PolicyName "policy-3"}) => ..delete-result..))

(fact "that ensuring policies leaves existing policies if they are in the local configuration but are different"
      (ensure-policies {:RoleName "role" :Policies [{:PolicyName "policy-1"}
                                                    {:PolicyName "policy-2"}]})
      => nil
      (provided
       (create-iam-policies [{:PolicyName "policy-1"}
                             {:PolicyName "policy-2"}])
       => ..local-policies..
       (get-remote-policies "role") => ..remote-policies..
       (util/compare-config ..local-policies.. ..remote-policies..) => [[{:PolicyName "policy-2"}] nil]
       (upsert-role-policy anything anything) => nil :times 0
       (delete-role-policy anything anything) => nil :times 0))

(fact "that ensuring IAM goes through all the steps"
      (ensure-iam {:Role ..role..}) => nil
      (provided
       (ensure-role ..role..) => ..role-result..
       (ensure-instance-profile ..role..) => ..instance-profile-result..
       (ensure-policies ..role..) => ..policies-result..))
