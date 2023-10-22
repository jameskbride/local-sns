package com.jameskbride.fakesns.routes.topics

import com.jameskbride.fakesns.*
import com.jameskbride.fakesns.models.Topic
import com.typesafe.config.ConfigFactory
import io.vertx.core.shareddata.LocalMap
import io.vertx.ext.web.RoutingContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.*
import java.util.regex.Pattern

val createTopicRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    val logger: Logger = LogManager.getLogger("createTopicRoute")
    val topicName = getFormAttribute(ctx, "Name")
    if (topicName == null) {
        val errorMessage = "Topic name is missing"
        logAndReturnError(ctx, logger, errorMessage)
        return@route
    }

    if (!Pattern.matches(Topic.namePattern, topicName)) {
        val errorMessage = "Invalid topic name: $topicName"
        logAndReturnError(ctx, logger, errorMessage)
        return@route
    }

    val vertx = ctx.vertx()
    val topics = getTopicsMap(vertx)!!
    val subscriptions = getSubscriptionsMap(vertx)!!
    val topic = getOrCreateTopic(topics, topicName)
    logger.info("Creating topic: $topic")
    topics[topic!!.arn] = topic
    subscriptions[topic.arn] = listOf()
    vertx.eventBus().publish("configChange", "configChange")

    ctx.request().response()
        .setStatusCode(200)
        .putHeader("context-type", "text/xml")
        .end(
            """
                <CreateTopicResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/">
                  <CreateTopicResult>
                    <TopicArn>${topic.arn}</TopicArn>
                  </CreateTopicResult>
                  <ResponseMetadata>
                      <RequestId>${UUID.randomUUID()}</RequestId>
                  </ResponseMetadata>
                </CreateTopicResponse>
            """.trimIndent()
        )
}

private fun getOrCreateTopic(
    topics: LocalMap<String, Topic>,
    topicName: String
) = if (topics.contains(topicName)) {
    topics[topicName]
} else {
    val config = ConfigFactory.load()
    val region = getAwsRegion(config)
    val accountId = getAwsAccountId(config)
    val newTopic = Topic(arn = "arn:aws:sns:$region:$accountId:${topicName}", name = topicName)

    newTopic
}