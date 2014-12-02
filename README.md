# Mr. Pedantic - Keeping AWS infrastructure in sync

## Intro

Pedantic is able to create IAM roles (and their policies), security groups, load balancers, S3 buckets and DynamoDB tables in AWS. It regularly compares a configuration file with the current state of an AWS account and makes the changes required to ensure the state matches whatever configuration is present.

All the configuration is stored in Github repositories in under a chosen organisation. Each application has a repository which contains a file called `{application}.clj` which contains configuration to be applied to all accounts.

Pedantic can also use Campfire to communicate configuration changes.

## Resources

Once the application is up and running a list of resources which are available can be found at `http://{host}:{port}/resources`.

## Configuration file

Pedantic configuration files are written in Clojure, the only requirement is to return a Clojure map.
The configuration uses same naming as in Amazon's APIs, so you can refer to the [AWS documentation](http://aws.amazon.com/documentation) for help.