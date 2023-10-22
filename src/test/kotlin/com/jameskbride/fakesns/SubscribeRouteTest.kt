package com.jameskbride.fakesns

import com.jameskbride.fakesns.verticles.MainVerticle
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class SubscribeRouteTest : BaseTest() {

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    @Test
    fun `it returns an error when the topic arn is missing`(testContext: VertxTestContext) {
        val response = subscribe(null, createEndpoint("queue1"), "sqs")
        assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns an error when the protocol is missing`(testContext: VertxTestContext) {
        val topic = "topic1"
        val topicResponse = createTopic(topic)
        val topicArn = getTopicArnFromResponse(topicResponse)
        val response = subscribe(topicArn, createEndpoint("queue1"), null)
        assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns an error when topic arn is invalid`(testContext: VertxTestContext) {
        val topic = "bad topic"
        val response = subscribe(topic, createEndpoint("queue1"), "sqs")
        assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns an error when topic arn does not exist`(testContext: VertxTestContext) {
        val topic = "topic1"
        createTopic(topic)
        val response = subscribe(createValidArn("doesnotexist"), createEndpoint("queue1"), "sqs")
        assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it can subscribe to a topic`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")

        val response = subscribe(topic.arn, createEndpoint("queue1"), "sqs")

        val subscriptionArn = getSubscriptionArnFromResponse(response)
        assertEquals(200, response.statusCode)
        assertTrue(subscriptionArn.isNotEmpty())

        testContext.completeNow()
    }

    @Test
    fun `it indicates a db change`(vertx: Vertx, testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")

        vertx.eventBus().consumer<String>("configChange") {
            testContext.completeNow()
        }

        subscribe(topic.arn, createEndpoint("queue1"), "sqs")
    }

    @Test
    fun `it can subscribe with message attributes`(testContext: VertxTestContext) {
        val messageAttributes = mapOf(
            "FilterPolicy" to "the policy"
        )
        val topic = createTopicModel("topic1")

        val response = subscribe(topic.arn, createEndpoint("queue1"), "sqs", messageAttributes)
        val subscriptionArn = getSubscriptionArnFromResponse(response)
        assertEquals(200, response.statusCode)
        assertTrue(subscriptionArn.isNotEmpty())

        val getSubscriptionAttributesResponse = getSubscriptionAttributes(subscriptionArn)
        val entries = getEntries(getSubscriptionAttributesResponse)
        assertAttributeFound(entries, "FilterPolicy", "the policy")

        testContext.completeNow()
    }
}