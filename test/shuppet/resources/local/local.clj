(def $vpc-id "vpc-7bc88713")
(def $sg-ingress [{:IpRanges "10.216.221.0/24"
                   :IpProtocol "tcp"
                   :FromPort 80
                   :ToPort 8080}
                  {:IpRanges  "10.83.1.0/24"
                   :IpProtocol "tcp"
                   :FromPort 80
                   :ToPort 8080}])
(def $sg-egress [{:IpRanges "0.0.0.0/0"
                  :IpProtocol -1}])
(def $elb-subnets-be ["subnet-24df904c" "subnet-bdc08fd5"])
(def $private-ips ["10.0.0.0/8" "172.16.0.0/12" "192.168.0.0/16"])
(def $sg-egress-all [{:IpRanges "0.0.0.0/0"
                      :IpProtocol -1}])
(def $sg-ssh "test-ssh")
(def $sg-http-8080 "Brislabs-8080")

(def $s3-puppet-read-policy {:PolicyName "s3ReposReadOnly"
                             :Version "2012-10-17",
                             :PolicyDocument [{:Sid "s3ReposReadOnly"
                                               :Effect "Allow"
                                               :Action ["s3:GetObject",
                                                        "s3:ListBucket"]
                                               :Resource ["arn:aws:s3:::ent-aws-puppet",
                                                          "arn:aws:s3:::ent-aws-puppet/*",
                                                          "arn:aws:s3:::ent-aws-repo/*",
                                                          "arn:aws:s3:::eu-west-1-dist-rd-cloudplatform-nokia-com/yum-repos/*",
                                                          "arn:aws:s3:::eu-west-1-dist-p-cloudplatform-nokia-com/yum-repos/*"]}]})

(def $ec2-describe-tags-policy {:PolicyName "ec2DescribeTags"
                                :Version "2012-10-17",
                                :PolicyDocument [{:Sid "ec2DescribeTags"
                                                  :Effect "Allow"
                                                  :Action ["autoscaling:DescribeAutoScalingInstances",
                                                           "ec2:DescribeTags"]
                                                  :Resource ["*"]}]})

;Here we define a configuration as a Clojure map, it must be at the end of the file to be used by Shuppet.
;This map defines a security group as in http://docs.aws.amazon.com/AWSEC2/latest/APIReference/ApiReference-query-CreateSecurityGroup.html

{:SecurityGroups [{:GroupName $sg-ssh
                   :GroupDescription "test SSH"
                   :VpcId $vpc-id
                   :Ingress [{:IpRanges $private-ips
                               :IpProtocol "tcp"
                               :FromPort 22
                               :ToPort 22}]
                   :Egress $sg-egress-all}]

 :DefaultRolePolicies [$s3-puppet-read-policy $ec2-describe-tags-policy]}
