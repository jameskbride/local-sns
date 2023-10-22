package com.jameskbride.fakesns.routes.topics

import com.jameskbride.fakesns.*
import com.jameskbride.fakesns.models.Topic
import io.vertx.ext.web.RoutingContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.*
import java.util.regex.Pattern

val deleteTopicRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    val logger: Logger = LogManager.getLogger("deleteTopicRoute")
    val topicArn = getFormAttribute(ctx, "TopicArn")

    if (!Pattern.matches(Topic.arnPattern, topicArn)) {
        val errorMessage = "Invalid TopicArn: $topicArn"
        logAndReturnError(ctx, logger, errorMessage)
        return@route
    }

    val vertx = ctx.vertx()
    val topics = getTopicsMap(vertx)!!
    if (!topics.contains(topicArn)) {
        val errorMessage = "TopicArn not found: $topicArn"
        logAndReturnError(ctx, logger, errorMessage, NOT_FOUND, 404)
        return@route
    }

    val subscriptions = getSubscriptionsMap(vertx)!!
    logger.info("Deleting topic: $topicArn")
    topics.remove(topicArn)
    subscriptions.remove(topicArn)
    vertx.eventBus().publish("configChange", "configChange")
    ctx.request().response()
        .setStatusCode(200)
        .putHeader("context-type", "text/xml")
        .end(
            """
              <DeleteTopicResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/">
                <ResponseMetadata>
                  <RequestId>
                    ${UUID.randomUUID()}
                  </RequestId>
                </ResponseMetadata>
              </DeleteTopicResponse>
            """.trimIndent()
        )
}