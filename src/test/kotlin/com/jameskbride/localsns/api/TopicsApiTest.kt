package com.jameskbride.localsns.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jameskbride.localsns.BaseTest
import com.jameskbride.localsns.api.topics.CreateTopicRequest
import com.jameskbride.localsns.api.topics.ErrorResponse
import com.jameskbride.localsns.api.topics.TopicResponse
import com.jameskbride.localsns.api.topics.UpdateTopicRequest
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
class TopicsApiTest : BaseTest() {

    private val gson = Gson()

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    @Test
    fun `it can list topics via JSON API`(testContext: VertxTestContext) {
        val response = getTopicsApi()
        
        assertEquals(200, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val topics: List<TopicResponse> = gson.fromJson(response.text, object : TypeToken<List<TopicResponse>>() {}.type)
        assertTrue(topics.isEmpty())
        
        testContext.completeNow()
    }

    @Test
    fun `it can create a topic via JSON API`(testContext: VertxTestContext) {
        val request = CreateTopicRequest("test-topic")
        val response = createTopicApi(request)
        
        assertEquals(201, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val topic = gson.fromJson(response.text, TopicResponse::class.java)
        assertEquals("test-topic", topic.name)
        assertTrue(topic.arn.contains("test-topic"))
        
        testContext.completeNow()
    }

    @Test
    fun `it returns existing topic when creating duplicate via JSON API`(testContext: VertxTestContext) {
        val request = CreateTopicRequest("duplicate-topic")
        val firstResponse = createTopicApi(request)
        val secondResponse = createTopicApi(request)
        
        assertEquals(201, firstResponse.statusCode)
        assertEquals(200, secondResponse.statusCode)
        
        val firstTopic = gson.fromJson(firstResponse.text, TopicResponse::class.java)
        val secondTopic = gson.fromJson(secondResponse.text, TopicResponse::class.java)
        
        assertEquals(firstTopic.arn, secondTopic.arn)
        assertEquals(firstTopic.name, secondTopic.name)
        
        testContext.completeNow()
    }

    @Test
    fun `it validates topic name when creating via JSON API`(testContext: VertxTestContext) {
        val request = CreateTopicRequest("")
        val response = createTopicApi(request)
        
        assertEquals(400, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val error = gson.fromJson(response.text, ErrorResponse::class.java)
        assertEquals("MISSING_PARAMETER", error.error)
        
        testContext.completeNow()
    }

    @Test
    fun `it validates invalid JSON when creating topic`(testContext: VertxTestContext) {
        val response = khttp.post(
            url = "${getBaseUrl()}/api/topics",
            headers = mapOf("Content-Type" to "application/json"),
            data = "{ invalid json }"
        )
        
        assertEquals(400, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val error = gson.fromJson(response.text, ErrorResponse::class.java)
        assertEquals("INVALID_JSON", error.error)
        
        testContext.completeNow()
    }

    @Test
    fun `it can get a specific topic via JSON API`(testContext: VertxTestContext) {
        // First create a topic
        val request = CreateTopicRequest("get-topic-test")
        val createResponse = createTopicApi(request)
        val createdTopic = gson.fromJson(createResponse.text, TopicResponse::class.java)
        
        // Then get it by ARN
        val response = getTopicApi(createdTopic.arn)
        
        assertEquals(200, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val topic = gson.fromJson(response.text, TopicResponse::class.java)
        assertEquals(createdTopic.arn, topic.arn)
        assertEquals(createdTopic.name, topic.name)
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 404 when getting non-existent topic via JSON API`(testContext: VertxTestContext) {
        val fakeArn = "arn:aws:sns:us-east-1:000000000000:non-existent"
        val response = getTopicApi(fakeArn)
        
        assertEquals(404, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val error = gson.fromJson(response.text, ErrorResponse::class.java)
        assertEquals("NOT_FOUND", error.error)
        
        testContext.completeNow()
    }

    @Test
    fun `it can update a topic via JSON API`(testContext: VertxTestContext) {
        // First create a topic
        val createRequest = CreateTopicRequest("update-topic-test")
        val createResponse = createTopicApi(createRequest)
        val createdTopic = gson.fromJson(createResponse.text, TopicResponse::class.java)
        
        // Then update it
        val updateRequest = UpdateTopicRequest("updated-topic-name")
        val response = updateTopicApi(createdTopic.arn, updateRequest)
        
        assertEquals(200, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val updatedTopic = gson.fromJson(response.text, TopicResponse::class.java)
        assertEquals("updated-topic-name", updatedTopic.name)
        assertTrue(updatedTopic.arn.contains("updated-topic-name"))
        assertNotEquals(createdTopic.arn, updatedTopic.arn)
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 404 when updating non-existent topic via JSON API`(testContext: VertxTestContext) {
        val fakeArn = "arn:aws:sns:us-east-1:000000000000:non-existent"
        val updateRequest = UpdateTopicRequest("new-name")
        val response = updateTopicApi(fakeArn, updateRequest)
        
        assertEquals(404, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val error = gson.fromJson(response.text, ErrorResponse::class.java)
        assertEquals("NOT_FOUND", error.error)
        
        testContext.completeNow()
    }

    @Test
    fun `it can delete a topic via JSON API`(testContext: VertxTestContext) {
        // First create a topic
        val createRequest = CreateTopicRequest("delete-topic-test")
        val createResponse = createTopicApi(createRequest)
        val createdTopic = gson.fromJson(createResponse.text, TopicResponse::class.java)
        
        // Then delete it
        val response = deleteTopicApi(createdTopic.arn)
        
        assertEquals(204, response.statusCode)
        
        // Verify it's gone
        val getResponse = getTopicApi(createdTopic.arn)
        assertEquals(404, getResponse.statusCode)
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 404 when deleting non-existent topic via JSON API`(testContext: VertxTestContext) {
        val fakeArn = "arn:aws:sns:us-east-1:000000000000:non-existent"
        val response = deleteTopicApi(fakeArn)
        
        assertEquals(404, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val error = gson.fromJson(response.text, ErrorResponse::class.java)
        assertEquals("NOT_FOUND", error.error)
        
        testContext.completeNow()
    }

    @Test
    fun `it shows created topics in list via JSON API`(testContext: VertxTestContext) {
        // Create a few topics
        val topic1 = gson.fromJson(createTopicApi(CreateTopicRequest("list-topic-1")).text, TopicResponse::class.java)
        val topic2 = gson.fromJson(createTopicApi(CreateTopicRequest("list-topic-2")).text, TopicResponse::class.java)
        
        // List topics
        val response = getTopicsApi()
        assertEquals(200, response.statusCode)
        
        val topics: List<TopicResponse> = gson.fromJson(response.text, object : TypeToken<List<TopicResponse>>() {}.type)
        assertEquals(2, topics.size)
        assertTrue(topics.any { it.arn == topic1.arn && it.name == topic1.name })
        assertTrue(topics.any { it.arn == topic2.arn && it.name == topic2.name })
        
        testContext.completeNow()
    }

    // Helper methods for API calls
    private fun getTopicsApi(): Response {
        return khttp.get("${getBaseUrl()}/api/topics")
    }

    private fun createTopicApi(request: CreateTopicRequest): Response {
        return khttp.post(
            url = "${getBaseUrl()}/api/topics",
            headers = mapOf("Content-Type" to "application/json"),
            data = gson.toJson(request)
        )
    }

    private fun getTopicApi(arn: String): Response {
        return khttp.get("${getBaseUrl()}/api/topics/$arn")
    }

    private fun updateTopicApi(arn: String, request: UpdateTopicRequest): Response {
        return khttp.put(
            url = "${getBaseUrl()}/api/topics/$arn",
            headers = mapOf("Content-Type" to "application/json"),
            data = gson.toJson(request)
        )
    }

    private fun deleteTopicApi(arn: String): Response {
        return khttp.delete("${getBaseUrl()}/api/topics/$arn")
    }
}
