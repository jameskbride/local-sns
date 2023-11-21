package com.jameskbride.localsns

import com.jameskbride.localsns.models.Topic
import com.jameskbride.localsns.verticles.MainVerticle
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.elasticmq.server.ElasticMQServer
import org.elasticmq.server.config.ElasticMQServerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import java.io.Serializable
import java.net.URI

private const val ELASTIC_MQ_SERVER_URL = "http://localhost:9324/000000000000"

fun createQueueUrl(queueName: String): String {
    return "$ELASTIC_MQ_SERVER_URL/$queueName"
}

@ExtendWith(VertxExtension::class)
class PublishRouteIntegrationTest: BaseTest() {

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
        router.clear()
    }

    companion object {
        private lateinit var router: Router
        private lateinit var httpServer: HttpServer
        private lateinit var server: ElasticMQServer
        private lateinit var sqsClient: SqsAsyncClient
        private lateinit var sqsSyncClient: SqsClient
        private lateinit var snsClient: SnsClient
        private lateinit var credentials: AwsBasicCredentials
        private lateinit var config: Config

        @JvmStatic
        @BeforeAll
        fun setupBeforeAll(vertx: Vertx) {
            config = ConfigFactory.load("elasticmq.conf")
            credentials = AwsBasicCredentials.create("xxx", "xxx")
            snsClient = SnsClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider { credentials }
                .endpointOverride(URI.create(getBaseUrl()))
                .build()

            sqsClient = SqsAsyncClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider { credentials }
                .endpointOverride(URI.create("http://localhost:9324"))
                .build()

            sqsSyncClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider { credentials }
                .endpointOverride(URI.create("http://localhost:9324"))
                .build()

            server =
                ElasticMQServer(ElasticMQServerConfig(config))
            server.start()

            httpServer = vertx.createHttpServer()
            router = Router.router(vertx)

            httpServer.requestHandler(router).listen(9933) { http ->
                if (http.succeeded()) {
                    println("Test HTTP server started on port 9933")
                } else {
                    println("Could not start the test HTTP server: ${http.cause()}")
                }
            }
        }
    }

    @Test
    fun `it can publish messages to sqs`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val queueName = "standard-publish"
        val endpoint = createQueue(queueName)
        subscribe(topic.arn, endpoint, "sqs")
        val message = "Hello, SNS!"

        val queueUrl = createQueueUrl(queueName)
        startReceivingMessages(queueUrl) { response ->
            val messages = response.messages()
            assertTrue(messages.any {
                val requestBody = it.body()
                val jsonBody = JsonObject(requestBody)
                message == jsonBody.getString("Message")
            })
            messages.forEach {
                sqsClient.deleteMessage(DeleteMessageRequest.builder().receiptHandle(it.receiptHandle()).build())
            }
            testContext.completeNow()
        }

        val request = publishRequest(topic, message)
        snsClient.publish(request)
    }

    @Test
    fun `it can publish raw messages to sqs`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val queueName = "raw-queue"
        val endpoint = createQueue(queueName)
        subscribe(topic.arn, endpoint, "sqs", mapOf("RawMessageDelivery" to "true"))
        val message = "Hello, SNS!"

        val queueUrl = createQueueUrl(queueName)
        startReceivingMessages(queueUrl) { response ->
            val messages = response.messages()
            assertTrue(messages.any {
                message == it.body()
            })
            messages.forEach {
                sqsClient.deleteMessage(DeleteMessageRequest.builder().receiptHandle(it.receiptHandle()).build())
            }
            testContext.completeNow()
        }

        val request = publishRequest(topic, message)
        snsClient.publish(request)
    }

    @Test
    fun `it can publish using the TargetArn`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val queueName = "target-arn-queue"
        createQueue(queueName)
        val endpoint = createSqsEndpoint(queueName)
        subscribe(topic.arn, endpoint, "sqs")
        val message = "Hello, SNS!"
        val request = publishRequest(topic, message, useTargetArn = true)

        snsClient.publish(request)

        val queueUrl = createQueueUrl(queueName)
        startReceivingMessages(queueUrl) { response ->
            val messages = response.messages()
            assertTrue(messages.any {
                val jsonBody = JsonObject(it.body())
                jsonBody.getString("Message") == message
            })
            messages.forEach {
                sqsClient.deleteMessage(DeleteMessageRequest.builder().receiptHandle(it.receiptHandle()).build())
            }
            testContext.completeNow()
        }
    }

    @Test
    fun `it can publish with message attributes`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val queueName = "with-attributes"
        val endpoint = createQueue(queueName)
        subscribe(topic.arn, endpoint, "sqs")
        val message = "Hello, SNS!"
        val messageAttributes = mapOf(
            "first" to "firstValue",
            "second" to "secondValue"
        )

        val request = publishRequest(topic, message, messageAttributes)

        snsClient.publish(request)

        val queueUrl = createQueueUrl("with-attributes")
        startReceivingMessages(queueUrl, messageAttributes.keys) { response ->
            val messages = response.messages()
            if (messages.any {
                    val jsonBody = JsonObject(it.body())
                    jsonBody.getString("Message") == message &&
                        messageHasAttribute(it, "first", "firstValue") &&
                        messageHasAttribute(it, "second", "secondValue")
            }) {
                testContext.completeNow()
            } else {
                testContext.failNow("Message not found")
            }
            messages.forEach {
                sqsClient.deleteMessage(DeleteMessageRequest.builder().receiptHandle(it.receiptHandle()).build())
            }
        }
    }

    private fun createQueueUrl(queueName: String): String {
        return "$ELASTIC_MQ_SERVER_URL/$queueName"
    }

    private fun createQueue(queueName: String): String {
        sqsSyncClient.createQueue {
            it.queueName(queueName)
            it.build()
        }

        return createSqsEndpoint(queueName)
    }

    @Test
    fun `it can publish to http endpoints`(testContext: VertxTestContext) {
        val message = "Hello, SNS!"
        // Define a POST route
        router.post("/testEndpoint").handler { routingContext ->
            val request = routingContext.request()
            request.bodyHandler { body ->
                val requestBody = body.toString("UTF-8")
                val jsonBody = JsonObject(requestBody)
                assertEquals(message, jsonBody.getString("Message"))
                testContext.completeNow()
            }

            val response = routingContext.response()
            response.setStatusCode(200).end("POST Request Received")
        }

        val topic = createTopicModel("httpTopic")
        subscribe(topic.arn, createHttpEndpoint("http://localhost:9933/testEndpoint", method="POST"), "http")

        val request = publishRequest(topic, message)

        snsClient.publish(request)
    }

    @Test
    fun `it can publish raw messages to http endpoints`(testContext: VertxTestContext) {
        val message = "Hello, SNS!"
        // Define a POST route
        router.post("/testEndpoint").handler { routingContext ->
            val request = routingContext.request()
            request.bodyHandler { body ->
                val requestBody = body.toString("UTF-8")
                assertEquals(message, requestBody)
                assertEquals(request.headers().get("x-amz-sns-rawdelivery"), "true")
                testContext.completeNow()
            }

            val response = routingContext.response()
            response.setStatusCode(200).end("POST Request Received")
        }

        val topic = createTopicModel("httpTopic")
        subscribe(
            topic.arn,
            createHttpEndpoint("http://localhost:9933/testEndpoint", method="POST"),
            "http",
            mapOf("RawMessageDelivery" to "true")
        )

        val request = publishRequest(topic, message)

        snsClient.publish(request)
    }

    @Test
    fun `it can publish to multiple subscriptions`(testContext: VertxTestContext) {
        val message = "Hello, SNS!"
        router.post("/testEndpoint1").handler { routingContext ->
            val request = routingContext.request()
            request.bodyHandler { body ->
                val requestBody = body.toString("UTF-8")
                val jsonBody = JsonObject(requestBody)
                assertEquals(message, jsonBody.getString("Message"))
                testContext.checkpoint()
            }

            val response = routingContext.response()
            response.setStatusCode(200).end("POST Request Received")
        }

        router.post("/testEndpoint2").handler { routingContext ->
            val request = routingContext.request()
            request.bodyHandler { body ->
                val requestBody = body.toString("UTF-8")
                val jsonBody = JsonObject(requestBody)
                assertEquals(message, jsonBody.getString("Message"))
                testContext.completeNow()
            }

            val response = routingContext.response()
            response.setStatusCode(200).end("POST Request Received")
        }

        val topic = createTopicModel("httpTopic")
        subscribe(topic.arn, createHttpEndpoint("http://localhost:9933/testEndpoint1", method="POST"), "http")
        subscribe(topic.arn, createHttpEndpoint("http://localhost:9933/testEndpoint2", method="POST"), "http")

        val request = publishRequest(topic, message)

        snsClient.publish(request)
    }

    @Test
    fun `an error with one endpoint does not stop publishing`(testContext: VertxTestContext) {
        val message = "Hello, SNS!"
        // Define a POST route
        router.post("/testEndpoint").handler { routingContext ->
            val request = routingContext.request()
            request.bodyHandler { body ->
                val requestBody = body.toString("UTF-8")
                val jsonBody = JsonObject(requestBody)
                assertEquals(message, jsonBody.getString("Message"))
                testContext.completeNow()
            }

            val response = routingContext.response()
            response.setStatusCode(200).end("POST Request Received")
        }

        val topic = createTopicModel("httpTopic")
        subscribe(topic.arn, createHttpEndpoint("http://invalidhost/", method="POST"), "http")
        subscribe(topic.arn, createHttpEndpoint("http://localhost:9933/testEndpoint", method="POST"), "http")

        val request = publishRequest(topic, message)

        snsClient.publish(request)
    }

    @Test
    fun `it can publish using MessageStructure`(testContext: VertxTestContext) {
        data class Message(val default: String, val http:String): Serializable
        data class JsonMessage(val key:String):Serializable
        val httpMessage = Json.encode(JsonMessage("hello http"))
        val message = Message("default message", httpMessage)

        // Define a POST route
        router.post("/testEndpoint").handler { routingContext ->
            val request = routingContext.request()
            request.bodyHandler { body ->
                val requestBody = body.toString("UTF-8")
                val jsonBody = JsonObject(requestBody)
                assertEquals(message.http, jsonBody.getString("Message"))
                testContext.completeNow()
            }

            val response = routingContext.response()
            response.setStatusCode(200).end("POST Request Received")
        }

        val topic = createTopicModel("topic1")
        subscribe(topic.arn, createHttpEndpoint("http://localhost:9933/testEndpoint", method="POST"), "http")

        val request = publishRequest(topic, Json.encode(message), messageStructure = "json")
        snsClient.publish(request)
    }

    @Test
    fun `it can publish raw http messages using MessageStructure`(testContext: VertxTestContext) {
        data class Message(val default: String, val http:String): Serializable
        data class JsonMessage(val key:String):Serializable
        val httpMessage = Json.encode(JsonMessage("hello http"))
        val message = Message("default message", httpMessage)

        // Define a POST route
        router.post("/testEndpoint").handler { routingContext ->
            val request = routingContext.request()
            request.bodyHandler {
                assertEquals(message.http, httpMessage)
                testContext.completeNow()
            }

            val response = routingContext.response()
            response.setStatusCode(200).end("POST Request Received")
        }

        val topic = createTopicModel("topic1")
        subscribe(
            topic.arn,
            createHttpEndpoint("http://localhost:9933/testEndpoint", method="POST"),
            "http",
            mapOf("RawMessageDelivery" to "true")
        )

        val request = publishRequest(topic, Json.encode(message), messageStructure = "json")
        snsClient.publish(request)
    }

    @Test
    fun `it can publish raw sqs messages using MessageStructure`(testContext: VertxTestContext) {
        data class Message(val default: String, val sqs:String): Serializable
        data class JsonMessage(val key:String):Serializable
        val jsonMessage = Json.encode(JsonMessage("hello sqs"))
        val message = Message("default message", jsonMessage)

        val queueName = "raw-message-structure-sqs"
        val endpoint = createQueue(queueName)
        val topic = createTopicModel("topic1")
        subscribe(
            topic.arn,
            endpoint,
            "sqs",
            mapOf("RawMessageDelivery" to "true")
        )

        val request = publishRequest(topic, Json.encode(message), messageStructure = "json")
        snsClient.publish(request)

        val queueUrl = createQueueUrl(queueName)
        startReceivingMessages(queueUrl) { response ->
            val messages = response.messages()
            if (messages.any {
                    jsonMessage == message.sqs
                }) {
                testContext.completeNow()
            } else {
                testContext.failNow("Message not found")
            }
            messages.forEach {
                sqsClient.deleteMessage(DeleteMessageRequest.builder().receiptHandle(it.receiptHandle()).build())
            }
        }
    }

    private fun messageHasAttribute(message: Message, key: String, value: String) =
        message.messageAttributes()[key]!!.stringValue() == value

    private fun startReceivingMessages(queueUrl: String, messageAttributes: Set<String> = setOf(), handleMessages: (ReceiveMessageResponse) -> Unit) {
        val receiveMessages = {
            sqsClient.receiveMessage(
                ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(10)
                    .messageAttributeNames(messageAttributes)
                    .build()
            ).thenAccept(handleMessages)
        }

        receiveMessages()
    }

    private fun publishRequest(
        topic: Topic,
        message: String,
        messageAttributes: Map<String, String> = mapOf(),
        useTargetArn:Boolean = false,
        messageStructure: String? = null,
    ): PublishRequest? {
        val attributes =
            messageAttributes.map { it.key to MessageAttributeValue.builder().stringValue(it.value).build() }.toMap()
        return PublishRequest.builder()
            .apply {
                if (useTargetArn) {
                    targetArn(topic.arn)
                } else {
                    topicArn(topic.arn)
                }
                message(message)
                if (messageStructure != null) {
                    messageStructure(messageStructure)
                }
                messageAttributes(attributes)
            }.build()

    }
}