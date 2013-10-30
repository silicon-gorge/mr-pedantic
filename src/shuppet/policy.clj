(ns shuppet.policy
  (:require
   [shuppet.util :refer [without-nils]]))

(def aws-read-only-actions ["autoscaling:Describe*",
                            "cloudformation:DescribeStacks",
                            "cloudformation:DescribeStackEvents",
                            "cloudformation:DescribeStackResources",
                            "cloudformation:GetTemplate",
                            "cloudfront:Get*",
                            "cloudfront:List*",
                            "cloudwatch:Describe*",
                            "cloudwatch:Get*",
                            "cloudwatch:List*",
                            "directconnect:Describe*",
                            "dynamodb:GetItem",
                            "dynamodb:BatchGetItem",
                            "dynamodb:Query",
                            "dynamodb:Scan",
                            "dynamodb:DescribeTable",
                            "dynamodb:ListTables",
                            "ec2:Describe*",
                            "elasticache:Describe*",
                            "elasticbeanstalk:Check*",
                            "elasticbeanstalk:Describe*",
                            "elasticbeanstalk:List*",
                            "elasticbeanstalk:RequestEnvironmentInfo",
                            "elasticbeanstalk:RetrieveEnvironmentInfo",
                            "elasticloadbalancing:Describe*",
                            "elastictranscoder:Read*",
                            "elastictranscoder:List*",
                            "iam:List*",
                            "iam:Get*",
                            "opsworks:Describe*",
                            "opsworks:Get*",
                            "route53:Get*",
                            "route53:List*",
                            "redshift:Describe*",
                            "redshift:ViewQueriesInConsole",
                            "rds:Describe*",
                            "rds:ListTagsForResource",
                            "s3:Get*",
                            "s3:List*",
                            "sdb:GetAttributes",
                            "sdb:List*",
                            "sdb:Select*",
                            "ses:Get*",
                            "ses:List*",
                            "sns:Get*",
                            "sns:List*",
                            "sqs:GetQueueAttributes",
                            "sqs:ListQueues",
                            "sqs:ReceiveMessage",
                            "storagegateway:List*",
                            "storagegateway:Describe*"])

(defn- create-policy-document
  ([sid effect services actions resources]
     (let [services (if (string? services) [services] (vec services))
           actions (if (string? actions) [actions] (vec actions))
           resources (if (string? resources) [resources] (vec resources))]
       (without-nils {:Effect effect
                       :Sid sid
                       :Principal (without-nils {:Service services})
                       :Action actions
                       :Resource resources}))))

(defn create-policy
  ([sid effect services actions resources]
     {:Statement [(create-policy-document sid effect services actions resources)]})
  ([{:keys [Sid Effect Services Actions Resources]}]
     (create-policy Sid Effect Services Actions Resources)))

(defn join-policies
  [policy-docs]
    {:Statement (vec (map #(first (:Statement %)) policy-docs))})


(def default-policy (create-policy nil "Allow" "ec2.amazonaws.com" "sts:AssumeRole" nil))
(def ec2-describe (create-policy nil "Allow" nil "EC2:Describe*" "*"))
(def elb-describe (create-policy nil "Allow" nil "elasticloadbalancing:Describe*" "*"))
(def aws-sqs-read-only (create-policy nil "Allow" nil (list "sqs:Get*" "sqs:List*") "*"))
(def aws-s3-read-only (create-policy nil "Allow" nil (list "s3:Get*" "s3:List*") "*"))
(def aws-ddb-read-only (create-policy nil "Allow" nil (list "dynamodb:ListTables","dynamodb:DescribeTable") "*"))
(def aws-read-only (create-policy nil "Allow" nil aws-read-only-actions  "*"))

(defn s3-read-only [resources]
  (create-policy "s3ReposReadOnly" "Allow" nil (list "s3:GetObject" "s3:ListBucket") resources))

(defn s3-read-and-write [resources]
  (create-policy "s3ReposReadOnly" "Allow" nil (list "s3:GetObject" "s3:ListBucket" "s3:GetObjectAcl" "s3:PutObject") resources))

(defn s3-full-access [resources]
  (create-policy "s3ReposFullAccess" "Allow" nil "s3:*" resources))

(prn (join-policies [aws-sqs-read-only ec2-describe]) )
