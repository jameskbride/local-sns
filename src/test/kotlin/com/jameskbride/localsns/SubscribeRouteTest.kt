package com.jameskbride.localsns

import com.jameskbride.localsns.models.Configuration
import com.jameskbride.localsns.verticles.DatabaseVerticle
import com.jameskbride.localsns.verticles.MainVerticle
import com.typesafe.config.ConfigFactory
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class SubscribeRouteTest : BaseTest() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setupClass(vertx: Vertx, testContext: VertxTestContext) {
            val mainFuture = vertx.deployVerticle(MainVerticle())
            val databaseFuture = vertx.deployVerticle(DatabaseVerticle())
            configureObjectMappers()
            CompositeFuture.all(mainFuture, databaseFuture)
                .onComplete {
                    testContext.completeNow()
                }
        }
    }

    @Test
    fun `it returns an error when the topic arn is missing`(testContext: VertxTestContext) {
        val response = subscribe(null, createCamelSqsEndpoint("queue1"), "sqs")
        assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns an error when the protocol is missing`(testContext: VertxTestContext) {
        val topic = "topic1"
        val topicResponse = createTopic(topic)
        val topicArn = getTopicArnFromResponse(topicResponse)
        val response = subscribe(topicArn, createCamelSqsEndpoint("queue1"), null)
        assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns an error when topic arn is invalid`(testContext: VertxTestContext) {
        val topic = "bad topic"
        val response = subscribe(topic, createCamelSqsEndpoint("queue1"), "sqs")
        assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns an error when topic arn does not exist`(testContext: VertxTestContext) {
        val topic = "topic1"
        createTopic(topic)
        val response = subscribe(createValidArn("doesnotexist"), createCamelSqsEndpoint("queue1"), "sqs")
        assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it can subscribe to a topic`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")

        val response = subscribe(topic.arn, createCamelSqsEndpoint("queue1"), "sqs")

        val subscriptionArn = getSubscriptionArnFromResponse(response)
        assertEquals(200, response.statusCode)
        assertTrue(subscriptionArn.isNotEmpty())

        testContext.completeNow()
    }

    @Test
    fun `it creates a camel-compliant sqs endpoint subscription when subscribing to an http sqs queue`(vertx: Vertx, testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")

        val queueName = "queue1"
        val response = subscribe(topic.arn, createHttpSqsEndpoint(queueName), "sqs")
        val config = ConfigFactory.load()
        vertx.eventBus().consumer<String>("configChangeComplete") {
            vertx.fileSystem()
                .readFile(getDbOutputPath(config))
                .onComplete { result ->
                    val configFile = result.result()
                    val jsonConfig = JsonObject(configFile)

                    val configuration = jsonConfig.mapTo(Configuration::class.java)
                    assertEquals(configuration.version, 1)
                    assertTrue(configuration.topics.contains(topic))
                    val expectedEndpoint =
                        "aws2-sqs://$queueName?accessKey=xxx&secretKey=xxx&region=us-east-1&trustAllCertificates=true&overrideEndpoint=true&uriEndpointOverride=http://localhost:9324"
                    val foundSubscription = configuration.subscriptions
                        .find { it.topicArn == topic.arn && it.protocol == "sqs" && it.endpoint == expectedEndpoint }
                    if (foundSubscription == null) {
                        testContext.failNow(IllegalStateException("Subscription not found"))
                    }

                    testContext.completeNow()
                }
        }

        val subscriptionArn = getSubscriptionArnFromResponse(response)
        assertEquals(200, response.statusCode)
        assertTrue(subscriptionArn.isNotEmpty())
    }

    @Test
    fun `it indicates a db change`(vertx: Vertx, testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")

        vertx.eventBus().consumer<String>("configChange") {
            testContext.completeNow()
        }

        subscribe(topic.arn, createCamelSqsEndpoint("queue1"), "sqs")
    }

    @Test
    fun `it can subscribe with message attributes`(testContext: VertxTestContext) {
        val subscriptionAttributes = mapOf(
            "FilterPolicy" to "the policy"
        )
        val topic = createTopicModel("topic1")

        val response = subscribe(topic.arn, createCamelSqsEndpoint("queue1"), "sqs", subscriptionAttributes)
        val subscriptionArn = getSubscriptionArnFromResponse(response)
        assertEquals(200, response.statusCode)
        assertTrue(subscriptionArn.isNotEmpty())

        val getSubscriptionAttributesResponse = getSubscriptionAttributes(subscriptionArn)
        val entries = getEntries(getSubscriptionAttributesResponse)
        assertAttributeFound(entries, "FilterPolicy", "the policy")

        testContext.completeNow()
    }

    @Test
    fun `it returns an error when the RawMessageDelivery value is invalid`(testContext: VertxTestContext) {
        val subscriptionAttributes = mapOf(
            "RawMessageDelivery" to ""
        )
        val topic = createTopicModel("topic1")

        val response = subscribe(topic.arn, createCamelSqsEndpoint("queue1"), "sqs", subscriptionAttributes)
        getSubscriptionArnFromResponse(response)
        assertEquals(400, response.statusCode)

        testContext.completeNow()
    }
}