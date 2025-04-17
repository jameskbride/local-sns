package com.jameskbride.localsns;

import com.google.gson.Gson
import com.google.gson.JsonObject
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
        val gson = Gson()
        val text = response.text
        val jsonConfig = gson.fromJson(text, JsonObject::class.java)
        assertTrue(jsonConfig.has("topics"))
        assertTrue(jsonConfig.has("subscriptions"))
        assertTrue(jsonConfig.has("timestamp"))
        assertTrue(jsonConfig.has("version"))
        gson.toJson(jsonConfig)

        testContext.completeNow()
    }
}
