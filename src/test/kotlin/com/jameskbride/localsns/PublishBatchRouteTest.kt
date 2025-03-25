package com.jameskbride.localsns

import com.jameskbride.localsns.verticles.MainVerticle
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import khttp.responses.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

@ExtendWith(VertxExtension::class)
class PublishBatchRouteTest: BaseTest() {
    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    @Test
    fun `it returns an error when both the TopicArn and TargetArn are missing`(testContext: VertxTestContext) {
        val response = publishBatch(topicArn = null)

        assertEquals(400, response.statusCode)
        testContext.completeNow()
    }

    @Test
    fun `it returns an error when the TopicArn does not exist`(testContext: VertxTestContext) {
        val topicArn = createValidArn("topic1")

        val response = publishBatch(topicArn = topicArn)

        assertEquals(404, response.statusCode)
        testContext.completeNow()
    }

    @Test
    fun `it returns an error when the TopicArn is not valid`(testContext: VertxTestContext) {
        val topicArn = "arn:aws:sns:us-east-1:123456789012:topic!"

        val response = publishBatch(topicArn = topicArn)

        assertEquals(400, response.statusCode)
        testContext.completeNow()
    }

    @Test
    fun `it returns an error when the batch is empty`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val response = publishBatch(topicArn = topic.arn)

        assertEquals(400, response.statusCode)
        testContext.completeNow()
    }

    @Test
    fun `it returns an error when the batch is too large`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val batchEntries = mutableMapOf<String, String>()
        for (i in 0..10) {
            batchEntries["PublishBatchRequestEntries.member.${i}.Id"] = "${UUID.randomUUID()}"
            batchEntries["PublishBatchRequestEntries.member.${i}.Message"] = "Hello, SNS!$i"
        }
        val response = publishBatch(topicArn = topic.arn, batchEntries = batchEntries)

        assertEquals(400, response.statusCode)
        testContext.completeNow()
    }

    @Test
    fun `it returns an error when the batch entry ids are not distinct`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val batchEntries = mutableMapOf<String, String>()
        for (i in 0..1) {
            batchEntries["PublishBatchRequestEntries.member.${i}.Id"] = "thesameid"
            batchEntries["PublishBatchRequestEntries.member.${i}.Message"] = "Hello, SNS!$i"
        }
        val response = publishBatch(topicArn = topic.arn, batchEntries = batchEntries)

        assertEquals(400, response.statusCode)
        testContext.completeNow()
    }

    @Test
    fun `it returns an error when batch entry ids are not formatted correctly`(testContext: VertxTestContext) {
        val topic = createTopicModel("topic1")
        val batchEntries = mutableMapOf<String, String>()
        //no spaces allowed
        batchEntries["PublishBatchRequestEntries.member.1.Id"] = "the same id"
        batchEntries["PublishBatchRequestEntries.member.1.Message"] = "Hello, SNS!1"
        val response = publishBatch(topicArn = topic.arn, batchEntries = batchEntries)

        assertEquals(400, response.statusCode)
        testContext.completeNow()
    }

    fun publishBatch(topicArn: String?, batchEntries: Map<String, String> = mapOf()): Response {
        val data = mutableMapOf(
            "Action" to "PublishBatch"
        )
        if (topicArn != null) { data["TopicArn"] = topicArn }

        if (batchEntries.isNotEmpty()) {
            batchEntries.forEach({
                data[it.key] = it.value
            })
        }

        return postFormData(data)
    }
}