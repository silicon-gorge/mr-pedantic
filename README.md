# Mr. Pedantic [![Build Status](https://travis-ci.org/mixradio/mr-pedantic.png)](https://travis-ci.org/mixradio/mr-pedantic)

## Intro

Pedantic is able to create IAM roles (and their policies), security groups, load balancers, S3 buckets and DynamoDB tables in AWS. It regularly compares a configuration file with the current state of an AWS account and makes the changes required to ensure the state matches whatever configuration is present.

You can see @neilprosser talking about the application in [this talk at Clojure eXchange 2014](https://skillsmatter.com/skillscasts/6057-herding-cattle-with-clojure-at-mixradio).

All the configuration is stored in Github repositories in under a chosen organisation. Each application has a repository which contains a file called `{application}.clj` which contains configuration to be applied to all accounts.

Pedantic can also use Campfire to communicate configuration changes.

## Running

```
lein run
```

or:

```
lein uberjar
java -jar target/pedantic.jar
```

## Configuration

There are a number of properties which are present in the `project.clj`. With the `lein run` option you can just amend the properties and they'll be made available to the application via [lein-environ](https://github.com/weavejester/environ). If using the `uberjar` option, you'll want to `export` them first:

```
export GITHUB_BASE_URL=http://github
# The above property will be recognised by environ as :github-base-url
java -jar pedantic.jar
```

## Resources

Once the application is up and running a list of resources which are available can be found at `http://{host}:{port}/resources`.

## Configuration file

Pedantic configuration files are written in Clojure, the only requirement is to return a Clojure map.
The configuration uses same naming as in Amazon's APIs, so you can refer to the [AWS documentation](http://aws.amazon.com/documentation) for help.

## License

Copyright © 2014 MixRadio

[mr-pedantic is released under the 3-clause license ("New BSD License" or "Modified BSD License")](https://github.com/mixradio/mr-pedantic/blob/master/LICENSE)
