package com.jameskbride.localsns.routes.topics

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.jameskbride.localsns.NOT_FOUND
import com.jameskbride.localsns.getFormAttribute
import com.jameskbride.localsns.getTopicsMap
import com.jameskbride.localsns.logAndReturnError
import com.jameskbride.localsns.models.MessageAttribute
import com.jameskbride.localsns.models.Topic
import com.jameskbride.localsns.topics.PublishRequest
import com.jameskbride.localsns.topics.getTopicArn
import io.vertx.ext.web.RoutingContext
import org.apache.camel.impl.DefaultCamelContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.*
import java.util.regex.Pattern

val publishRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    val logger: Logger = LogManager.getLogger("publishRoute")
    val topicArnFormAttribute = getFormAttribute(ctx, "TopicArn")
    val targetArnFormAttribute = getFormAttribute(ctx, "TargetArn")
    val topicArn = getTopicArn(topicArnFormAttribute, targetArnFormAttribute)
    val message = getFormAttribute(ctx, "Message")
    val messageStructure = getFormAttribute(ctx, "MessageStructure")
    val formAttributes = ctx.request().formAttributes()
    logger.debug("MessageAttributes passed to publish: {}", formAttributes)
    val attributes = formAttributes
        .filter { it.key.startsWith("MessageAttributes.entry") }
    val vertx = ctx.vertx()

    if (topicArn == null) {
        val errorMessage =
            "Either TopicArn or TargetArn is required. TopicArn: $topicArnFormAttribute, TargetArn: $targetArnFormAttribute"
        logAndReturnError(ctx, logger, errorMessage)
        return@route
    }

    if (!Pattern.matches(Topic.arnPattern, topicArn)) {
        logAndReturnError(ctx, logger, "Invalid TopicARN or TargetArn: $topicArn")
        return@route
    }

    val topicsMap = getTopicsMap(vertx)!!
    if (!topicsMap.contains(topicArn)) {
        logAndReturnError(ctx, logger, "Invalid TopicARN or TargetArn: $topicArn", NOT_FOUND, 404)
        return@route
    }

    if (messageStructure != null && messageStructure != "json") {
        logAndReturnError(ctx, logger, "MessageStructure must be json")
        return@route
    }

    if (message == null) {
        val errorMessage = "Message is missing"
        logAndReturnError(ctx, logger, errorMessage)
        return@route
    }

    val messageAttributes:Map<String, MessageAttribute> = MessageAttribute.parse(attributes)
    val camelContext = DefaultCamelContext()
    camelContext.start()
    val gson = Gson()
    val publishRequest = PublishRequest(
        message = message,
        messageAttributes = messageAttributes,
        topicArn = topicArn
    )

    if (messageStructure != null) {
         when (messageStructure) {
            "json" -> {
                val messages = gson.fromJson(message, JsonObject::class.java)
                if (messages.get("default") == null) {
                    logAndReturnError(ctx, logger, "Attribute 'default' is required when MessageStructure is json.")
                    return@route
                }
                vertx.eventBus().publish("publishJsonStructure", gson.toJson(publishRequest))
            }
            else -> {
                vertx.eventBus().publish("publishBasicMessage", gson.toJson(publishRequest))
            }
        }
    } else {
        vertx.eventBus().publish("publishBasicMessage", gson.toJson(publishRequest))
    }

    val messageId = UUID.randomUUID()
    ctx.request().response()
        .putHeader("context-type", "text/xml")
        .setStatusCode(200)
        .end(
            """
              <PublishResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/">
                <PublishResult>
                  <MessageId>${messageId}</MessageId>
                </PublishResult>
                <ResponseMetadata>
                  <RequestId>${UUID.randomUUID()}</RequestId>
                </ResponseMetadata>
              </PublishResponse>
            """.trimIndent()
        )
}