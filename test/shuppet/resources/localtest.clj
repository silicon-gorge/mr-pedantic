(def elb-8080->8080  {:LoadBalancerPort 8080
                      :InstancePort 8080
                      :Protocol "http"
                      :InstanceProtocol "http"})
(def elb-healthcheck-ping {:Target "HTTP:8080/1.x/ping"
                           :HealthyThreshold 2
                           :UnhealthyThreshold 2
                           :Interval 6
                           :Timeout 5})
(def elb-sg-name (str $app-name "-lb"))

{:SecurityGroups [{:GroupName $app-name
                   :GroupDescription (str "Security group for application " $app-name)
                   :VpcId $vpc-id
                   :Ingress [{:IpRanges ["sg-14fe1a7b" "Brislabs-SSH"]
                              :IpProtocol "tcp"
                              :FromPort 80
                              :ToPort 8080}]
                   :Egress $sg-egress}
                  {:GroupName elb-sg-name
                   :GroupDescription (str "Security group for load balancer " $app-name)
                   :VpcId $vpc-id
                   :Ingress $sg-ingress
                   :Egress [{:IpRanges ["198.0.2.0/24" "198.51.100.0/24"]
                             :IpProtocol "tcp"
                             :FromPort 80
                             :ToPort 8080}
                            {:IpRanges "198.100.2.0/24"
                             :IpProtocol "udp"
                             :FromPort 80
                             :ToPort 8080}
                            ]}]

 :LoadBalancer {:LoadBalancerName $app-name
                :Listeners [elb-8080->8080]
                :SecurityGroups [elb-sg-name]
                :Subnets $elb-subnets
                :Scheme "internal"
                :HealthCheck elb-healthcheck-ping}


 :Role {:RoleName $app-name
        :Policies  [{:PolicyName "s3-read-only"
                     :PolicyDocument [{:Effect "Allow"
                                       :Action "EC2:Describe*"
                                       :Resource "*"}
                                      {:Effect "Allow"
                                       :Sid "s3ReposReadOnly"
                                       :Principal {:AWS ["arn:aws:iam::513894612423:user/amanas"]}
                                       :Action ["s3:GetObject" "s3:ListBucket"]
                                       :Resource "*"}]}
                    {:PolicyName "s3-full-access"
                     :PolicyDocument [{:Action "s3:*"
                                       :Resource "*"}]}]}

 :S3 [{:BucketName "shuppet-test"
       :Id "local-test"
       :Statement [{:Sid "1"
                    :Principal {:AWS ["arn:aws:iam::513894612423:user/bot/bakins"]}
                    :Action "s3:*"
                    :Resource "arn:aws:s3:::shuppet-test/*"}
                   {:Sid "2"
                    :Principal {:AWS ["arn:aws:iam::513894612423:user/bot/bakins"]}
                    :Action "s3:*"
                    :Resource "arn:aws:s3:::shuppet-test/*"}]
       :AccessControlPolicy {:Owner {:ID "9de2bfc0800fb4335f649e259431d7dca8bbce19b39e98bfed0fb70911b8e9bb"
                                     :DisplayName "I_EXT_AWS_ENTERTAINMENT_RD"}
                             :AccessControlList [{:Permission "FULL_CONTROL"
                                                  :ID "9de2bfc0800fb4335f649e259431d7dca8bbce19b39e98bfed0fb70911b8e9bb"}]}}]

 :DynamoDB [{:TableName "shuppet-test"
             :ProvisionedThroughput {:ReadCapacityUnits 3
                                     :WriteCapacityUnits 5}
             :AttributeDefinitions {"Subject" "S"
                                    "ForumName" "S"
                                    "LastPostDateTime" "S"
                                    }
             :KeySchema {:ForumName "HASH"
                         :Subject "RANGE"}
             :LocalSecondaryIndexes [{:IndexName "LastPostIndex"
                                      :KeySchema {:LastPostDateTime "RANGE"
                                                  :ForumName "HASH"}
                                      :Projection {:ProjectionType "KEYS_ONLY"}}]

             :ForceDelete false}]

  :Campfire [$cf-shuppet]}
