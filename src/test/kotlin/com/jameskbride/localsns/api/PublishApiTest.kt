package com.jameskbride.localsns.api

import com.google.gson.Gson
import com.jameskbride.localsns.BaseTest
import com.jameskbride.localsns.models.MessageAttribute
import com.jameskbride.localsns.verticles.MainVerticle
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import khttp.responses.Response
import khttp.post
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class PublishApiTest : BaseTest() {

    private val gson = Gson()

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    private fun createTopicApi(name: String): Response {
        val baseUrl = getBaseUrl()
        val requestBody = mapOf("name" to name)
        return post("${baseUrl}api/topics", json = requestBody)
    }

    private fun publishMessageApi(topicArn: String, message: String, messageAttributes: Map<String, MessageAttribute>? = null, messageStructure: String? = null): Response {
        val baseUrl = getBaseUrl()
        val requestBody = mutableMapOf<String, Any>(
            "message" to message
        )
        messageAttributes?.let { requestBody["messageAttributes"] = it }
        messageStructure?.let { requestBody["messageStructure"] = it }
        
        return post("${baseUrl}api/topics/$topicArn/publish", json = requestBody)
    }

    private fun publishMessageGeneralApi(topicArn: String? = null, targetArn: String? = null, message: String, messageAttributes: Map<String, MessageAttribute>? = null, messageStructure: String? = null): Response {
        val baseUrl = getBaseUrl()
        val requestBody = mutableMapOf<String, Any>(
            "message" to message
        )
        topicArn?.let { requestBody["topicArn"] = it }
        targetArn?.let { requestBody["targetArn"] = it }
        messageAttributes?.let { requestBody["messageAttributes"] = it }
        messageStructure?.let { requestBody["messageStructure"] = it }
        
        return post("${baseUrl}api/publish", json = requestBody)
    }

    @Test
    fun `it can publish a basic message to a topic via path parameter`(testContext: VertxTestContext) {
        val topicResponse = createTopicApi("test-topic")
        Assertions.assertEquals(201, topicResponse.statusCode)
        
        val topicData = gson.fromJson(topicResponse.text, Map::class.java)
        val topicArn = topicData["arn"] as String
        
        val publishResponse = publishMessageApi(topicArn, "Hello, SNS!")
        
        Assertions.assertEquals(200, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData.containsKey("messageId"))
        Assertions.assertEquals(topicArn, responseData["topicArn"])
        
        testContext.completeNow()
    }

    @Test
    fun `it can publish a basic message via general publish endpoint`(testContext: VertxTestContext) {
        val topicResponse = createTopicApi("test-topic-2")
        Assertions.assertEquals(201, topicResponse.statusCode)
        
        val topicData = gson.fromJson(topicResponse.text, Map::class.java)
        val topicArn = topicData["arn"] as String
        
        val publishResponse = publishMessageGeneralApi(topicArn = topicArn, message = "Hello, SNS via general endpoint!")
        
        Assertions.assertEquals(200, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData.containsKey("messageId"))
        Assertions.assertEquals(topicArn, responseData["topicArn"])
        
        testContext.completeNow()
    }

    @Test
    fun `it can publish a message with message attributes`(testContext: VertxTestContext) {
        val topicResponse = createTopicApi("test-topic-attrs")
        Assertions.assertEquals(201, topicResponse.statusCode)
        
        val topicData = gson.fromJson(topicResponse.text, Map::class.java)
        val topicArn = topicData["arn"] as String
        
        val messageAttributes = mapOf(
            "attr1" to MessageAttribute("attr1", "value1", "String"),
            "attr2" to MessageAttribute("attr2", "value2", "String")
        )
        
        val publishResponse = publishMessageGeneralApi(topicArn = topicArn, message = "Hello with attributes!", messageAttributes = messageAttributes)
        
        Assertions.assertEquals(200, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData.containsKey("messageId"))
        Assertions.assertEquals(topicArn, responseData["topicArn"])
        
        testContext.completeNow()
    }

    @Test
    fun `it can publish a JSON structured message`(testContext: VertxTestContext) {
        val topicResponse = createTopicApi("test-topic-json")
        Assertions.assertEquals(201, topicResponse.statusCode)
        
        val topicData = gson.fromJson(topicResponse.text, Map::class.java)
        val topicArn = topicData["arn"] as String
        
        val jsonMessage = mapOf(
            "default" to "Default message",
            "sqs" to "SQS specific message",
            "http" to "HTTP specific message"
        )
        
        val publishResponse = publishMessageGeneralApi(topicArn = topicArn, message = gson.toJson(jsonMessage), messageStructure = "json")
        
        Assertions.assertEquals(200, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData.containsKey("messageId"))
        Assertions.assertEquals(topicArn, responseData["topicArn"])
        
        testContext.completeNow()
    }

    @Test
    fun `it can publish using targetArn instead of topicArn`(testContext: VertxTestContext) {
        val topicResponse = createTopicApi("test-topic-target")
        Assertions.assertEquals(201, topicResponse.statusCode)
        
        val topicData = gson.fromJson(topicResponse.text, Map::class.java)
        val topicArn = topicData["arn"] as String
        
        val publishResponse = publishMessageGeneralApi(targetArn = topicArn, message = "Hello with targetArn!")
        
        Assertions.assertEquals(200, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData.containsKey("messageId"))
        Assertions.assertEquals(topicArn, responseData["topicArn"])
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 400 when request body is empty`(testContext: VertxTestContext) {
        val baseUrl = getBaseUrl()
        val response = post("${baseUrl}api/publish", data = "")
        
        Assertions.assertEquals(400, response.statusCode)
        Assertions.assertEquals("application/json", response.headers["Content-Type"])
        
        val responseData = gson.fromJson(response.text, Map::class.java)
        Assertions.assertTrue(responseData["error"].toString().contains("Request body is required"))
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 400 when JSON is invalid`(testContext: VertxTestContext) {
        val baseUrl = getBaseUrl()
        val response = post("${baseUrl}api/publish", data = "invalid json")
        
        Assertions.assertEquals(400, response.statusCode)
        Assertions.assertEquals("application/json", response.headers["Content-Type"])
        
        val responseData = gson.fromJson(response.text, Map::class.java)
        Assertions.assertTrue(responseData["error"].toString().contains("Invalid JSON format"))
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 400 when neither topicArn nor targetArn is provided`(testContext: VertxTestContext) {
        val publishResponse = publishMessageGeneralApi(message = "Hello, SNS!")
        
        Assertions.assertEquals(400, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData["error"].toString().contains("Either topicArn or targetArn is required"))
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 400 when topicArn format is invalid`(testContext: VertxTestContext) {
        val publishResponse = publishMessageGeneralApi(topicArn = "invalid@#$%^arn", message = "Hello, SNS!")
        
        Assertions.assertEquals(400, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData["error"].toString().contains("Invalid TopicArn or TargetArn format"))
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 404 when topic does not exist`(testContext: VertxTestContext) {
        val publishResponse = publishMessageGeneralApi(topicArn = "arn:aws:sns:us-east-1:123456789012:non-existent-topic", message = "Hello, SNS!")
        
        Assertions.assertEquals(404, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData["error"].toString().contains("Topic not found"))
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 400 when message is empty`(testContext: VertxTestContext) {
        val topicResponse = createTopicApi("test-topic-empty-msg")
        Assertions.assertEquals(201, topicResponse.statusCode)
        
        val topicData = gson.fromJson(topicResponse.text, Map::class.java)
        val topicArn = topicData["arn"] as String
        
        val publishResponse = publishMessageGeneralApi(topicArn = topicArn, message = "")
        
        Assertions.assertEquals(400, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData["error"].toString().contains("Message cannot be empty"))
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 400 when messageStructure is invalid`(testContext: VertxTestContext) {
        val topicResponse = createTopicApi("test-topic-invalid-structure")
        Assertions.assertEquals(201, topicResponse.statusCode)
        
        val topicData = gson.fromJson(topicResponse.text, Map::class.java)
        val topicArn = topicData["arn"] as String
        
        val publishResponse = publishMessageGeneralApi(topicArn = topicArn, message = "Hello, SNS!", messageStructure = "xml")
        
        Assertions.assertEquals(400, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData["error"].toString().contains("MessageStructure must be 'json' if specified"))
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 400 when JSON structure is missing default attribute`(testContext: VertxTestContext) {
        val topicResponse = createTopicApi("test-topic-no-default")
        Assertions.assertEquals(201, topicResponse.statusCode)
        
        val topicData = gson.fromJson(topicResponse.text, Map::class.java)
        val topicArn = topicData["arn"] as String
        
        val jsonMessage = mapOf(
            "sqs" to "SQS specific message",
            "http" to "HTTP specific message"
        )
        
        val publishResponse = publishMessageGeneralApi(topicArn = topicArn, message = gson.toJson(jsonMessage), messageStructure = "json")
        
        Assertions.assertEquals(400, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData["error"].toString().contains("Attribute 'default' is required when messageStructure is 'json'"))
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 400 when message is invalid JSON but messageStructure is json`(testContext: VertxTestContext) {
        val topicResponse = createTopicApi("test-topic-invalid-json")
        Assertions.assertEquals(201, topicResponse.statusCode)
        
        val topicData = gson.fromJson(topicResponse.text, Map::class.java)
        val topicArn = topicData["arn"] as String
        
        val publishResponse = publishMessageGeneralApi(topicArn = topicArn, message = "invalid json string", messageStructure = "json")
        
        Assertions.assertEquals(400, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData["error"].toString().contains("Invalid JSON in message when messageStructure is 'json'"))
        
        testContext.completeNow()
    }
}
