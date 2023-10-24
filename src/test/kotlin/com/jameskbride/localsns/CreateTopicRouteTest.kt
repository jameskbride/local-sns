package com.jameskbride.localsns

import com.jameskbride.localsns.verticles.MainVerticle
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class CreateTopicRouteTest: BaseTest() {

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    @Test
    fun `it creates a new topic`(testContext: VertxTestContext) {
        val topic = "topicName"

        val response = createTopic(topic)

        Assertions.assertEquals(200, response.statusCode)
        Assertions.assertTrue(response.text.contains(topic))

        val listResponse = listTopics()
        Assertions.assertTrue(listResponse.text.contains(topic))
        testContext.completeNow()
    }

    @Test
    fun `it indicates a db change`(vertx: Vertx, testContext: VertxTestContext) {
        val topic = "topicName2"

        vertx.eventBus().consumer<String>("configChange") {
            testContext.completeNow()
        }

        createTopic(topic)
    }

    @Test
    fun `it returns an error when topic name is missing`(testContext: VertxTestContext) {
        val payload = mapOf(
            "Action" to "CreateTopic",
        )
        val response = postFormData(payload)

        Assertions.assertEquals(400, response.statusCode)
        testContext.completeNow()
    }

    @Test
    fun `it returns an error when topic name is invalid`(testContext: VertxTestContext) {
        val topic = "bad topic name"

        val response = createTopic(topic)

        Assertions.assertEquals(400, response.statusCode)
        testContext.completeNow()
    }
}