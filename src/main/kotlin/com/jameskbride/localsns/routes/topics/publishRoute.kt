package com.jameskbride.localsns.routes.topics

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.jameskbride.localsns.*
import com.jameskbride.localsns.models.MessageAttribute
import com.jameskbride.localsns.models.Subscription
import com.jameskbride.localsns.models.Topic
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.eventbus.eventBusOptionsOf
import io.vertx.kotlin.core.json.get
import org.apache.camel.ProducerTemplate
import org.apache.camel.impl.DefaultCamelContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URLDecoder
import java.time.LocalDateTime
import java.time.ZoneOffset
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
                publishMessage(producerTemplate, subscription, Json.encode(messageToPublish), logger, messageAttributes)
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
    val decodedUrl = URLDecoder.decode(subscription.endpoint, "UTF-8")
    val headers = messageAttributes.map { it.key to it.value }.toMap() +
            mapOf(
                "x-amz-sns-message-type" to "Notification",
                "x-amz-sns-message-id" to UUID.randomUUID().toString(),
                "x-amz-sns-subscription-arn" to subscription.arn,
                "x-amz-sns-topic-arn" to subscription.topicArn
            )

    data class LambdaSnsMessage(
        @SerializedName("Message") val message:String,
        @SerializedName("MessageId") val messageId:String,
        @SerializedName("Signature") val signature:String,
        @SerializedName("SignatureVersion") val signatureVersion: Int,
        @SerializedName("SigningCertUrl") val signingCertUrl:String,
        @SerializedName("Subject") val subject: String? = null,
        @SerializedName("Timestamp") val timestamp: String,
        @SerializedName("TopicArn") val topicArn: String,
        @SerializedName("Type") val type: String = "Notification",
        @SerializedName("UnsubscribeUrl") val unsubscribeUrl: String? = null,
    )
    data class LambdaRecord(
        @SerializedName("EventSource") val eventSource:String,
        @SerializedName("EventSubscriptionArn") val eventSubscriptionArn:String,
        @SerializedName("EventVersion") val eventVersion:Double,
        @SerializedName("Message") val message: LambdaSnsMessage,
    )
    data class LambdaEvent(
        @SerializedName("Records") val records:List<LambdaRecord> = listOf()
    )
    val messageToPublish = when (subscription.protocol) {
        "lambda" -> {
            val timestamp = LocalDateTime.now()
            val formattedTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(timestamp)
            val snsMessage = LambdaSnsMessage(
                message,
                UUID.randomUUID().toString(),
                "SIGNATURE",
                1,
                "http://signing-cert-url",
                null,
                formattedTimestamp,
                subscription.topicArn,
                )
            val record = LambdaRecord("aws:sns", subscription.arn, 1.0, snsMessage)
            val event = LambdaEvent(listOf(record))
            val gson = Gson()
            gson.toJson(event)
        }
        else -> {
            message
        }
    }

    logger.info("Publishing to lambda: $messageToPublish")
    producer.asyncRequestBodyAndHeaders(decodedUrl, messageToPublish, headers)
        .exceptionally { it.printStackTrace() }
}
