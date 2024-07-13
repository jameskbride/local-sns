[![Docker Pulls](https://img.shields.io/docker/pulls/jameskbride/local-sns.svg?maxAge=2592000)](https://hub.docker.com/r/jameskbride/local-sns/)
[![CI/CD for local-sns main](https://github.com/jameskbride/local-sns/actions/workflows/main.yaml/badge.svg)](https://github.com/jameskbride/local-sns/actions/workflows/main.yaml)
![GitHub License](https://img.shields.io/github/license/jameskbride/local-sns)

# Local SNS
Fake Amazon Simple Notification Service (SNS) for local development. Supports:
- Create/List/Delete topics
- Subscribe/unsubscribe endpoints
- List subscriptions, list subscriptions by topic endpoints
- Get/Set subscription attribute endpoints
- Publish message
- Subscription persistence to file, including subscription attributes
- Subscription filtering (currently under development with some alpha features)
- Integrations with SQS, File, HTTP, RabbitMQ, Slack, and Lambda

## Usage

### Docker

Based on the `amazoncorretto:11.0.21-alpine` image. Run it with the command:
```
docker run -d -p 9911:9911 jameskbride/local-sns
```

If you would like to keep the topic/subscription database in the current host folder:
```
docker run -d -p 9911:9911 -v "$PWD":/etc/sns jameskbride/local-sns
```

#### Using aws-cli

The image has aws-cli preinstalled. For example, create a topic:
```
docker exec <CONTAINER_ID> sh -c 'aws sns --endpoint-url http://localhost:9911 create-topic --name test1'
```

### Jar

Download the latest release from https://github.com/jameskbride/local-sns/releases and run:
```
DB_PATH=/tmp/db.json java -jar local-sns.jar
```
Requires Java11.

## Configuration

Configuration can be set via environment variables:
- `DB_PATH` - path to subscription database file, default: `db.json`
- `DB_OUTPUT_PATH` - path that the subscription database file will be written to when updated, default: `db.json`
- `HTTP_INTERFACE` - interface to bind to, default: `0.0.0.0`
- `HTTP_PORT` - tcp port, default: `9911`
- `AWS_DEFAULT_REGION` - AWS region, default: `us-east-1`
- `AWS_ACCOUNT_ID` - AWS Account ID, default: `123456789012`

## Supported integrations

- Amazon SQS: `"aws2-sqs://queueName?accessKey=xxx&secretKey=xxx&region=us-east-1&trustAllCertificates=true&overrideEndpoint=true&uriEndpointOverride=http://host:<port>`
- RabbitMQ: `rabbitmq://exchangeName[?options]`
- HTTP: `http:hostName[:port][/resourceUri][?options]`
- File: `file://tmp?fileName=sns.txt`
- Slack: `slack:@username?token=someToken&webhookUrl=https://hooks.slack.com/services/aaa/bbb/ccc`
- Lambda: `aws2-lambda://functionName?accessKey=xxx&secretKey=xxx&region=us-east-1&trustAllCertificates=true&overrideEndpoint=true&uriEndpointOverride=http://host:<port>`

See [camel documentation](http://camel.apache.org/components.html) for more details.

Note: Environment variables can be used to specify URIs via `{{env:ENV_NAME}}`.

Example: `aws2-sqs://{{env:QUEUE_NAME}}?amazonSQSEndpoint={{env:SQS_ENDPOINT}}&...`

### Example fake SQS integration:

Tested with [elasticmq](https://github.com/adamw/elasticmq).

```
cd example
docker-compose up
```

## Features
### Subscriptions
#### Supported Subscription Attributes (See [SetSubscriptionAttributes](https://docs.aws.amazon.com/sns/latest/api/API_SetSubscriptionAttributes.html))
* `RawMessageDelivery` - NOTE: Messages sent via a Slack endpoint are always sent raw.
* `FilterPolicyScope` - Both `MessageBody` and `MessageAttributes` are supported (`MessageAttributes` is the default behavior).
* `FilterPolicy` - Currently under development, supported for `MessageBody` and `MessageAttributes` with some limitations: 

  | Feature                                                                                                                                                                                         | Supported                                                                                    |
  |-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
  | [Policy Complexity Constraints](https://docs.aws.amazon.com/sns/latest/dg/subscription-filter-policy-constraints.html#subscription-filter-policy-common-constraints)                            | No                                                                                           |
  | [Policy Constraints for MessageAttribute-based filtering](https://docs.aws.amazon.com/sns/latest/dg/subscription-filter-policy-constraints.html#subscription-filter-policy-payload-constraints) | Yes (only `String` and `Number`; `String.Array` is not currently supported)                  |
  | [Nested Constraints](https://docs.aws.amazon.com/sns/latest/dg/subscription-filter-policy-constraints.html#subscription-filter-policy-payload-constraints) for payload-based filtering          | No (local-sns only supports top-level attribute filtering for `MessageBody` filter policies) |
  | [Exact String match](https://docs.aws.amazon.com/sns/latest/dg/string-value-matching.html#string-exact-matching)                                                                                | Yes                                                                                          |
  | [String anything-but match](https://docs.aws.amazon.com/sns/latest/dg/string-value-matching.html#string-anything-but-matching)                                                                  | No                                                                                           |
  | [String prefix match](https://docs.aws.amazon.com/sns/latest/dg/string-value-matching.html#string-prefix-matching)                                                                              | No                                                                                           |
  | [String suffix match](https://docs.aws.amazon.com/sns/latest/dg/string-value-matching.html#ip-suffix-matching)                                                                                  | No                                                                                           |
  | [IP Address match](https://docs.aws.amazon.com/sns/latest/dg/string-value-matching.html#ip-address-matching)                                                                                     | No                                                                                           |
  | [Exact Number match](https://docs.aws.amazon.com/sns/latest/dg/numeric-value-matching.html#numeric-exact-matching)                                                                              | Yes                                                                                          |
  | [Numeric anything-but match](https://docs.aws.amazon.com/sns/latest/dg/numeric-value-matching.html#numeric-anything-but-matching)                                                               | No                                                                                           |
  | [Numeric Value Range match](https://docs.aws.amazon.com/sns/latest/dg/numeric-value-matching.html#numeric-value-range-matching)                                                                 | No                                                                                           |
  | [And Logic](https://docs.aws.amazon.com/sns/latest/dg/subscription-filter-policy-constraints.html#subscription-filter-policy-payload-constraints)                                               | Yes                                                                                          |
  | [Or Logic](https://docs.aws.amazon.com/sns/latest/dg/subscription-filter-policy-constraints.html#subscription-filter-policy-payload-constraints)                                                | Yes                                                                                          |
  | [Or Operator](https://docs.aws.amazon.com/sns/latest/dg/and-or-logic.html#or-operator)                                                                                                          | No                                                                                           |
  | [Key Matching](https://docs.aws.amazon.com/sns/latest/dg/attribute-key-matching.html)                                                                                                           | No                                                                                           |

### Additional Endpoints
* `GET /config` - Returns the current configuration of the local-sns instance.
* `GET /health` - Returns a 200 status code if the service is running.

## Development
This project uses Kotlin, [Vert.X](https://vertx.io), and [Apache Camel](https://camel.apache.org) for message routing.

Be sure to read the [SNS documentation](https://docs.aws.amazon.com/sns/latest/dg/welcome.html). The [API docs](https://docs.aws.amazon.com/sns/latest/api/API_Operations.html) in particular are useful.

It's also useful to run the equivalent `aws sns` CLI command with the `--debug` flag to better understand what the request and response payloads look like. 

You can also execute an `aws sns <command> --endpoint-url <local-sns url>` command to point the AWS CLI to the `local-sns` instance.

### Unit and Integration tests
`./gradlew test`

This command will run the unit and integration tests all together. The integration tests will create in-memory web servers that
run on ports 9922 and 9933, as well as starting elasticmq on port 9234.

## Thanks
Big thanks to Sergey Novikov ([@s12v](https://github.com/s12v)) for all the awesome work he did on https://github.com/s12v/sns. This project was largely inspired by Sergey's work,
and takes a lot of design ideas from the original sns project. 