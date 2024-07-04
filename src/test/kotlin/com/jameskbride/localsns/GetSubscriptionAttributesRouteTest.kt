package com.jameskbride.localsns

import com.jameskbride.localsns.verticles.MainVerticle
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class GetSubscriptionAttributesRouteTest: BaseTest() {

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    @Test
    fun `it returns an error when SubscriptionArn is not passed`(testContext: VertxTestContext) {
        val response = getSubscriptionAttributes(null)

        assertEquals(400, response.statusCode)
        testContext.completeNow()
    }

    @Test
    fun `it returns an error when SubscriptionArn does not exist`(testContext: VertxTestContext) {
        val response = getSubscriptionAttributes(createValidArn("queue1"))

        assertEquals(404, response.statusCode)
        testContext.completeNow()
    }

    @Test
    fun `it returns an error when SubscriptionArn is invalid`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        subscribe(topicArn = topic.arn, createCamelSqsEndpoint("queue1"), "sqs")
        val response = getSubscriptionAttributes("bad arn")

        assertEquals(400, response.statusCode)
        testContext.completeNow()
    }

    @Test
    fun `it can return default attribute values`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val endpoint = createCamelSqsEndpoint("queue1")
        val subscriptionArn = getSubscriptionArnFromResponse((subscribe(topicArn = topic.arn, endpoint, "sqs")))

        val response = getSubscriptionAttributes(subscriptionArn)
        assertEquals(200, response.statusCode)

        val doc = Jsoup.parse(response.text)
        val entries = doc.select("entry").toList()

        assertAttributeFound(entries, "SubscriptionArn", subscriptionArn)
        assertAttributeFound(entries, "TopicArn", topic.arn)
        assertAttributeFound(entries, "Owner", "")
        assertAttributeFound(entries, "Endpoint", endpoint)
        assertAttributeFound(entries, "Protocol", "sqs")
        assertAttributeFound(entries, "PendingConfirmation", "false")
        assertAttributeFound(entries, "SubscriptionPrincipal", "")
        assertAttributeFound(entries, "ConfirmationWasAuthenticated", "false")
        assertAttributeFound(entries, "RawMessageDelivery", "false")

        testContext.completeNow()
    }

    @Test
    fun `it can return overridden attributes`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val endpoint = createCamelSqsEndpoint("queue1")
        val subscriptionArn = getSubscriptionArnFromResponse((subscribe(topicArn = topic.arn, endpoint, "sqs")))

        setSubscriptionAttributes(subscriptionArn, "RawMessageDelivery", "true")

        val response = getSubscriptionAttributes(subscriptionArn)
        assertEquals(200, response.statusCode)

        val entries = getEntries(response)
        assertAttributeFound(entries, "RawMessageDelivery", "true")

        testContext.completeNow()
    }

    @Test
    fun `it can return arbitrary attributes`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val endpoint = createCamelSqsEndpoint("queue1")
        val subscriptionArn = getSubscriptionArnFromResponse((subscribe(topicArn = topic.arn, endpoint, "sqs")))

        setSubscriptionAttributes(subscriptionArn, "status", "sending")

        val response = getSubscriptionAttributes(subscriptionArn)
        assertEquals(200, response.statusCode)

        val entries = getEntries(response)
        assertAttributeFound(entries, "status", "sending")

        testContext.completeNow()
    }
}