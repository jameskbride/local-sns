package com.jameskbride.localsns

import com.jameskbride.localsns.verticles.MainVerticle
import io.vertx.core.Vertx
import io.vertx.core.json.Json
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import khttp.responses.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.Serializable

@ExtendWith(VertxExtension::class)
class PublishRouteTest: BaseTest() {

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    @Test
    fun `it returns an error when both the TopicArn and TargetArn are missing`(testContext: VertxTestContext) {
        val response = publish(topicArn = null, message = "message")

        assertEquals(400, response.statusCode)
        testContext.completeNow()
    }

    @Test
    fun `it returns an error when the Message is missing`(testContext: VertxTestContext) {
        val response = publish(topicArn = null, message = null)

        assertEquals(400, response.statusCode)
        testContext.completeNow()
    }

    @Test
    fun `it returns an error when the TopicArn does not exist`(testContext: VertxTestContext) {
        val topicArn = createValidArn("topic1")

        val response = publish(topicArn = topicArn, message = "message")

        assertEquals(404, response.statusCode)
        testContext.completeNow()
    }

    @Test
    fun `it returns an error when MessageStructure is not json`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        subscribe(topic.arn, createCamelSqsEndpoint("queue2"), "sqs")
        val message = "Hello, SNS!"

        val response = publish(topic.arn, message, messageStructure = "wrongValue")

        assertEquals(400, response.statusCode)
        testContext.completeNow()
    }

    @Test
    fun `it returns an error when MessageStructure is valid and Message does not contain a default key`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        subscribe(topic.arn, createCamelSqsEndpoint("queue2"), "sqs")
        data class BadMessage(val http:String): Serializable
        val badMessage = BadMessage(http = "some message")
        val message = Json.encode(badMessage)

        val response = publish(topic.arn, message, messageStructure = "json")

        assertEquals(400, response.statusCode)
        testContext.completeNow()
    }

    fun publish(topicArn: String?, message: String? = null, messageStructure: String? = null): Response {
        val data = mutableMapOf(
            "Action" to "Publish"
        )
        if (topicArn != null) { data["TopicArn"] = topicArn }
        if (message != null) { data["Message"] = message }
        if (messageStructure != null) { data["MessageStructure"] = messageStructure }

        return postFormData(data)
    }
}