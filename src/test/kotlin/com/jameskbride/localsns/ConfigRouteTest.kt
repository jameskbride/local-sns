package com.jameskbride.localsns;

import com.jameskbride.localsns.models.Configuration
import com.jameskbride.localsns.verticles.MainVerticle
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class ConfigRouteTest: BaseTest() {

    @BeforeEach
    fun setup(vertx:Vertx, testContext:VertxTestContext) {
        configureObjectMappers()
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    @Test
    fun `it returns the configuration`(testContext:VertxTestContext) {
        val response = getCurrentConfig()

        assertEquals(200, response.statusCode)
        val text = response.text
        val jsonConfig = io.vertx.core.json.JsonObject(text)
        assertTrue(jsonConfig.containsKey("topics"))
        assertTrue(jsonConfig.containsKey("subscriptions"))
        assertTrue(jsonConfig.containsKey("timestamp"))
        assertTrue(jsonConfig.containsKey("version"))

        jsonConfig.mapTo(Configuration::class.java)

        testContext.completeNow()
    }
}
