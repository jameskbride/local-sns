package com.jameskbride.localsns.api

import com.google.gson.Gson
import com.jameskbride.localsns.BaseTest
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

    private fun publishMessageApi(topicArn: String, jsonBody: String): Response {
        val baseUrl = getBaseUrl()
        return post("${baseUrl}api/topics/$topicArn/publish", data = jsonBody, headers = mapOf("Content-Type" to "application/json"))
    }

    private fun publishMessageGeneralApi(jsonBody: String): Response {
        val baseUrl = getBaseUrl()
        return post("${baseUrl}api/publish", data = jsonBody, headers = mapOf("Content-Type" to "application/json"))
    }

    @Test
    fun `it can publish a basic message to a topic via path parameter`(testContext: VertxTestContext) {
        val topicResponse = createTopicApi("test-topic")
        Assertions.assertEquals(201, topicResponse.statusCode)
        
        val topicData = gson.fromJson(topicResponse.text, Map::class.java)
        val topicArn = topicData["arn"] as String
        
        val jsonBody = """{"message": "Hello, SNS!"}"""
        val publishResponse = publishMessageApi(topicArn, jsonBody)
        
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
        
        val jsonBody = """{"topicArn": "$topicArn", "message": "Hello, SNS via general endpoint!"}"""
        val publishResponse = publishMessageGeneralApi(jsonBody)
        
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
        
        val jsonBody = """{
            "topicArn": "$topicArn",
            "message": "Hello with attributes!",
            "messageAttributes": {
                "attr1": {
                    "name": "attr1",
                    "value": "value1",
                    "type": "String"
                },
                "attr2": {
                    "name": "attr2",
                    "value": "value2",
                    "type": "String"
                }
            }
        }"""
        
        val publishResponse = publishMessageGeneralApi(jsonBody)
        
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
        
        val jsonBody = """{
            "topicArn": "$topicArn",
            "message": "{\"default\":\"Default message\",\"sqs\":\"SQS specific message\",\"http\":\"HTTP specific message\"}",
            "messageStructure": "json"
        }"""
        
        val publishResponse = publishMessageGeneralApi(jsonBody)
        
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
        
        val jsonBody = """{"targetArn": "$topicArn", "message": "Hello with targetArn!"}"""
        val publishResponse = publishMessageGeneralApi(jsonBody)
        
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
        val jsonBody = """{"message": "Hello, SNS!"}"""
        val publishResponse = publishMessageGeneralApi(jsonBody)
        
        Assertions.assertEquals(400, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData["error"].toString().contains("Either topicArn or targetArn is required"))
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 400 when both topicArn and targetArn fields are missing from JSON`(testContext: VertxTestContext) {
        // Test with a JSON that only contains message field - no topicArn or targetArn fields at all
        val jsonBody = """{"message": "Hello, SNS without any ARN fields!"}"""
        val publishResponse = publishMessageGeneralApi(jsonBody)
        
        Assertions.assertEquals(400, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData["error"].toString().contains("Either topicArn or targetArn is required"))
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 400 when topicArn and targetArn are null in JSON`(testContext: VertxTestContext) {
        // Test with explicit null values for both fields
        val jsonBody = """{"topicArn": null, "targetArn": null, "message": "Hello, SNS with null ARNs!"}"""
        val publishResponse = publishMessageGeneralApi(jsonBody)
        
        Assertions.assertEquals(400, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData["error"].toString().contains("Either topicArn or targetArn is required"))
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 400 when JSON contains only empty object`(testContext: VertxTestContext) {
        // Test with completely empty JSON object
        val jsonBody = """{}"""
        val publishResponse = publishMessageGeneralApi(jsonBody)
        
        Assertions.assertEquals(400, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        // This should fail for missing message field first, or missing ARN fields
        Assertions.assertTrue(
            responseData["error"].toString().contains("Either topicArn or targetArn is required") ||
            responseData["error"].toString().contains("Message") ||
            responseData["error"].toString().contains("message")
        )
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 400 when topicArn format is invalid`(testContext: VertxTestContext) {
        val jsonBody = """{"topicArn": "invalid@#$%^arn", "message": "Hello, SNS!"}"""
        val publishResponse = publishMessageGeneralApi(jsonBody)
        
        Assertions.assertEquals(400, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData["error"].toString().contains("Invalid TopicArn or TargetArn format"))
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 404 when topic does not exist`(testContext: VertxTestContext) {
        val jsonBody = """{"topicArn": "arn:aws:sns:us-east-1:123456789012:non-existent-topic", "message": "Hello, SNS!"}"""
        val publishResponse = publishMessageGeneralApi(jsonBody)
        
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
        
        val jsonBody = """{"topicArn": "$topicArn", "message": ""}"""
        val publishResponse = publishMessageGeneralApi(jsonBody)
        
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
        
        val jsonBody = """{"topicArn": "$topicArn", "message": "Hello, SNS!", "messageStructure": "xml"}"""
        val publishResponse = publishMessageGeneralApi(jsonBody)
        
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
        
        val jsonBody = """{
            "topicArn": "$topicArn",
            "message": "{\"sqs\":\"SQS specific message\",\"http\":\"HTTP specific message\"}",
            "messageStructure": "json"
        }"""
        
        val publishResponse = publishMessageGeneralApi(jsonBody)
        
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
        
        val jsonBody = """{"topicArn": "$topicArn", "message": "invalid json string", "messageStructure": "json"}"""
        val publishResponse = publishMessageGeneralApi(jsonBody)
        
        Assertions.assertEquals(400, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData["error"].toString().contains("Invalid JSON in message when messageStructure is 'json'"))
        
        testContext.completeNow()
    }

    @Test
    fun `it can publish a message without messageAttributes field in JSON`(testContext: VertxTestContext) {
        val topicResponse = createTopicApi("test-topic-no-attrs")
        Assertions.assertEquals(201, topicResponse.statusCode)
        
        val topicData = gson.fromJson(topicResponse.text, Map::class.java)
        val topicArn = topicData["arn"] as String
        
        // Test without messageAttributes field at all (not even null)
        val jsonBody = """{"topicArn": "$topicArn", "message": "Hello without attributes field!"}"""
        val publishResponse = publishMessageGeneralApi(jsonBody)
        
        Assertions.assertEquals(200, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData.containsKey("messageId"))
        Assertions.assertEquals(topicArn, responseData["topicArn"])
        
        testContext.completeNow()
    }

    @Test
    fun `it can publish a message without messageStructure field in JSON`(testContext: VertxTestContext) {
        val topicResponse = createTopicApi("test-topic-no-structure")
        Assertions.assertEquals(201, topicResponse.statusCode)
        
        val topicData = gson.fromJson(topicResponse.text, Map::class.java)
        val topicArn = topicData["arn"] as String
        
        // Test without messageStructure field at all (not even null)
        val jsonBody = """{"topicArn": "$topicArn", "message": "Hello without messageStructure field!"}"""
        val publishResponse = publishMessageGeneralApi(jsonBody)
        
        Assertions.assertEquals(200, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData.containsKey("messageId"))
        Assertions.assertEquals(topicArn, responseData["topicArn"])
        
        testContext.completeNow()
    }

    @Test
    fun `it can publish a message with minimal JSON containing only required fields`(testContext: VertxTestContext) {
        val topicResponse = createTopicApi("test-topic-minimal")
        Assertions.assertEquals(201, topicResponse.statusCode)
        
        val topicData = gson.fromJson(topicResponse.text, Map::class.java)
        val topicArn = topicData["arn"] as String
        
        // Test with only the minimal required fields
        val jsonBody = """{"topicArn": "$topicArn", "message": "Minimal message!"}"""
        val publishResponse = publishMessageGeneralApi(jsonBody)
        
        Assertions.assertEquals(200, publishResponse.statusCode)
        Assertions.assertEquals("application/json", publishResponse.headers["Content-Type"])
        
        val responseData = gson.fromJson(publishResponse.text, Map::class.java)
        Assertions.assertTrue(responseData.containsKey("messageId"))
        Assertions.assertEquals(topicArn, responseData["topicArn"])
        
        testContext.completeNow()
    }
}
