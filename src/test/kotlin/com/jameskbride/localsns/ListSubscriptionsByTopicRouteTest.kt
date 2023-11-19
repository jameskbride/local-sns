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
class ListSubscriptionsByTopicRouteTest: BaseTest() {

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    @Test
    fun `it returns an error when the topic arn is missing`(testContext: VertxTestContext) {
        val response = listSubscriptionsByTopic()

        Assertions.assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns an error when the topic arn does not exist`(testContext: VertxTestContext) {
        val topicArn = createValidArn("topic1")
        val response = listSubscriptionsByTopic(topicArn)

        Assertions.assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns an error when the topic arn is invalid`(testContext: VertxTestContext) {
        createTopic("anotherTopic")
        val topicArn = "invalid"
        val response = listSubscriptionsByTopic(topicArn)

        Assertions.assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns success when there are no subscriptions for a topic`(testContext: VertxTestContext) {
        val topic = createTopicModel("anotherTopic")
        val response = listSubscriptionsByTopic(topic.arn)

        Assertions.assertEquals(200, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns success when there are subscriptions for a topic`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val endpoint1 = createSqsEndpoint("queue1")
        val subscribeResponse1 = subscribe(topic.arn, endpoint1, "sqs")
        val subscription1Arn = getSubscriptionArnFromResponse(subscribeResponse1)

        val topic2 = createTopicModel("topic2")
        val endpoint2 = createSqsEndpoint("queue2")
        val subscribeResponse2 = subscribe(topic2.arn, endpoint2, "lambda")
        val subscription2Arn = getSubscriptionArnFromResponse(subscribeResponse2)

        val response = listSubscriptionsByTopic(topic.arn)
        Assertions.assertEquals(200, response.statusCode)

        val doc = Jsoup.parse(response.text)
        val members = doc.select("member").toList()

        Assertions.assertTrue(members.any {
            val text = it.text()
            text.contains(subscription1Arn) && text.contains(topic.arn) && text
                .contains("sqs") && text.contains("queue1")
        })

        //Only return subscriptions for the requested topic
        Assertions.assertTrue(members.none {
            val text = it.text()
            text.contains(subscription2Arn)
        })

        testContext.completeNow()
    }
}