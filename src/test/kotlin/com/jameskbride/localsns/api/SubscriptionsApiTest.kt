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
        val topicRequest = CreateTopicRequest("test-topic")
        val topicResponse = createTopicApi(topicRequest)
        val topic = gson.fromJson(topicResponse.text, TopicResponse::class.java)
        
        val subscriptionRequest = CreateSubscriptionRequest(
            topicArn = topic.arn,
            protocol = "http",
            endpoint = "http://example.com/webhook"
        )
        val response = createSubscriptionApi(subscriptionRequest)
        
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
        val topicRequest = CreateTopicRequest("test-topic-attrs")
        val topicResponse = createTopicApi(topicRequest)
        val topic = gson.fromJson(topicResponse.text, TopicResponse::class.java)
        
        val subscriptionRequest = CreateSubscriptionRequest(
            topicArn = topic.arn,
            protocol = "http",
            endpoint = "http://example.com/webhook",
            attributes = mapOf(
                "RawMessageDelivery" to "true",
                "FilterPolicy" to """{"key": "value"}"""
            )
        )
        val response = createSubscriptionApi(subscriptionRequest)
        
        assertEquals(201, response.statusCode)
        val subscription = gson.fromJson(response.text, SubscriptionResponse::class.java)
        assertEquals("true", subscription.attributes["RawMessageDelivery"])
        assertEquals("""{"key": "value"}""", subscription.attributes["FilterPolicy"])
        
        testContext.completeNow()
    }

    @Test
    fun `it validates required fields when creating subscription via JSON API`(testContext: VertxTestContext) {
        val subscriptionRequest = CreateSubscriptionRequest(
            topicArn = "",
            protocol = "http",
            endpoint = "http://example.com/webhook"
        )
        val response = createSubscriptionApi(subscriptionRequest)
        
        assertEquals(400, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val error = gson.fromJson(response.text, com.jameskbride.localsns.api.subscriptions.ErrorResponse::class.java)
        assertEquals("MISSING_PARAMETER", error.error)
        
        testContext.completeNow()
    }

    @Test
    fun `it validates topic exists when creating subscription via JSON API`(testContext: VertxTestContext) {
        val subscriptionRequest = CreateSubscriptionRequest(
            topicArn = "arn:aws:sns:us-east-1:000000000000:non-existent",
            protocol = "http",
            endpoint = "http://example.com/webhook"
        )
        val response = createSubscriptionApi(subscriptionRequest)
        
        assertEquals(404, response.statusCode)
        assertEquals("application/json", response.headers["Content-Type"])
        
        val error = gson.fromJson(response.text, com.jameskbride.localsns.api.subscriptions.ErrorResponse::class.java)
        assertEquals("NOT_FOUND", error.error)
        
        testContext.completeNow()
    }

    @Test
    fun `it validates RawMessageDelivery attribute when creating subscription via JSON API`(testContext: VertxTestContext) {
        val topicRequest = CreateTopicRequest("test-topic-validation")
        val topicResponse = createTopicApi(topicRequest)
        val topic = gson.fromJson(topicResponse.text, TopicResponse::class.java)
        
        val subscriptionRequest = CreateSubscriptionRequest(
            topicArn = topic.arn,
            protocol = "http",
            endpoint = "http://example.com/webhook",
            attributes = mapOf("RawMessageDelivery" to "invalid")
        )
        val response = createSubscriptionApi(subscriptionRequest)
        
        assertEquals(400, response.statusCode)
        val error = gson.fromJson(response.text, com.jameskbride.localsns.api.subscriptions.ErrorResponse::class.java)
        assertEquals("INVALID_PARAMETER", error.error)
        assertTrue(error.message.contains("RawMessageDelivery"))
        
        testContext.completeNow()
    }

    @Test
    fun `it can get a specific subscription via JSON API`(testContext: VertxTestContext) {
        val (topic, subscription) = createTopicAndSubscription()
        
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
        val (topic, subscription) = createTopicAndSubscription()
        
        val updateRequest = UpdateSubscriptionRequest(
            attributes = mapOf(
                "RawMessageDelivery" to "true",
                "FilterPolicy" to """{"updated": "policy"}"""
            )
        )
        val response = updateSubscriptionApi(subscription.arn, updateRequest)
        
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
        
        val updateRequest = UpdateSubscriptionRequest(
            attributes = mapOf("RawMessageDelivery" to "invalid-value")
        )
        val response = updateSubscriptionApi(subscription.arn, updateRequest)
        
        assertEquals(400, response.statusCode)
        val error = gson.fromJson(response.text, com.jameskbride.localsns.api.subscriptions.ErrorResponse::class.java)
        assertEquals("INVALID_PARAMETER", error.error)
        assertTrue(error.message.contains("RawMessageDelivery"))
        
        testContext.completeNow()
    }

    @Test
    fun `it returns 404 when updating non-existent subscription via JSON API`(testContext: VertxTestContext) {
        val fakeArn = "arn:aws:sns:us-east-1:000000000000:test-topic:non-existent-uuid"
        val updateRequest = UpdateSubscriptionRequest(
            attributes = mapOf("RawMessageDelivery" to "true")
        )
        val response = updateSubscriptionApi(fakeArn, updateRequest)
        
        assertEquals(404, response.statusCode)
        val error = gson.fromJson(response.text, com.jameskbride.localsns.api.subscriptions.ErrorResponse::class.java)
        assertEquals("NOT_FOUND", error.error)
        
        testContext.completeNow()
    }

    @Test
    fun `it can delete a subscription via JSON API`(testContext: VertxTestContext) {
        val (topic, subscription) = createTopicAndSubscription()
        
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
        val topicRequest = CreateTopicRequest("test-topic-list")
        val topicResponse = createTopicApi(topicRequest)
        val topic = gson.fromJson(topicResponse.text, TopicResponse::class.java)
        
        val sub1Request = CreateSubscriptionRequest(
            topicArn = topic.arn,
            protocol = "http",
            endpoint = "http://example1.com/webhook"
        )
        val sub2Request = CreateSubscriptionRequest(
            topicArn = topic.arn,
            protocol = "http",
            endpoint = "http://example2.com/webhook"
        )
        
        val sub1Response = createSubscriptionApi(sub1Request)
        val sub2Response = createSubscriptionApi(sub2Request)
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
        val topicRequest = CreateTopicRequest("test-topic-sqs")
        val topicResponse = createTopicApi(topicRequest)
        val topic = gson.fromJson(topicResponse.text, TopicResponse::class.java)
        
        val subscriptionRequest = CreateSubscriptionRequest(
            topicArn = topic.arn,
            protocol = "sqs",
            endpoint = "https://sqs.us-east-1.amazonaws.com/123456789012/my-queue"
        )
        val response = createSubscriptionApi(subscriptionRequest)
        
        assertEquals(201, response.statusCode)
        val subscription = gson.fromJson(response.text, SubscriptionResponse::class.java)
        
        assertTrue(subscription.endpoint!!.startsWith("aws2-sqs://my-queue"))
        assertTrue(subscription.endpoint!!.contains("queueOwnerAWSAccountId=123456789012"))
        
        testContext.completeNow()
    }

    // Helper methods
    private fun createTopicAndSubscription(topicName: String = "test-topic"): Pair<TopicResponse, SubscriptionResponse> {
        val topicRequest = CreateTopicRequest(topicName)
        val topicResponse = createTopicApi(topicRequest)
        val topic = gson.fromJson(topicResponse.text, TopicResponse::class.java)
        
        val subscriptionRequest = CreateSubscriptionRequest(
            topicArn = topic.arn,
            protocol = "http",
            endpoint = "http://example.com/webhook"
        )
        val subscriptionResponse = createSubscriptionApi(subscriptionRequest)
        val subscription = gson.fromJson(subscriptionResponse.text, SubscriptionResponse::class.java)
        
        return Pair(topic, subscription)
    }

    private fun getSubscriptionsApi(): Response {
        return khttp.get("${getBaseUrl()}/api/subscriptions")
    }

    private fun getSubscriptionsByTopicApi(topicArn: String): Response {
        return khttp.get("${getBaseUrl()}/api/topics/$topicArn/subscriptions")
    }

    private fun createSubscriptionApi(request: CreateSubscriptionRequest): Response {
        return khttp.post(
            url = "${getBaseUrl()}/api/subscriptions",
            headers = mapOf("Content-Type" to "application/json"),
            data = gson.toJson(request)
        )
    }

    private fun getSubscriptionApi(arn: String): Response {
        return khttp.get("${getBaseUrl()}/api/subscriptions/$arn")
    }

    private fun updateSubscriptionApi(arn: String, request: UpdateSubscriptionRequest): Response {
        return khttp.put(
            url = "${getBaseUrl()}/api/subscriptions/$arn",
            headers = mapOf("Content-Type" to "application/json"),
            data = gson.toJson(request)
        )
    }

    private fun deleteSubscriptionApi(arn: String): Response {
        return khttp.delete("${getBaseUrl()}/api/subscriptions/$arn")
    }

    private fun createTopicApi(request: CreateTopicRequest): Response {
        return khttp.post(
            url = "${getBaseUrl()}/api/topics",
            headers = mapOf("Content-Type" to "application/json"),
            data = gson.toJson(request)
        )
    }
}
