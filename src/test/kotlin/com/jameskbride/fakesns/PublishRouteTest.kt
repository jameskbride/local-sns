package com.jameskbride.fakesns

import com.jameskbride.fakesns.verticles.MainVerticle
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import khttp.responses.Response
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class PublishRouteTest: BaseTest() {

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    @Test
    fun `it returns an error when the TopicArn is missing`(testContext: VertxTestContext) {
        val response = publish(topicArn = null, message = "message")

        Assertions.assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns an error when the Message is missing`(testContext: VertxTestContext) {
        val response = publish(topicArn = null, message = null)

        Assertions.assertEquals(400, response.statusCode)

        testContext.completeNow()
    }

    @Test
    fun `it returns an error when the TopicArn does not exist`(testContext: VertxTestContext) {
        val topicArn = createValidArn("topic1")
        val response = publish(topicArn = topicArn, message = "message")

        Assertions.assertEquals(404, response.statusCode)

        testContext.completeNow()
    }

    fun publish(topicArn: String?, message: String?): Response {
        val data = mutableMapOf(
            "Action" to "Publish"
        )
        if (topicArn != null) { data["TopicArn"] = topicArn }
        if (message != null) { data["Message"] = message }

        return postFormData(data)
    }
}