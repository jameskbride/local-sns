package com.jameskbride.localsns

import com.google.gson.Gson
import com.jameskbride.localsns.models.Configuration
import com.jameskbride.localsns.models.Subscription
import com.jameskbride.localsns.models.Topic
import com.jameskbride.localsns.verticles.DatabaseVerticle
import com.jameskbride.localsns.verticles.MainVerticle
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(VertxExtension::class)
class SubscribeRouteTest : BaseTest() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setupClass(vertx: Vertx, testContext: VertxTestContext) {
            val mainFuture = vertx.deployVerticle(MainVerticle())
            val databaseFuture = vertx.deployVerticle(DatabaseVerticle())
            configureObjectMappers()
            Future.all (mainFuture, databaseFuture).onComplete {
                if (it.succeeded()) {
                    testContext.completeNow()
                } else {
                    testContext.failNow(it.cause())
                }
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
    fun `it can subscribe to a topic`(vertx: Vertx, testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val queueName = "queue1"
        val camelSqsEndpoint = createCamelSqsEndpoint(queueName)
        val config = ConfigFactory.load()
        vertx.eventBus().consumer<String>("configChangeComplete") {
            waitOnSubscription(vertx, testContext, config, topic) {
                it.topicArn == topic.arn && it.protocol == "sqs" && camelEndpointMatches(
                    it.endpoint,
                    queueName,
                    "000000000000",
                    "http://localhost:9324"
                )
            }
        }

        val response = subscribe(topic.arn, camelSqsEndpoint, "sqs")

        val subscriptionArn = getSubscriptionArnFromResponse(response)
        assertEquals(200, response.statusCode)
        assertTrue(subscriptionArn.isNotEmpty())

        testContext.completeNow()
    }

    @Test
    @Tag("skipForCI")
    fun `it creates a camel-compliant sqs endpoint subscription when subscribing to an http sqs queue`(vertx: Vertx, testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")

        val queueName = "queue1"
        val response = subscribe(topic.arn, createHttpSqsEndpoint(queueName, "http://localhost:9324"), "sqs")
        val config = ConfigFactory.load()
        vertx.eventBus().consumer<String>("configChangeComplete") {
            waitOnSubscription(vertx, testContext, config, topic) {
                it.topicArn == topic.arn && it.protocol == "sqs" && camelEndpointMatches(
                    it.endpoint,
                    queueName,
                    "000000000000",
                    "http://localhost:9324"
                )
            }
        }

        val subscriptionArn = getSubscriptionArnFromResponse(response)
        assertEquals(200, response.statusCode)
        assertTrue(subscriptionArn.isNotEmpty())
    }

    @Test
    @Tag("skipForCI")
    fun `it creates a camel-compliant sqs endpoint subscription when subscribing to an http sqs queue with no port`(vertx: Vertx, testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")

        val queueName = "queue1"
        val response = subscribe(topic.arn, createHttpSqsEndpoint(queueName, "https://sqs.us-east-1.amazonaws.com"), "sqs")
        val config = ConfigFactory.load()
        vertx.eventBus().consumer<String>("configChangeComplete") {
            waitOnSubscription(vertx, testContext, config, topic) {
                it.topicArn == topic.arn && it.protocol == "sqs" && camelEndpointMatches(
                    it.endpoint,
                    queueName,
                    "000000000000",
                    "https://sqs.us-east-1.amazonaws.com"
                )
            }
        }

        val subscriptionArn = getSubscriptionArnFromResponse(response)
        assertEquals(200, response.statusCode)
        assertTrue(subscriptionArn.isNotEmpty())
    }

    private fun waitOnSubscription(
        vertx: Vertx,
        testContext: VertxTestContext,
        config: Config,
        topic: Topic,
        subscriptionPredicate: (Subscription) -> Boolean
    ) {
        vertx.fileSystem()
            .readFile(getDbOutputPath(config))
            .onComplete { result ->
                val configFile = result.result().toString()
                val gson = Gson()
                val configuration = gson.fromJson(configFile, Configuration::class.java)
                assertEquals(configuration.version, 1)
                assertTrue(configuration.topics.contains(topic))
                val foundSubscription = configuration.subscriptions.find(subscriptionPredicate)
                if (foundSubscription == null) {
                    testContext.failNow(IllegalStateException("Subscription not found"))
                }

                testContext.completeNow()
            }
    }

    private fun camelEndpointMatches(endpoint: String?, queueName: String, accountId: String, uriEndpointOverride: String): Boolean {
        val uri = URI(endpoint)
        return uri.scheme == "aws2-sqs"
                && uri.host == queueName
                && uri.query.contains("queueOwnerAWSAccountId=$accountId")
                && uri.query.contains("uriEndpointOverride=$uriEndpointOverride")
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