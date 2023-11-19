package com.jameskbride.localsns.routes.topics

import com.google.gson.Gson
import com.jameskbride.localsns.*
import com.jameskbride.localsns.models.*
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.get
import org.apache.camel.ProducerTemplate
import org.apache.camel.impl.DefaultCamelContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.regex.Pattern

val publishRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    val logger: Logger = LogManager.getLogger("publishRoute")
    val topicArnFormAttribute = getFormAttribute(ctx, "TopicArn")
    val targetArnFormAttribute = getFormAttribute(ctx, "TargetArn")
    val topicArn = getTopicArn(topicArnFormAttribute, targetArnFormAttribute)
    val message = getFormAttribute(ctx, "Message")
    val messageStructure = getFormAttribute(ctx, "MessageStructure")
    val attributes = ctx.request().formAttributes()
        .filter { it.key.startsWith("MessageAttributes.entry") }
        .filterNot { it.key.matches(".*\\.DataType.*".toRegex()) }
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

    val messageAttributes = MessageAttribute.parse(attributes).associate { it.name to it.value }
    val subscriptionsMap = getSubscriptionsMap(vertx)
    val subscriptions = subscriptionsMap!!.getOrDefault(topicArn, listOf())
    val camelContext = DefaultCamelContext()
    val producerTemplate = camelContext.createProducerTemplate()
    camelContext.start()

    when (messageStructure) {
        "json" -> {
            if (!publishJsonStructure(message, ctx, logger, subscriptions, producerTemplate, messageAttributes)) return@route
        }
        else -> {
            publishBasicMessage(subscriptions, logger, message, producerTemplate, messageAttributes)
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

private fun publishJsonStructure(
    message: String?,
    ctx: RoutingContext,
    logger: Logger,
    subscriptions: List<Subscription>,
    producerTemplate: ProducerTemplate,
    messageAttributes: Map<String, String>
): Boolean {
    try {
        val messages = JsonObject(message)
        if (messages.get<String?>("default") == null) {
            logAndReturnError(ctx, logger, "Attribute 'default' is required when MessageStructure is json.")
            return false
        }

        subscriptions.forEach { subscription ->
            try {
                val messageToPublish: Any = if (messages.containsKey(subscription.protocol)) {
                    messages[subscription.protocol]
                } else {
                    messages["default"]
                }
                logger.info("Messages to publish: $messageToPublish")
                publishMessage(producerTemplate, subscription, messageToPublish as String, logger, messageAttributes)
            } catch (e: Exception) {
                logger.error("An error occurred when publishing to: ${subscription.endpoint}", e)
            }
        }
    } catch (ex: Exception) {
        ex.printStackTrace()
        logAndReturnError(ctx, logger, "Message must be valid JSON")
        return false
    }
    return true
}

private fun publishBasicMessage(
    subscriptions: List<Subscription>,
    logger: Logger,
    message: String,
    producerTemplate: ProducerTemplate,
    messageAttributes: Map<String, String>
) {
    subscriptions.forEach { subscription ->
        try {
            logger.info("Message to publish: $message")
            publishMessage(producerTemplate, subscription, message, logger, messageAttributes)
        } catch (e: Exception) {
            logger.error("An error occurred when publishing to: ${subscription.endpoint}", e)
        }
    }
}

fun getTopicArn(topicArn: String?, targetArn: String?): String? {
    return topicArn ?: targetArn
}

private fun publishMessage(
    producer: ProducerTemplate,
    subscription: Subscription,
    message: String,
    logger: Logger,
    messageAttributes: Map<String, String>
) {
    val headers = messageAttributes.map { it.key to it.value }.toMap() +
            mapOf(
                "x-amz-sns-message-type" to "Notification",
                "x-amz-sns-message-id" to UUID.randomUUID().toString(),
                "x-amz-sns-subscription-arn" to subscription.arn,
                "x-amz-sns-topic-arn" to subscription.topicArn
            )
    when (subscription.protocol) {
        "lambda" -> {
            publishToLambda(subscription, producer, headers, logger, message)
        }
        "http" -> {
            publishToHttp(subscription, headers, message, producer, logger)
        }
        "https" -> {
            publishToHttp(subscription, headers, message, producer, logger)
        }
        else -> {
            val timestamp = LocalDateTime.now()
            val snsMessage = createSnsMessage(timestamp, message, subscription)
            val gson = Gson()
            val messageToPublish = gson.toJson(snsMessage)
            producer.asyncRequestBodyAndHeaders(subscription.decodedEndpointUrl(), messageToPublish, headers)
                .exceptionally { logger.error("Error publishing message $messageToPublish, to subscription: $subscription", it) }
        }
    }
}

private fun publishToLambda(
    subscription: Subscription,
    producer: ProducerTemplate,
    headers: Map<String, String>,
    logger: Logger,
    message: String
) {
    val timestamp = LocalDateTime.now()
    val snsMessage = createSnsMessage(timestamp, message, subscription)
    val gson = Gson()
    val record = LambdaRecord("aws:sns", subscription.arn, 1.0, snsMessage)
    val event = LambdaEvent(listOf(record))
    val messageToPublish = gson.toJson(event)
    producer.asyncRequestBodyAndHeaders(
        subscription.decodedEndpointUrl(),
        messageToPublish,
        headers + mapOf("Content-Type" to "application/json")
    )
        .exceptionally { logger.error("Error publishing message $message, to subscription: $subscription", it) }
}

private fun publishToHttp(
    subscription: Subscription,
    headers: Map<String, String>,
    message: String,
    producer: ProducerTemplate,
    logger: Logger
) {
    val timestamp = LocalDateTime.now()
    val snsMessage = createSnsMessage(timestamp, message, subscription)
    val gson = Gson()
    val isRawMessageDelivery = subscription.subscriptionAttributes["RawMessageDelivery"] == "true"
    val httpHeaders = if (isRawMessageDelivery) {
        headers + mapOf("x-amz-sns-rawdelivery" to "true")
    } else {
        headers
    }

    val messageToPublish = if (isRawMessageDelivery) {
        message
    } else {
        gson.toJson(snsMessage)
    }

    producer.asyncRequestBodyAndHeaders(subscription.decodedEndpointUrl(), messageToPublish, httpHeaders)
        .exceptionally {
            logger.error(
                "Error publishing message $messageToPublish, to subscription: $subscription",
                it
            )
        }
}

private fun createSnsMessage(
    timestamp: LocalDateTime?,
    message: String,
    subscription: Subscription
): SnsMessage {
    val formattedTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(timestamp)
    return SnsMessage(
        message,
        UUID.randomUUID().toString(),
        "SIGNATURE",
        1,
        "http://signing-cert-url",
        null,
        formattedTimestamp,
        subscription.topicArn,
    )
}
