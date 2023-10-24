package com.jameskbride.localsns;

import com.jameskbride.localsns.verticles.MainVerticle
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class HealthRouteTest:BaseTest() {
    @BeforeEach
    fun setup(vertx:Vertx, testContext:VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    @Test
    fun `it returns ok`(testContext: VertxTestContext) {
        val response = khttp.get("${getBaseUrl()}/health")

        assertEquals("ok", response.text)
        testContext.completeNow()
    }
}
