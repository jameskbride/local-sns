package com.jameskbride.localsns

import com.jameskbride.localsns.models.PublishBatchRequestEntry
import com.jameskbride.localsns.models.Topic
import com.jameskbride.localsns.verticles.MainVerticle
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.elasticmq.server.ElasticMQServer
import org.elasticmq.server.config.ElasticMQServerConfig
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.PublishBatchRequest
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.*
import java.net.URI
import java.util.*

private const val ELASTIC_MQ_SERVER_URL = "http://localhost:9326/000000000000"

@ExtendWith(VertxExtension::class)
class PublishBatchRouteIntegrationTest: BaseTest() {

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
        router.clear()
        deleteAllQueues()
    }

    private fun deleteAllQueues() {
        val listQueuesResponse = sqsSyncClient.listQueues()
        listQueuesResponse.queueUrls().forEach { queueUrl ->
            sqsSyncClient.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl).build())
        }
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
            config = ConfigFactory.load("elasticmq-publish-batch-integration-tests.conf")
            credentials = AwsBasicCredentials.create("xxx", "xxx")
            snsClient = SnsClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider { credentials }
                .endpointOverride(URI.create(getBaseUrl()))
                .build()

            sqsClient = SqsAsyncClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider { credentials }
                .endpointOverride(URI.create("http://localhost:9326"))
                .build()

            sqsSyncClient = SqsClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider { credentials }
                .endpointOverride(URI.create("http://localhost:9326"))
                .build()

            server =
                ElasticMQServer(ElasticMQServerConfig(config))
            server.start()

            httpServer = vertx.createHttpServer()
            router = Router.router(vertx)

            httpServer.requestHandler(router).listen(9934) { http ->
                if (http.succeeded()) {
                    println("Test HTTP server started on port 9934")
                } else {
                    println("Could not start the test HTTP server: ${http.cause()}")
                }
            }
        }
    }

    @Test
    fun `it can publish batch messages to sqs`(testContext: VertxTestContext) {
        val topic = createTopicModel(UUID.randomUUID().toString())
        val queueName = UUID.randomUUID().toString()
        val endpoint = createQueue(queueName)
        subscribe(topic.arn, endpoint, "sqs")

        val queueUrl = createQueueUrl(queueName)
        startReceivingMessages(queueUrl) { response ->
            val messages = response.messages()
            Assertions.assertTrue(messages.isNotEmpty(), "No messages received from SQS")
            messages.forEach {
                sqsClient.deleteMessage(DeleteMessageRequest.builder().receiptHandle(it.receiptHandle()).build())
            }
            testContext.completeNow()
        }

        val publishBatchRequestEntries = mutableListOf<PublishBatchRequestEntry>()
        publishBatchRequestEntries.add(
            PublishBatchRequestEntry(
                UUID.randomUUID().toString(),
                "Hello, SNS!",
            )
        )

        val request = publishBatchRequest(topic, publishBatchRequestEntries)
        snsClient.publishBatch(request)
    }

    private fun createQueueUrl(queueName: String): String {
        return "$ELASTIC_MQ_SERVER_URL/$queueName"
    }

    private fun createQueue(queueName: String): String {
        sqsSyncClient.createQueue {
            it.queueName(queueName)
            it.build()
        }

        return createCamelSqsEndpoint(queueName, 9326)
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

    private fun publishBatchRequest(
        topic: Topic,
        batchEntries: List<PublishBatchRequestEntry>,
    ): PublishBatchRequest? {
        val publishBatchRequestEntries = batchEntries.map { batchEntry ->
            software.amazon.awssdk.services.sns.model.PublishBatchRequestEntry.builder()
                .id(batchEntry.id)
                .message(batchEntry.message)
                .build()
        }

        val publishBatchRequest = PublishBatchRequest.builder()
            .publishBatchRequestEntries(publishBatchRequestEntries)
            .topicArn(topic.arn)
            .build()

        return publishBatchRequest
    }
}