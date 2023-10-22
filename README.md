# Fake SNS

Fake Amazon Simple Notification Service (SNS) for testing. Supports:
- Create/List/Delete topics
- Subscribe endpoint
- Publish message
- Subscription persistence
- Integrations with (Fake-)SQS, File, HTTP, RabbitMQ, Slack, and Lambda

## Usage

### Docker

Based on the `openjdk:11.0.13-jre-slim` image. Run it with the command:
```
docker run -d -p 9911:9911 jameskbride/fake-sns
```

If you would like to keep the topic/subscription database in the current host folder:
```
docker run -d -p 9911:9911 -v "$PWD":/etc/sns jameskbride/fake-sns
```

#### Using aws-cli

The image has aws-cli preinstalled. For example, create a topic:
```
docker exec <CONTAINER_ID> sh -c 'aws sns --endpoint-url http://localhost:9911 create-topic --name test1'
```

### Jar

Download the latest release from https://github.com/jameskbride/fake-sns/releases and run:
```
DB_PATH=/tmp/db.json java -jar sns-0.2.0.jar
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

Example: `aws-sqs://{{env:QUEUE_NAME}}?amazonSQSEndpoint={{env:SQS_ENDPOINT}}&...`

### Example fake SQS integration:

Tested with [elasticmq](https://github.com/adamw/elasticmq).

```
docker run -d -p 9911:9911 -v "$PWD/example/config":/etc/sns jameskbride/fake-sns
```

## Development

### Unit tests

`./gradlew test`