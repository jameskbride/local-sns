package com.jameskbride.localsns.routes.topics

import com.jameskbride.localsns.*
import com.jameskbride.localsns.models.MessageAttribute
import com.jameskbride.localsns.models.Subscription
import com.jameskbride.localsns.models.Topic
import io.vertx.ext.web.RoutingContext
import org.apache.camel.ProducerTemplate
import org.apache.camel.impl.DefaultCamelContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URLDecoder
import java.util.*
import java.util.regex.Pattern

val publishRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    val logger: Logger = LogManager.getLogger("publishRoute")
    val topicArn = getFormAttribute(ctx, "TopicArn")
    val message = getFormAttribute(ctx, "Message")
    val attributes = ctx.request().formAttributes()
        .filter { it.key.startsWith("MessageAttributes.entry") }
        .filterNot { it.key.matches(".*\\.DataType.*".toRegex()) }
    val vertx = ctx.vertx()

    if (topicArn == null || message == null) {
        val errorMessage = "TopicArn or Message are missing. TopicArn: $topicArn, Message: $message"
        logAndReturnError(ctx, logger, errorMessage)
        return@route
    }

    if (!Pattern.matches(Topic.arnPattern, topicArn)) {
        logAndReturnError(ctx, logger, "Invalid TopicARN: $topicArn")
        return@route
    }

    val topicsMap = getTopicsMap(vertx)!!
    if (!topicsMap.contains(topicArn)) {
        logAndReturnError(ctx, logger, "Invalid TopicARN: $topicArn", NOT_FOUND, 404)
        return@route
    }
    val messageAttributes = MessageAttribute.parse(attributes).associate { it.name to it.value }
    val subscriptionsMap = getSubscriptionsMap(vertx)
    val subscriptions = subscriptionsMap!!.getOrDefault(topicArn, listOf())
    val camelContext = DefaultCamelContext()
    val producerTemplate = camelContext.createProducerTemplate()
    camelContext.start()
    subscriptions.forEach { subscription ->
        try {
            publishMessage(producerTemplate, subscription, message, messageAttributes, logger)
        } catch (e: Exception) {
            logger.error("An error occurred when publishing to: ${subscription.endpoint}", e)
        }
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

private fun publishMessage(
    producer: ProducerTemplate,
    subscription: Subscription,
    message: String,
    messageAttributes: Map<String, String>,
    logger: Logger
) {
    val decodedUrl = URLDecoder.decode(subscription.endpoint, "UTF-8")
    val headers = messageAttributes.map { it.key to it.value }.toMap() +
            mapOf(
                "x-amz-sns-message-type" to "Notification",
                "x-amz-sns-message-id" to UUID.randomUUID().toString(),
                "x-amz-sns-subscription-arn" to subscription.arn,
                "x-amz-sns-topic-arn" to subscription.topicArn
            )
    logger.info("Publishing message to $decodedUrl: $message, with headers: $headers")
    producer.asyncRequestBodyAndHeaders(decodedUrl, message, headers)
        .exceptionally { it.printStackTrace() }
}
