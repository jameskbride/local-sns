package com.jameskbride.localsns.routes.topics

import com.google.gson.Gson
import com.jameskbride.localsns.getSubscriptionsMap
import com.jameskbride.localsns.logAndReturnError
import com.jameskbride.localsns.models.*
import com.jameskbride.localsns.models.SubscriptionAttribute.Companion.FILTER_POLICY
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.get
import org.apache.camel.ProducerTemplate
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.CompletableFuture

private val logger: Logger = LogManager.getLogger("publish")

fun getTopicSubscriptions(
    vertx: Vertx,
    topicArn: String?
): List<Subscription> {
    val subscriptionsMap = getSubscriptionsMap(vertx)
    val subscriptions = subscriptionsMap!!.getOrDefault(topicArn, listOf())
    return subscriptions
}

fun getTopicArn(topicArn: String?, targetArn: String?): String? {
    return topicArn ?: targetArn
}

fun publishJsonStructure(
    message: String?,
    messageAttributes: Map<String, MessageAttribute>,
    topicArn: String,
    producerTemplate: ProducerTemplate,
    routingContext: RoutingContext
): Boolean {
    logger.info("Publishing message to topic with json structure {}", topicArn)
    val subscriptions = getTopicSubscriptions(routingContext.vertx(), topicArn)
    try {
        val messages = JsonObject(message)
        if (messages.get<String?>("default") == null) {
            logAndReturnError(routingContext, logger, "Attribute 'default' is required when MessageStructure is json.")
            return false
        }

        subscriptions.map { subscription ->
            val messageToPublish: Any = if (messages.containsKey(subscription.protocol)) {
                messages[subscription.protocol]
            } else {
                messages["default"]
            }
            logger.debug("Messages to publish: $messageToPublish")
            publishToSubscription(subscription, messageToPublish as String, messageAttributes, producerTemplate)
                ?.exceptionally { logger.error("Error publishing to subscription: ${subscription.arn}", it) }
        }
    } catch (ex: Exception) {
        ex.printStackTrace()
        logAndReturnError(routingContext, logger, "Message must be valid JSON")
        return false
    }

    return true
}

fun publishBasicMessageToTopic(
    message: String,
    messageAttributes: Map<String, MessageAttribute>,
    topicArn: String,
    producerTemplate: ProducerTemplate,
    routingContext: RoutingContext
) {
    val subscriptions = getTopicSubscriptions(routingContext.vertx(), topicArn)
    logger.info("Publishing to topic: $topicArn")
    subscriptions.forEach { subscription ->
        publishToSubscription(subscription, message, messageAttributes, producerTemplate)
            ?.exceptionally { logger.error("An error occurred when publishing to: ${subscription.endpoint}", it) }
    }
}

private fun publishToSubscription(
    subscription: Subscription,
    message: String,
    messageAttributes: Map<String, MessageAttribute>,
    producer: ProducerTemplate
): CompletableFuture<Any>? {
    val matchesFilterPolicy = matchesFilterPolicy(subscription, message, messageAttributes)

    if (!matchesFilterPolicy) {
        logger.info("Message does not match filter policy for subscription: ${subscription.arn}")
        return CompletableFuture.completedFuture(null)
    }

    val headers = messageAttributes.map { it.key to it.value.value }.toMap() +
            mapOf(
                "x-amz-sns-message-type" to "Notification",
                "x-amz-sns-message-id" to UUID.randomUUID().toString(),
                "x-amz-sns-subscription-arn" to subscription.arn,
                "x-amz-sns-topic-arn" to subscription.topicArn
            )
    return when (subscription.protocol) {
        "lambda" -> {
            logger.info("Publishing to Lambda subscription: ${subscription.arn}")
            publishToLambda(subscription, message, headers, producer)
        }

        "http" -> {
            logger.info("Publishing to HTTP subscription: ${subscription.arn}")
            publishToHttp(subscription, message, headers, producer)
        }

        "https" -> {
            logger.info("Publishing to HTTPS subscription: ${subscription.arn}")
            publishToHttp(subscription, message, headers, producer)
        }

        "slack" -> {
            logger.info("Publishing to Slack subscription: ${subscription.arn}")
            publishToSlack(subscription, message, headers, producer)
        }

        "sqs" -> {
            logger.info("Publishing to SQS subscription: ${subscription.arn}")
            publishToSqs(subscription, message, headers, producer)
        }

        else -> {
            logger.info("Publishing to subscription: ${subscription.arn}")
            publishAllowingRawMessage(subscription, message, headers, producer)
        }
    }
}

private fun matchesFilterPolicy(
    subscription: Subscription,
    message: String,
    messageAttributes: Map<String, MessageAttribute>
) = if (subscription.subscriptionAttributes.containsKey(FILTER_POLICY)) {
    val filterPolicyScope = subscription.subscriptionAttributes.get("FilterPolicyScope")
    when (filterPolicyScope) {
        "MessageBody" -> matchesMessageBodyFilterPolicy(subscription, message)
        else -> matchesMessageAttributesFilterPolicy(subscription, messageAttributes)
    }
} else {
    true
}

