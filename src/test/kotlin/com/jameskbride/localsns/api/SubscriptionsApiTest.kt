package com.jameskbride.localsns.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jameskbride.localsns.BaseTest
import com.jameskbride.localsns.api.subscriptions.*
import com.jameskbride.localsns.api.topics.CreateTopicRequest
import com.jameskbride.localsns.api.topics.TopicResponse
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
class SubscriptionsApiTest : BaseTest() {

    private val gson = Gson()

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    @Test
    fun `it can list subscriptions via JSON API`(testContext: VertxTestContext) {
        val response = getSubscriptionsApi()
        
        assertEquals(200, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val subscriptions: List<SubscriptionResponse> = gson.fromJson(response.text, object : TypeToken<List<SubscriptionResponse>>() {}.type)
        assertTrue(subscriptions.isEmpty())
        
        testContext.completeNow()
    }

    @Test
    fun `it can create a subscription via JSON API`(testContext: VertxTestContext) {
        val topicJson = """{"name": "test-topic"}"""
        val topicResponse = createTopicApi(topicJson)
        val topic = gson.fromJson(topicResponse.text, TopicResponse::class.java)
        
        val subscriptionJson = """{
            "topicArn": "${topic.arn}",
            "protocol": "http",
            "endpoint": "http://example.com/webhook"
        }"""
        val response = createSubscriptionApi(subscriptionJson)
        
        assertEquals(201, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val subscription = gson.fromJson(response.text, SubscriptionResponse::class.java)
        assertEquals(topic.arn, subscription.topicArn)
        assertEquals("http", subscription.protocol)
        assertEquals("http://example.com/webhook", subscription.endpoint)
        assertTrue(subscription.arn.contains(topic.arn))
        
        testContext.completeNow()
    }

    @Test
    fun `it can create a subscription with attributes via JSON API`(testContext: VertxTestContext) {
        val topicJson = """{"name": "test-topic-attrs"}"""
        val topicResponse = createTopicApi(topicJson)
        val topic = gson.fromJson(topicResponse.text, TopicResponse::class.java)
        
        val subscriptionJson = """{
            "topicArn": "${topic.arn}",
            "protocol": "http",
            "endpoint": "http://example.com/webhook",
            "attributes": {
                "RawMessageDelivery": "true",
                "FilterPolicy": "{\"key\": \"value\"}"
            }
        }"""
        val response = createSubscriptionApi(subscriptionJson)
        
        assertEquals(201, response.statusCode)
        val subscription = gson.fromJson(response.text, SubscriptionResponse::class.java)
        assertEquals("true", subscription.attributes["RawMessageDelivery"])
        assertEquals("""{"key": "value"}""", subscription.attributes["FilterPolicy"])
        
        testContext.completeNow()
    }

    @Test
    fun `it validates required fields when creating subscription via JSON API`(testContext: VertxTestContext) {
        val subscriptionJson = """{
            "topicArn": "",
            "protocol": "http",
            "endpoint": "http://example.com/webhook"
        }"""
        val response = createSubscriptionApi(subscriptionJson)
        
        assertEquals(400, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val error = gson.fromJson(response.text, com.jameskbride.localsns.api.subscriptions.ErrorResponse::class.java)
        assertEquals("MISSING_PARAMETER", error.error)
        
        testContext.completeNow()
    }

    @Test
    fun `it validates all required fields are present when creating subscription via JSON API`(testContext: VertxTestContext) {
        // Test missing topicArn
        val missingTopicArn = """{
            "protocol": "http",
            "endpoint": "http://example.com/webhook"
        }"""
        val response1 = createSubscriptionApi(missingTopicArn)
        assertEquals(400, response1.statusCode)
        
        // Test missing protocol
        val missingProtocol = """{
            "topicArn": "arn:aws:sns:us-east-1:000000000000:test-topic",
            "endpoint": "http://example.com/webhook"
        }"""
        val response2 = createSubscriptionApi(missingProtocol)
        assertEquals(400, response2.statusCode)
        
        // Test missing endpoint
        val missingEndpoint = """{
            "topicArn": "arn:aws:sns:us-east-1:000000000000:test-topic",
            "protocol": "http"
        }"""
        val response3 = createSubscriptionApi(missingEndpoint)
        assertEquals(400, response3.statusCode)
        
        testContext.completeNow()
    }

    @Test
    fun `it validates topic exists when creating subscription via JSON API`(testContext: VertxTestContext) {
        val subscriptionJson = """{
            "topicArn": "arn:aws:sns:us-east-1:000000000000:non-existent",
            "protocol": "http",
            "endpoint": "http://example.com/webhook"
        }"""
        val response = createSubscriptionApi(subscriptionJson)
        
        assertEquals(404, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val error = gson.fromJson(response.text, com.jameskbride.localsns.api.subscriptions.ErrorResponse::class.java)
        assertEquals("NOT_FOUND", error.error)
        
        testContext.completeNow()
    }

    @Test
    fun `it validates RawMessageDelivery attribute when creating subscription via JSON API`(testContext: VertxTestContext) {
        val topicJson = """{"name": "test-topic-validation"}"""
        val topicResponse = createTopicApi(topicJson)
        val topic = gson.fromJson(topicResponse.text, TopicResponse::class.java)
        
        val subscriptionJson = """{
            "topicArn": "${topic.arn}",
            "protocol": "http",
            "endpoint": "http://example.com/webhook",
            "attributes": {
                "RawMessageDelivery": "invalid"
            }
        }"""
        val response = createSubscriptionApi(subscriptionJson)
        
        assertEquals(400, response.statusCode)
        val error = gson.fromJson(response.text, com.jameskbride.localsns.api.subscriptions.ErrorResponse::class.java)
        assertEquals("INVALID_PARAMETER", error.error)
        assertTrue(error.message.contains("RawMessageDelivery"))
        
        testContext.completeNow()
    }

    @Test
    fun `it can get a specific subscription via JSON API`(testContext: VertxTestContext) {
        val (_, subscription) = createTopicAndSubscription()
        
        val response = getSubscriptionApi(subscription.arn)
        
        assertEquals(200, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val retrievedSubscription = gson.fromJson(response.text, SubscriptionResponse::class.java)
        assertEquals(subscription.arn, retrievedSubscription.arn)
        assertEquals(subscription.topicArn, retrievedSubscription.topicArn)
        assertEquals(subscription.protocol, retrievedSubscription.protocol)
        assertEquals(subscription.endpoint, retrievedSubscription.endpoint)
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 404 when getting non-existent subscription via JSON API`(testContext: VertxTestContext) {
        val fakeArn = "arn:aws:sns:us-east-1:000000000000:test-topic:non-existent-uuid"
        val response = getSubscriptionApi(fakeArn)
        
        assertEquals(404, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val error = gson.fromJson(response.text, com.jameskbride.localsns.api.subscriptions.ErrorResponse::class.java)
        assertEquals("NOT_FOUND", error.error)
        
        testContext.completeNow()
    }

    @Test
    fun `it can update subscription attributes via JSON API`(testContext: VertxTestContext) {
        val (_, subscription) = createTopicAndSubscription()
        
        val updateJson = """{
            "attributes": {
                "RawMessageDelivery": "true",
                "FilterPolicy": "{\"updated\": \"policy\"}"
            }
        }"""
        val response = updateSubscriptionApi(subscription.arn, updateJson)
        
        assertEquals(200, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val updatedSubscription = gson.fromJson(response.text, SubscriptionResponse::class.java)
        assertEquals("true", updatedSubscription.attributes["RawMessageDelivery"])
        assertEquals("""{"updated": "policy"}""", updatedSubscription.attributes["FilterPolicy"])
        
        testContext.completeNow()
    }

    @Test
    fun `it validates RawMessageDelivery when updating subscription via JSON API`(testContext: VertxTestContext) {
        val (_, subscription) = createTopicAndSubscription()
        
        val updateJson = """{
            "attributes": {
                "RawMessageDelivery": "invalid-value"
            }
        }"""
        val response = updateSubscriptionApi(subscription.arn, updateJson)
        
        assertEquals(400, response.statusCode)
        val error = gson.fromJson(response.text, com.jameskbride.localsns.api.subscriptions.ErrorResponse::class.java)
        assertEquals("INVALID_PARAMETER", error.error)
        assertTrue(error.message.contains("RawMessageDelivery"))
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 404 when updating non-existent subscription via JSON API`(testContext: VertxTestContext) {
        val fakeArn = "arn:aws:sns:us-east-1:000000000000:test-topic:non-existent-uuid"
        val updateJson = """{
            "attributes": {
                "RawMessageDelivery": "true"
            }
        }"""
        val response = updateSubscriptionApi(fakeArn, updateJson)
        
        assertEquals(404, response.statusCode)
        val error = gson.fromJson(response.text, com.jameskbride.localsns.api.subscriptions.ErrorResponse::class.java)
        assertEquals("NOT_FOUND", error.error)
        
        testContext.completeNow()
    }

    @Test
    fun `it can delete a subscription via JSON API`(testContext: VertxTestContext) {
        val (_, subscription) = createTopicAndSubscription()
        
        val response = deleteSubscriptionApi(subscription.arn)
        
        assertEquals(204, response.statusCode)
        
        val getResponse = getSubscriptionApi(subscription.arn)
        assertEquals(404, getResponse.statusCode)
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 404 when deleting non-existent subscription via JSON API`(testContext: VertxTestContext) {
        val fakeArn = "arn:aws:sns:us-east-1:000000000000:test-topic:non-existent-uuid"
        val response = deleteSubscriptionApi(fakeArn)
        
        assertEquals(404, response.statusCode)
        val error = gson.fromJson(response.text, com.jameskbride.localsns.api.subscriptions.ErrorResponse::class.java)
        assertEquals("NOT_FOUND", error.error)
        
        testContext.completeNow()
    }

    @Test
    fun `it can list subscriptions by topic via JSON API`(testContext: VertxTestContext) {
        val topicJson = """{"name": "test-topic-list"}"""
        val topicResponse = createTopicApi(topicJson)
        val topic = gson.fromJson(topicResponse.text, TopicResponse::class.java)
        
        val sub1Json = """{
            "topicArn": "${topic.arn}",
            "protocol": "http",
            "endpoint": "http://example1.com/webhook"
        }"""
        val sub2Json = """{
            "topicArn": "${topic.arn}",
            "protocol": "http",
            "endpoint": "http://example2.com/webhook"
        }"""
        
        val sub1Response = createSubscriptionApi(sub1Json)
        val sub2Response = createSubscriptionApi(sub2Json)
        val sub1 = gson.fromJson(sub1Response.text, SubscriptionResponse::class.java)
        val sub2 = gson.fromJson(sub2Response.text, SubscriptionResponse::class.java)
        
        val response = getSubscriptionsByTopicApi(topic.arn)
        
        assertEquals(200, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val subscriptions: List<SubscriptionResponse> = gson.fromJson(response.text, object : TypeToken<List<SubscriptionResponse>>() {}.type)
        assertEquals(2, subscriptions.size)
        assertTrue(subscriptions.any { it.arn == sub1.arn })
        assertTrue(subscriptions.any { it.arn == sub2.arn })
        
        testContext.completeNow()
    }

    @Test
    fun `it shows created subscriptions in global list via JSON API`(testContext: VertxTestContext) {
        val (topic1, sub1) = createTopicAndSubscription("topic1")
        val (topic2, sub2) = createTopicAndSubscription("topic2")
        
        val response = getSubscriptionsApi()
        assertEquals(200, response.statusCode)
        
        val subscriptions: List<SubscriptionResponse> = gson.fromJson(response.text, object : TypeToken<List<SubscriptionResponse>>() {}.type)
        assertEquals(2, subscriptions.size)
        assertTrue(subscriptions.any { it.arn == sub1.arn && it.topicArn == topic1.arn })
        assertTrue(subscriptions.any { it.arn == sub2.arn && it.topicArn == topic2.arn })
        
        testContext.completeNow()
    }

    @Test
    fun `it handles SQS endpoint conversion for subscriptions via JSON API`(testContext: VertxTestContext) {
        val topicJson = """{"name": "test-topic-sqs"}"""
        val topicResponse = createTopicApi(topicJson)
        val topic = gson.fromJson(topicResponse.text, TopicResponse::class.java)
        
        val subscriptionJson = """{
            "topicArn": "${topic.arn}",
            "protocol": "sqs",
            "endpoint": "https://sqs.us-east-1.amazonaws.com/123456789012/my-queue"
        }"""
        val response = createSubscriptionApi(subscriptionJson)
        
        assertEquals(201, response.statusCode)
        val subscription = gson.fromJson(response.text, SubscriptionResponse::class.java)
        
        assertTrue(subscription.endpoint!!.startsWith("aws2-sqs://my-queue"))
        assertTrue(subscription.endpoint!!.contains("queueOwnerAWSAccountId=123456789012"))
        
        testContext.completeNow()
    }

    @Test
    fun `it handles subscription creation without attributes field via JSON API`(testContext: VertxTestContext) {
        val topicJson = """{"name": "test-topic-no-attrs"}"""
        val topicResponse = createTopicApi(topicJson)
        val topic = gson.fromJson(topicResponse.text, TopicResponse::class.java)
        
        // Test without attributes field at all (real-world scenario)
        val subscriptionJson = """{
            "topicArn": "${topic.arn}",
            "protocol": "http",
            "endpoint": "http://example.com/webhook"
        }"""
        val response = createSubscriptionApi(subscriptionJson)
        
        assertEquals(201, response.statusCode)
        val subscription = gson.fromJson(response.text, SubscriptionResponse::class.java)
        assertEquals(topic.arn, subscription.topicArn)
        assertEquals("http", subscription.protocol)
        assertEquals("http://example.com/webhook", subscription.endpoint)
        
        testContext.completeNow()
    }

    @Test
    fun `it handles subscription creation with empty attributes via JSON API`(testContext: VertxTestContext) {
        val topicJson = """{"name": "test-topic-empty-attrs"}"""
        val topicResponse = createTopicApi(topicJson)
        val topic = gson.fromJson(topicResponse.text, TopicResponse::class.java)
        
        // Test with empty attributes object (real-world scenario)
        val subscriptionJson = """{
            "topicArn": "${topic.arn}",
            "protocol": "http",
            "endpoint": "http://example.com/webhook",
            "attributes": {}
        }"""
        val response = createSubscriptionApi(subscriptionJson)
        
        assertEquals(201, response.statusCode)
        val subscription = gson.fromJson(response.text, SubscriptionResponse::class.java)
        assertEquals(topic.arn, subscription.topicArn)
        assertTrue(subscription.attributes.isEmpty())
        
        testContext.completeNow()
    }

    @Test
    fun `it handles malformed JSON for subscription creation`(testContext: VertxTestContext) {
        // Test with truly malformed JSON (incomplete JSON)
        val malformedJson = """{
            "topicArn": "arn:aws:sns:us-east-1:000000000000:test",
            "protocol": "http"
            // Missing closing brace and endpoint field
        """
        val response = createSubscriptionApi(malformedJson)
        
        assertEquals(400, response.statusCode)
        
        testContext.completeNow()
    }

    // Helper methods
    private fun createTopicAndSubscription(topicName: String = "test-topic"): Pair<TopicResponse, SubscriptionResponse> {
        val topicJson = """{"name": "$topicName"}"""
        val topicResponse = createTopicApi(topicJson)
        val topic = gson.fromJson(topicResponse.text, TopicResponse::class.java)
        
        val subscriptionJson = """{
            "topicArn": "${topic.arn}",
            "protocol": "http",
            "endpoint": "http://example.com/webhook"
        }"""
        val subscriptionResponse = createSubscriptionApi(subscriptionJson)
        val subscription = gson.fromJson(subscriptionResponse.text, SubscriptionResponse::class.java)
        
        return Pair(topic, subscription)
    }

    private fun getSubscriptionsApi(): Response {
        return khttp.get("${getBaseUrl()}/api/subscriptions")
    }

    private fun getSubscriptionsByTopicApi(topicArn: String): Response {
        return khttp.get("${getBaseUrl()}/api/topics/$topicArn/subscriptions")
    }

    private fun createSubscriptionApi(jsonRequest: String): Response {
        return khttp.post(
            url = "${getBaseUrl()}/api/subscriptions",
            headers = mapOf("Content-Type" to "application/json"),
            data = jsonRequest
        )
    }

    private fun getSubscriptionApi(arn: String): Response {
        return khttp.get("${getBaseUrl()}/api/subscriptions/$arn")
    }

    private fun updateSubscriptionApi(arn: String, jsonRequest: String): Response {
        return khttp.put(
            url = "${getBaseUrl()}/api/subscriptions/$arn",
            headers = mapOf("Content-Type" to "application/json"),
            data = jsonRequest
        )
    }

    private fun deleteSubscriptionApi(arn: String): Response {
        return khttp.delete("${getBaseUrl()}/api/subscriptions/$arn")
    }

    private fun createTopicApi(jsonRequest: String): Response {
        return khttp.post(
            url = "${getBaseUrl()}/api/topics",
            headers = mapOf("Content-Type" to "application/json"),
            data = jsonRequest
        )
    }
}
