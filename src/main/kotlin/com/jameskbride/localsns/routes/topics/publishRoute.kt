package com.jameskbride.localsns.routes.topics

import com.jameskbride.localsns.*
import com.jameskbride.localsns.models.MessageAttribute
import com.jameskbride.localsns.models.Topic
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
    val subscriptionsMap = getSubscriptionsMap(vertx)
    val subscriptions = subscriptionsMap!!.getOrDefault(topicArn, listOf())
    val camelContext = DefaultCamelContext()
    val producerTemplate = camelContext.createProducerTemplate()
    camelContext.start()

    if (messageStructure != null) {
        val success = when (messageStructure) {
            "json" -> {
                publishJsonStructure(message, messageAttributes, subscriptions, producerTemplate, ctx, logger)
            }
            else -> {
                publishBasicMessage(message, messageAttributes, subscriptions, producerTemplate, logger)
                true
            }
        }

        if (!success) {
            logAndReturnError(ctx, logger, "Message structure is not valid")
            return@route
        }
    } else {
        publishBasicMessage(message, messageAttributes, subscriptions, producerTemplate, logger)
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

fun getTopicArn(topicArn: String?, targetArn: String?): String? {
    return topicArn ?: targetArn
}