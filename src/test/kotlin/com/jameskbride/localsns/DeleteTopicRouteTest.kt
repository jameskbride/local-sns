package com.jameskbride.localsns

import com.jameskbride.localsns.verticles.MainVerticle
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class DeleteTopicRouteTest : BaseTest() {

  @BeforeEach
  fun setup(vertx: Vertx, testContext: VertxTestContext) {
    vertx.deployVerticle(MainVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
  }

  @Test
  fun `it returns an error when the topic does not exist`(testContext: VertxTestContext) {
    val topic = "topicName"
    val payload = mapOf(
      "Action" to "DeleteTopic",
      "TopicArn" to topic
    )
    val response = postFormData(payload)

    assertEquals(404, response.statusCode)
    testContext.completeNow()
  }

  @Test
  fun `it returns an error when the topic name is invalid`(testContext: VertxTestContext) {
    val topic = "topicName"
    createTopic(topic)
    val payload = mapOf(
      "Action" to "DeleteTopic",
      "TopicArn" to "invalid topic name"
    )
    val response = postFormData(payload)

    assertEquals(400, response.statusCode)
    testContext.completeNow()
  }

  @Test
  fun `it can delete a topic`(testContext: VertxTestContext) {
    val topic = "topicName"
    val topicResponse = createTopic(topic)
    val topicArn = getTopicArnFromResponse(topicResponse)
    val response = deleteTopic(topicArn)

    assertEquals(200, response.statusCode)
    testContext.completeNow()

    val listResponse = listTopics()
    assertFalse(listResponse.text.contains(topicArn))
    testContext.completeNow()
  }

  @Test
  fun `it indicates a db change`(vertx: Vertx, testContext: VertxTestContext) {
    val topic = "topicName"
    val topicResponse = createTopic(topic)
    val topicArn = getTopicArnFromResponse(topicResponse)

    vertx.eventBus().consumer<String>("configChange") {
      testContext.completeNow()
    }

    deleteTopic(topicArn)
  }
}
