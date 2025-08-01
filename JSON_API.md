# Local SNS JSON REST API

This document describes the new JSON REST API endpoints for managing Topics and Subscriptions in local-sns.

## Topics API

### List Topics
- **GET** `/api/topics`
- **Response**: Array of topic objects
```json
[
  {
    "arn": "arn:aws:sns:us-east-1:123456789012:my-topic",
    "name": "my-topic"
  }
]
```

### Create Topic
- **POST** `/api/topics`
- **Request Body**:
```json
{
  "name": "my-topic"
}
```
- **Response**: Topic object (201 for new topic, 200 for existing topic)

### Get Topic
- **GET** `/api/topics/:arn`
- **Response**: Topic object

### Update Topic
- **PUT** `/api/topics/:arn`
- **Request Body**:
```json
{
  "name": "new-topic-name"
}
```
- **Response**: Updated topic object

### Delete Topic
- **DELETE** `/api/topics/:arn`
- **Response**: 204 No Content

## Subscriptions API

### List All Subscriptions
- **GET** `/api/subscriptions`
- **Response**: Array of subscription objects

### List Subscriptions by Topic
- **GET** `/api/topics/:topicArn/subscriptions`
- **Response**: Array of subscription objects for the specified topic

### Create Subscription
- **POST** `/api/subscriptions`
- **Request Body**:
```json
{
  "topicArn": "arn:aws:sns:us-east-1:123456789012:my-topic",
  "protocol": "http",
  "endpoint": "http://example.com/webhook",
  "attributes": {
    "RawMessageDelivery": "true",
    "FilterPolicy": "{\"key\": \"value\"}"
  }
}
```
- **Response**: Subscription object (201)

### Get Subscription
- **GET** `/api/subscriptions/:arn`
- **Response**: Subscription object

### Update Subscription Attributes
- **PUT** `/api/subscriptions/:arn`
- **Request Body**:
```json
{
  "attributes": {
    "RawMessageDelivery": "false",
    "FilterPolicy": "{\"updated\": \"policy\"}"
  }
}
```
- **Response**: Updated subscription object

### Delete Subscription
- **DELETE** `/api/subscriptions/:arn`
- **Response**: 204 No Content

## Data Models

### Topic
```json
{
  "arn": "arn:aws:sns:us-east-1:123456789012:topic-name",
  "name": "topic-name"
}
```

### Subscription
```json
{
  "arn": "arn:aws:sns:us-east-1:123456789012:topic-name:uuid",
  "owner": "123456789012",
  "topicArn": "arn:aws:sns:us-east-1:123456789012:topic-name",
  "protocol": "http",
  "endpoint": "http://example.com/webhook",
  "attributes": {
    "RawMessageDelivery": "true",
    "FilterPolicy": "{\"key\": \"value\"}"
  }
}
```

## Error Responses

All error responses use the following format:
```json
{
  "error": "ERROR_CODE",
  "message": "Human readable error message"
}
```

Common error codes:
- `MISSING_PARAMETER` - Required parameter is missing
- `INVALID_PARAMETER` - Parameter value is invalid
- `INVALID_JSON` - Request body contains invalid JSON
- `NOT_FOUND` - Resource not found
- `INTERNAL_ERROR` - Server error

## Publishing API

### Publish Message to Topic (by path)
- **POST** `/api/topics/:topicArn/publish`
- **Request Body**:
```json
{
  "message": "Hello, SNS!",
  "messageAttributes": {
    "attr1": {
      "name": "attr1",
      "value": "value1",
      "dataType": "String"
    }
  },
  "messageStructure": "json"
}
```
- **Response**: Publish result (200)
```json
{
  "messageId": "uuid-string",
  "topicArn": "arn:aws:sns:us-east-1:123456789012:my-topic"
}
```

### Publish Message (general endpoint)
- **POST** `/api/publish`
- **Request Body**:
```json
{
  "topicArn": "arn:aws:sns:us-east-1:123456789012:my-topic",
  "message": "Hello, SNS!",
  "messageAttributes": {
    "attr1": {
      "name": "attr1", 
      "value": "value1",
      "dataType": "String"
    }
  },
  "messageStructure": "json"
}
```
- **Alternative using targetArn**:
```json
{
  "targetArn": "arn:aws:sns:us-east-1:123456789012:my-topic",
  "message": "Hello, SNS!"
}
```
- **Response**: Publish result (200)

### JSON Message Structure
When using `"messageStructure": "json"`, the message should be a JSON string containing protocol-specific messages:
```json
{
  "message": "{\"default\": \"Default message\", \"sqs\": \"SQS specific message\", \"http\": \"HTTP specific message\"}",
  "messageStructure": "json"
}
```
The `"default"` key is required when using JSON message structure.

### Message Attribute Object
```json
{
  "name": "attribute-name",
  "value": "attribute-value", 
  "dataType": "String"
}
```

## Features

- **JSON Request/Response**: All endpoints use JSON format
- **Proper HTTP Status Codes**: 200, 201, 204, 400, 404, 500
- **Input Validation**: Validates topic names, ARNs, and subscription attributes
- **Error Handling**: Comprehensive error responses
- **Integration**: Updates existing Vert.x shared data maps
- **SQS Endpoint Conversion**: Automatically converts SQS HTTP URLs to Camel format
- **Attribute Validation**: Validates subscription attributes like RawMessageDelivery
- **Message Publishing**: Full support for publishing messages with attributes
- **JSON Message Structure**: Support for protocol-specific message formatting
- **Flexible Publishing**: Both topic-specific and general publish endpoints

## Compatibility

The JSON API runs alongside the existing AWS SNS XML API. Both APIs:
- Share the same underlying data storage (topics and subscriptions)
- Use the same message publishing engine
- Support the same protocols and features
- Can be used interchangeably

The JSON API provides a more modern, developer-friendly interface while maintaining full compatibility with the existing AWS SNS XML API.
