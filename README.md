# Shuppet - Puppet for AWS configuration

## Intro

Shuppet keeps AWS configuration in a consistent state.

An application deployed in AWS needs at least:

  * an elastic load balancer
  * a security group for the elastic load balancer
  * another security group for the application
  * an IAM role to be able to access other AWS APIs

Shuppet creates all this configuration for you and ensures that it stays as you defined by checking it and updating it when required.
On top of this Shuppet also supports:

  * S3
  * DynamoDb

Shuppet also has a configuration per environment and ensures that it stays as defined.

All the configuration is stored in GIT repositories in source.nokia.com in the _shuppet_ project.

Shuppet uses Campfire to communicate configuration changes.

## Resources

All the resources are listed here <http://shuppet.brislabs.com:8080/resources>

## Configuration file

Shuppet configuration files are in Clojure, the only requirement is to return a Clojure map.
The configuration uses same naming as in Amazon's APIs, so you can refer to <http://aws.amazon.com/documentation> for help.

Examples:

  * environment configuration <https://source.nokia.com/projects/6302-shuppet/repositories/29637/entry/master/poke.clj>
  * application configuration <https://source.nokia.com/projects/6302-shuppet/repositories/29679/entry/master/test.clj>
