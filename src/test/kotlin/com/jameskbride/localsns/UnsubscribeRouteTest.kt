package com.jameskbride.localsns

import com.jameskbride.localsns.verticles.MainVerticle
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class UnsubscribeRouteTest: BaseTest() {

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    @Test
    fun `it returns an error when subscription arn is missing`(testContext: VertxTestContext) {
        val response = unsubscribe()

        Assertions.assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns an error when subscription arn does not exist`(testContext: VertxTestContext) {
        val response = unsubscribe(createValidArn("queue1"))

        Assertions.assertEquals(404, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns an error when subscription arn is invalid`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        subscribe(topic.arn, createEndpoint("endpoint"), "sqs")

        val response = unsubscribe("bad arn")

        Assertions.assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns an success when subscription exists`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val subscriptionResponse = subscribe(topic.arn, createEndpoint("endpoint"), "sqs")
        val subscriptionArn = getSubscriptionArnFromResponse(subscriptionResponse)

        val response = unsubscribe(subscriptionArn)

        Assertions.assertEquals(200, response.statusCode)

        val listSubscriptionsResponse = listSubscriptionsByTopic(topic.arn)
        Assertions.assertEquals(200, response.statusCode)

        //Make sure the subscription has been removed
        val doc = Jsoup.parse(listSubscriptionsResponse.text)
        val members = doc.select("member").toList()
        Assertions.assertTrue(members.none {
            val text = it.text()
            text.contains(subscriptionArn)
        })

        testContext.completeNow()
    }

    @Test
    fun `it indicates a db change`(vertx: Vertx, testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val subscriptionResponse = subscribe(topic.arn, createEndpoint("endpoint"), "sqs")
        val subscriptionArn = getSubscriptionArnFromResponse(subscriptionResponse)

        vertx.eventBus().consumer<String>("configChange") {
            testContext.completeNow()
        }

        unsubscribe(subscriptionArn)
    }
}