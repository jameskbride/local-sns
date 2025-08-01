package com.jameskbride.localsns.api

import com.google.gson.Gson
import com.jameskbride.localsns.BaseTest
import com.jameskbride.localsns.api.config.ConfigurationResponse
import com.jameskbride.localsns.api.config.ErrorResponse
import com.jameskbride.localsns.api.config.UpdateConfigurationRequest
import com.jameskbride.localsns.models.Configuration
import com.jameskbride.localsns.models.Subscription
import com.jameskbride.localsns.models.Topic
import com.jameskbride.localsns.verticles.MainVerticle
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import khttp.responses.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class ConfigurationApiTest : BaseTest() {

    private val gson = Gson()

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    @Test
    fun `it can get configuration via JSON API`(testContext: VertxTestContext) {
        val response = getConfigurationApi()
        
        assertEquals(200, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val configuration = gson.fromJson(response.text, ConfigurationResponse::class.java)
        assertTrue(configuration.version >= 1) // Allow existing version
        assertNotNull(configuration.timestamp)
        assertNotNull(configuration.topics)
        assertNotNull(configuration.subscriptions)
        
        testContext.completeNow()
    }

    @Test
    fun `it can update configuration via JSON API`(testContext: VertxTestContext) {
        val currentResponse = getConfigurationApi()
        val currentConfig = gson.fromJson(currentResponse.text, ConfigurationResponse::class.java)
        val initialVersion = currentConfig.version
        
        val topic = Topic(
            arn = "arn:aws:sns:us-east-1:123456789012:test-topic",
            name = "test-topic"
        )
        
        val subscription = Subscription(
            topicArn = "arn:aws:sns:us-east-1:123456789012:test-topic",
            arn = "arn:aws:sns:us-east-1:123456789012:test-subscription",
            owner = "123456789012",
            protocol = "http",
            endpoint = "http://example.com/webhook",
            subscriptionAttributes = mapOf("RawMessageDelivery" to "true")
        )
        
        val request = UpdateConfigurationRequest(
            topics = listOf(topic),
            subscriptions = listOf(subscription)
        )
        
        val response = updateConfigurationApi(request)
        
        assertEquals(200, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val configuration = gson.fromJson(response.text, ConfigurationResponse::class.java)
        assertEquals(initialVersion + 1, configuration.version) // Should increment from current version
        assertEquals(1, configuration.topics.size)
        assertEquals(1, configuration.subscriptions.size)
        assertEquals("test-topic", configuration.topics[0].name)
        assertEquals("http", configuration.subscriptions[0].protocol)
        
        testContext.completeNow()
    }

    @Test
    fun `it can partially update configuration - topics only`(testContext: VertxTestContext) {
        val initialTopic = Topic(
            arn = "arn:aws:sns:us-east-1:123456789012:initial-topic",
            name = "initial-topic"
        )
        
        val initialSubscription = Subscription(
            topicArn = "arn:aws:sns:us-east-1:123456789012:initial-topic",
            arn = "arn:aws:sns:us-east-1:123456789012:initial-subscription",
            owner = "123456789012",
            protocol = "sqs",
            endpoint = "http://localhost:9324/queue/test-queue"
        )
        
        val initialRequest = UpdateConfigurationRequest(
            topics = listOf(initialTopic),
            subscriptions = listOf(initialSubscription)
        )
        updateConfigurationApi(initialRequest)
        
        val newTopic = Topic(
            arn = "arn:aws:sns:us-east-1:123456789012:new-topic",
            name = "new-topic"
        )
        
        val partialRequest = UpdateConfigurationRequest(
            topics = listOf(newTopic),
            subscriptions = null
        )
        
        val response = updateConfigurationApi(partialRequest)
        
        assertEquals(200, response.statusCode)
        
        val configuration = gson.fromJson(response.text, ConfigurationResponse::class.java)
        assertEquals(1, configuration.topics.size)
        assertEquals("new-topic", configuration.topics[0].name)
        assertEquals(1, configuration.subscriptions.size)
        assertEquals("sqs", configuration.subscriptions[0].protocol)
        
        testContext.completeNow()
    }

    @Test
    fun `it can reset configuration via JSON API`(testContext: VertxTestContext) {
        val topic = Topic(
            arn = "arn:aws:sns:us-east-1:123456789012:test-topic",
            name = "test-topic"
        )
        
        val request = UpdateConfigurationRequest(topics = listOf(topic))
        updateConfigurationApi(request)
        
        val getResponse = getConfigurationApi()
        val beforeReset = gson.fromJson(getResponse.text, ConfigurationResponse::class.java)
        assertEquals(1, beforeReset.topics.size)
        
        val resetResponse = resetConfigurationApi()
        assertEquals(204, resetResponse.statusCode)
        
        val afterResetResponse = getConfigurationApi()
        val afterReset = gson.fromJson(afterResetResponse.text, ConfigurationResponse::class.java)
        assertEquals(1, afterReset.version)
        assertTrue(afterReset.topics.isEmpty())
        assertTrue(afterReset.subscriptions.isEmpty())
        
        testContext.completeNow()
    }

    @Test
    fun `it can create configuration backup via JSON API`(testContext: VertxTestContext) {
        val response = createConfigurationBackupApi()
        
        assertEquals(201, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val result = gson.fromJson(response.text, Map::class.java)
        assertEquals("Backup created successfully", result["message"])
        assertNotNull(result["backupPath"])
        assertNotNull(result["timestamp"])
        
        testContext.completeNow()
    }

    @Test
    fun `it returns error for invalid JSON in update request`(testContext: VertxTestContext) {
        val response = khttp.put(
            url = "${getBaseUrl()}/api/config",
            headers = mapOf("Content-Type" to "application/json"),
            data = "invalid json"
        )
        
        assertEquals(400, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val error = gson.fromJson(response.text, ErrorResponse::class.java)
        assertEquals("INVALID_JSON", error.error)
        assertTrue(error.message.contains("Invalid JSON"))
        
        testContext.completeNow()
    }

    @Test
    fun `it returns error for empty request body in update`(testContext: VertxTestContext) {
        val response = khttp.put(
            url = "${getBaseUrl()}/api/config",
            headers = mapOf("Content-Type" to "application/json"),
            data = ""
        )
        
        assertEquals(400, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val error = gson.fromJson(response.text, ErrorResponse::class.java)
        assertEquals("INVALID_REQUEST", error.error)
        assertEquals("Request body is required", error.message)
        
        testContext.completeNow()
    }

    private fun getConfigurationApi(): Response {
        return khttp.get("${getBaseUrl()}/api/config")
    }

    private fun updateConfigurationApi(request: UpdateConfigurationRequest): Response {
        return khttp.put(
            url = "${getBaseUrl()}/api/config",
            headers = mapOf("Content-Type" to "application/json"),
            data = gson.toJson(request)
        )
    }

    private fun resetConfigurationApi(): Response {
        return khttp.delete("${getBaseUrl()}/api/config")
    }

    private fun createConfigurationBackupApi(): Response {
        return khttp.post("${getBaseUrl()}/api/config/backup")
    }
}
