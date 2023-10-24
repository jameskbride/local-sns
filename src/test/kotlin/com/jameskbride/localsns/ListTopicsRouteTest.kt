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
class ListTopicsRouteTest: BaseTest() {

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    @Test
    fun `it succeeds when there are no topics`(testContext: VertxTestContext) {
        val response = listTopics()

        Assertions.assertEquals(200, response.statusCode)
        testContext.completeNow()
    }

    @Test
    fun `it return topics`(testContext: VertxTestContext) {
        val firstTopic = "first"
        val secondTopic = "first"

        createTopic(firstTopic)
        createTopic(secondTopic)

        val response = listTopics()

        Assertions.assertEquals(200, response.statusCode)
        Assertions.assertTrue(response.text.contains(firstTopic))
        Assertions.assertTrue(response.text.contains(secondTopic))
        testContext.completeNow()
    }
}