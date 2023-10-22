package com.jameskbride.fakesns

import com.jameskbride.fakesns.verticles.MainVerticle
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class SetSubscriptionAttributesRouteTest: BaseTest() {
    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    @Test
    fun `it returns an error when SubscriptionArn is missing`(testContext: VertxTestContext) {
        val response = setSubscriptionAttributes(null, "name", "value")

        assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns an error when AttributeName is missing`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val subscriptionArn = getSubscriptionArnFromResponse(subscribe(topic.arn, createEndpoint("queue1"), "sqs"))

        val response = setSubscriptionAttributes(subscriptionArn, null, "value")

        assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns an error when AttributeValue is missing`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val subscriptionArn = getSubscriptionArnFromResponse(subscribe(topic.arn, createEndpoint("queue1"), "sqs"))

        val response = setSubscriptionAttributes(subscriptionArn, attributeName = null, "name")

        assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns an error when SubscriptionArn does not exist`(testContext: VertxTestContext) {
        val response = setSubscriptionAttributes(createValidArn("queue1"), attributeName = "name", "name")

        assertEquals(404, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns success when the message attribute is set`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val subscriptionArn = getSubscriptionArnFromResponse(subscribe(topic.arn, createEndpoint("queue1"), "sqs"))

        val response = setSubscriptionAttributes(subscriptionArn, "name", "value")

        assertEquals(200, response.statusCode)

        testContext.completeNow()
    }
}