private fun matchesMessageAttributesFilterPolicy(
    subscription: Subscription,
    messageAttributes: Map<String, MessageAttribute>
): Boolean {
    val filterPolicySubscriptionAttribute = subscription.subscriptionAttributes[FILTER_POLICY]
    val matched = filterPolicySubscriptionAttribute?.let { filterPolicy ->
        val messageAttributeFilterPolicy = MessageAttributeFilterPolicy(filterPolicy)
        messageAttributeFilterPolicy.matches(messageAttributes)
    } ?: true
    return matched
}

private fun matchesMessageBodyFilterPolicy(subscription: Subscription, message:String): Boolean {
    val filterPolicySubscriptionAttribute = subscription.subscriptionAttributes[FILTER_POLICY]
    val filterPolicy = JsonObject(filterPolicySubscriptionAttribute)
    val messageJson = JsonObject(message)
    val matched = filterPolicy.map.all { filterPolicyAttribute ->
        if (!messageJson.containsKey(filterPolicyAttribute.key)) {
            false
        } else {
            val attribute = messageJson.getValue(filterPolicyAttribute.key)
            attributeMatchesPolicy(filterPolicyAttribute.value as List<*>, attribute)
        }
    }
    return matched
}

private fun attributeMatchesPolicy(
    attributeMatchPolicy: List<*>,
    value: Any?
): Boolean {
    return attributeMatchPolicy.any {
        when (val permittedValue = attributeMatchPolicy.firstOrNull()) {
            is String -> {
                attributeMatchPolicy.map { it.toString() }.contains(value)
            }

            is LinkedHashMap<*, *> -> {
                if (permittedValue.containsKey("numeric")) {
                    numericMatches(permittedValue, value)
                } else false
            }

            is Boolean -> {
                permittedValue.toString() == value.toString()
            }

            else -> false
        }
    }
}

private fun numericMatches(permittedValue: LinkedHashMap<*, *>, attribute: Any?): Boolean {
    val matchParams = permittedValue["numeric"] as List<*>
    return when (matchParams.size) {
        2 -> {
            numberMatches(matchParams, attribute)
        }

        else -> false
    }
}

private fun numberMatches(matchParams: List<*>, value: Any?): Boolean {
    val operator = matchParams[0]
    return when (operator) {
        "=" -> value as Double == matchParams[1] as Double
        else -> false
    }
}

private fun publishToSqs(
    subscription: Subscription,
    message: String,
    headers: Map<String, String>,
    producer: ProducerTemplate
): CompletableFuture<Any>? {
    return publishAllowingRawMessage(subscription, message, headers, producer)
}

private fun publishAllowingRawMessage(
    subscription: Subscription,
    message: String,
    headers: Map<String, String>,
    producer: ProducerTemplate
): CompletableFuture<Any>? {
    val messageToPublish = if (subscription.isRawMessageDelivery()) {
        message
    } else {
        val timestamp = LocalDateTime.now()
        val snsMessage = createSnsMessage(subscription, message, timestamp)
        val gson = Gson()
        gson.toJson(snsMessage)
    }
    return publishToCamel(subscription.decodedEndpointUrl(), messageToPublish, headers, producer)
}

private fun publishToLambda(
    subscription: Subscription,
    message: String,
    headers: Map<String, String>,
    producer: ProducerTemplate
): CompletableFuture<Any>? {
    val timestamp = LocalDateTime.now()
    val snsMessage = createSnsMessage(subscription, message, timestamp)
    val gson = Gson()
    val record = LambdaRecord("aws:sns", subscription.arn, 1.0, snsMessage)
    val event = LambdaEvent(listOf(record))
    val messageToPublish = gson.toJson(event)
    return publishToCamel(
        subscription.decodedEndpointUrl(),
        messageToPublish,
        headers + mapOf("Content-Type" to "application/json"),
        producer
    )
}

private fun publishToHttp(
    subscription: Subscription,
    message: String,
    headers: Map<String, String>,
    producer: ProducerTemplate
): CompletableFuture<Any>? {
    val timestamp = LocalDateTime.now()
    val snsMessage = createSnsMessage(subscription, message, timestamp)
    val gson = Gson()
    val httpHeaders = if (subscription.isRawMessageDelivery()) {
        headers + mapOf("x-amz-sns-rawdelivery" to "true")
    } else {
        headers
    }

    val messageToPublish = if (subscription.isRawMessageDelivery()) {
        message
    } else {
        gson.toJson(snsMessage)
    }

    return publishToCamel(subscription.decodedEndpointUrl(), messageToPublish, httpHeaders, producer)
}

private fun publishToSlack(
    subscription: Subscription,
    message: String,
    headers: Map<String, String>,
    producer: ProducerTemplate
): CompletableFuture<Any>? {
    return publishToCamel(subscription.decodedEndpointUrl(), message, headers, producer)
}

private fun publishToCamel(
    endpoint: String,
    message: String,
    headers: Map<String, String>,
    producer: ProducerTemplate
): CompletableFuture<Any>? {
    logger.debug("Publishing to endpoint: $endpoint: message: $message, headers: $headers")
    return producer.asyncRequestBodyAndHeaders(endpoint, message, headers)
}

private fun createSnsMessage(
    subscription: Subscription,
    message: String,
    timestamp: LocalDateTime?
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
