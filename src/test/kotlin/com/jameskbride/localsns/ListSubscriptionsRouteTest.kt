package com.jameskbride.localsns

import com.jameskbride.localsns.verticles.MainVerticle
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class ListSubscriptionsRouteTest: BaseTest() {

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    @Test
    fun `it returns success when there are no subscriptions `(testContext: VertxTestContext) {
        val response = listSubscriptions()
        Assertions.assertEquals(200, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns subscriptions when they exist `(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val endpoint = createCamelSqsEndpoint("queue1")
        val subscribeResponse1 = subscribe(topic.arn, endpoint, "sqs")
        val subscription1Arn = getSubscriptionArnFromResponse(subscribeResponse1)

        val response = listSubscriptions()
        Assertions.assertEquals(200, response.statusCode)

        val doc = Jsoup.parse(response.text)
        val members = doc.select("member").toList()
        assertTrue(members.any {
            val text = it.text()
            text.contains(subscription1Arn) && text.contains(topic.arn) && text
                .contains("sqs") && text.contains("queue1")
        })

        assertTrue(members.any {
            val text = it.text()
            text.contains("Endpoint") && text.contains(endpoint)
        })

        testContext.completeNow()
    }

    @Test
    fun `it returns lambda subscriptions when they exist `(testContext: VertxTestContext) {
        val topic = createTopicModel("topic2")
        val endpoint = createCamelSqsEndpoint("queue2")
        val subscribeResponse2 = subscribe(topic.arn, endpoint, "lambda")
        val subscription2Arn = getSubscriptionArnFromResponse(subscribeResponse2)

        val response = listSubscriptions()
        Assertions.assertEquals(200, response.statusCode)

        val doc = Jsoup.parse(response.text)
        val members = doc.select("member").toList()

        assertTrue(members.any {
            val text = it.text()
            text.contains(subscription2Arn) && text.contains(topic.arn) && text
                .contains("lambda") && text.contains("queue2")
        })

        testContext.completeNow()
    }
}