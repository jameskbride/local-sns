package com.jameskbride.localsns

import com.jameskbride.localsns.verticles.MainVerticle
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import khttp.responses.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class PublishBatchRouteTest: BaseTest() {
    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    @Test
    fun `it returns an error when both the TopicArn and TargetArn are missing`(testContext: VertxTestContext) {
        val response = publishBatch(topicArn = null)

        assertEquals(400, response.statusCode)
        testContext.completeNow()
    }

    @Test
    fun `it returns an error when the TopicArn does not exist`(testContext: VertxTestContext) {
        val topicArn = createValidArn("topic1")

        val response = publishBatch(topicArn = topicArn)

        assertEquals(404, response.statusCode)
        testContext.completeNow()
    }

    @Test
    fun `it returns an error when the TopicArn is not valid`(testContext: VertxTestContext) {
        val topicArn = "arn:aws:sns:us-east-1:123456789012:topic!"

        val response = publishBatch(topicArn = topicArn)

        assertEquals(400, response.statusCode)
        testContext.completeNow()
    }

    fun publishBatch(topicArn: String?): Response {
        val data = mutableMapOf(
            "Action" to "PublishBatch"
        )
        if (topicArn != null) { data["TopicArn"] = topicArn }

        return postFormData(data)
    }
}