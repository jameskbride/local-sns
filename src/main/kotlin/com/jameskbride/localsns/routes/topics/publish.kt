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
import org.apache.logging.log4j.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

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
    routingContext: RoutingContext,
    logger: Logger
): Boolean {
    val subscriptions = getTopicSubscriptions(routingContext.vertx(), topicArn)
    try {
        val messages = JsonObject(message)
        if (messages.get<String?>("default") == null) {
            logAndReturnError(routingContext, logger, "Attribute 'default' is required when MessageStructure is json.")
            return false
        }

        subscriptions.forEach { subscription ->
            try {
                val messageToPublish: Any = if (messages.containsKey(subscription.protocol)) {
                    messages[subscription.protocol]
                } else {
                    messages["default"]
                }
                logger.debug("Messages to publish: $messageToPublish")
                publishToSubscription(subscription, messageToPublish as String, messageAttributes, producerTemplate, logger)
            } catch (e: Exception) {
                logger.error("An error occurred when publishing to: ${subscription.endpoint}", e)
            }
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
    routingContext: RoutingContext,
    logger: Logger
) {
    val subscriptions = getTopicSubscriptions(routingContext.vertx(), topicArn)
    subscriptions.forEach { subscription ->
        try {
            publishToSubscription(subscription, message, messageAttributes, producerTemplate, logger)
        } catch (e: Exception) {
            logger.error("An error occurred when publishing to: ${subscription.endpoint}", e)
        }
    }
}

fun publishBasicMessagesToTopic(
    messages: List<String>,
    messageAttributes: Map<String, MessageAttribute>,
    topicArn: String,
    producerTemplate: ProducerTemplate,
    routingContext: RoutingContext,
    logger: Logger
) {
    val subscriptions = getTopicSubscriptions(routingContext.vertx(), topicArn)
    subscriptions.forEach { subscription ->
        try {
            publishToSubscription(subscription, messages, messageAttributes, producerTemplate, logger)
        } catch (e: Exception) {
            logger.error("An error occurred when publishing to: ${subscription.endpoint}", e)
        }
    }
}

private fun publishToSubscription(
    subscription: Subscription,
    message: String,
    messageAttributes: Map<String, MessageAttribute>,
    producer: ProducerTemplate,
    logger: Logger
) {
    val matchesFilterPolicy = getMatchesFilterPolicy(subscription, message, messageAttributes)

    if (!matchesFilterPolicy) {
        logger.debug("Message does not match filter policy for subscription: ${subscription.arn}")
        return
    }

    val headers = messageAttributes.map { it.key to it.value.value }.toMap() +
            mapOf(
                "x-amz-sns-message-type" to "Notification",
                "x-amz-sns-message-id" to UUID.randomUUID().toString(),
                "x-amz-sns-subscription-arn" to subscription.arn,
                "x-amz-sns-topic-arn" to subscription.topicArn
            )
    when (subscription.protocol) {
        "lambda" -> {
            publishToLambda(subscription, message, headers, producer, logger)
        }
        "http" -> {
            publishToHttp(subscription, message, headers, producer, logger)
        }
        "https" -> {
            publishToHttp(subscription, message, headers, producer, logger)
        }
        "slack" -> {
            publishToSlack(subscription, message, headers, producer, logger)
        }
        "sqs" -> {
            publishToSqs(subscription, message, headers, producer, logger)
        }
        else -> {
            publishAllowingRawMessage(subscription, message, headers, producer, logger)
        }
    }
}

private fun publishToSubscription(
    subscription: Subscription,
    messages: List<String>,
    messageAttributes: Map<String, MessageAttribute>,
    producer: ProducerTemplate,
    logger: Logger
) {
    val messagesMatchingFilterPolicy = messages.filter { msg ->
        getMatchesFilterPolicy(subscription, msg, messageAttributes)
    }

    val headers = messageAttributes.map { it.key to it.value.value }.toMap() +
            mapOf(
                "x-amz-sns-message-type" to "Notification",
                "x-amz-sns-subscription-arn" to subscription.arn,
                "x-amz-sns-topic-arn" to subscription.topicArn
            )

    //Batch publishing is only supported for SQS subscriptions
    if (subscription.protocol == "sqs") {
        publishToSqs(subscription, messages, headers, producer, logger)
        return
    }

    messagesMatchingFilterPolicy.forEach { message ->
        when (subscription.protocol) {
            "lambda" -> {
                publishToLambda(subscription, message, headers, producer, logger)
            }
            "http" -> {
                publishToHttp(subscription, message, headers, producer, logger)
            }
            "https" -> {
                publishToHttp(subscription, message, headers, producer, logger)
            }
            "slack" -> {
                publishToSlack(subscription, message, headers, producer, logger)
            }
            else -> {
                publishAllowingRawMessage(subscription, message, headers, producer, logger)
            }
        }
    }
}

private fun getMatchesFilterPolicy(
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
    val filterPolicy = JsonObject(filterPolicySubscriptionAttribute)
    val matched = filterPolicy.map.all {
        if (!messageAttributes.containsKey(it.key)) {
            false
        } else {
            val permittedValues = it.value as List<*>
            val messageAttribute = messageAttributes[it.key]
            when (messageAttribute!!.dataType) {
                "Number" -> {
                    val parsedAttribute = messageAttribute.value.toDouble()
                    attributeMatchesPolicy(permittedValues, parsedAttribute)
                }
                else -> {
                    attributeMatchesPolicy(permittedValues, messageAttribute.value)
                }
            }
        }
    }
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
    producer: ProducerTemplate,
    logger: Logger
) {
    publishAllowingRawMessage(subscription, message, headers, producer, logger)
}

private fun publishToSqs(
    subscription: Subscription,
    messages: List<String>,
    headers: Map<String, String>,
    producer: ProducerTemplate,
    logger: Logger
) {
    publishAllowingRawMessage(subscription, messages, headers, producer, logger)
}

private fun publishAllowingRawMessage(
    subscription: Subscription,
    message: String,
    headers: Map<String, String>,
    producer: ProducerTemplate,
    logger: Logger
) {
    val messageToPublish = if (subscription.isRawMessageDelivery()) {
        message
    } else {
        val timestamp = LocalDateTime.now()
        val snsMessage = createSnsMessage(subscription, message, timestamp)
        val gson = Gson()
        gson.toJson(snsMessage)
    }
    publishToCamel(subscription, messageToPublish, headers, producer, logger)
}

private fun publishAllowingRawMessage(
    subscription: Subscription,
    messages: List<String>,
    headers: Map<String, String>,
    producer: ProducerTemplate,
    logger: Logger
) {
    val messagesToPublish = messages.map {message -> if (subscription.isRawMessageDelivery()) {
        message
    } else {
        val timestamp = LocalDateTime.now()
        val snsMessage = createSnsMessage(subscription, message, timestamp)
        val gson = Gson()
        gson.toJson(snsMessage)
    }}
    publishToCamel(subscription, messagesToPublish, headers, producer, logger)
}

private fun publishToLambda(
    subscription: Subscription,
    message: String,
    headers: Map<String, String>,
    producer: ProducerTemplate,
    logger: Logger
) {
    val timestamp = LocalDateTime.now()
    val snsMessage = createSnsMessage(subscription, message, timestamp)
    val gson = Gson()
    val record = LambdaRecord("aws:sns", subscription.arn, 1.0, snsMessage)
    val event = LambdaEvent(listOf(record))
    val messageToPublish = gson.toJson(event)
    publishToCamel(subscription, messageToPublish, headers + mapOf("Content-Type" to "application/json"), producer, logger)
}

private fun publishToHttp(
    subscription: Subscription,
    message: String,
    headers: Map<String, String>,
    producer: ProducerTemplate,
    logger: Logger
) {
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

    publishToCamel(subscription, messageToPublish, httpHeaders, producer, logger)
}

private fun publishToSlack(
    subscription: Subscription,
    message: String,
    headers: Map<String, String>,
    producer: ProducerTemplate,
    logger: Logger
) {
    publishToCamel(subscription, message, headers, producer, logger)
}

private fun publishToCamel(
    subscription: Subscription,
    message: String,
    headers: Map<String, String>,
    producer: ProducerTemplate,
    logger: Logger
) {
    logger.info("Publishing to subscription: ${subscription.arn}: message: $message, headers: $headers")
    producer.asyncRequestBodyAndHeaders(subscription.decodedEndpointUrl(), message, headers)
        .exceptionally { logger.error("Error publishing message $message, to subscription: $subscription", it) }
}

private fun publishToCamel(
    subscription: Subscription,
    messages: List<String>,
    headers: Map<String, String>,
    producer: ProducerTemplate,
    logger: Logger
) {
    logger.info("Publishing batch to subscription: ${subscription.arn}: message: $messages, headers: $headers")
    producer.asyncRequestBodyAndHeaders(subscription.decodedEndpointUrl(), messages, headers)
        .exceptionally { logger.error("Error publishing message $messages, to subscription: $subscription", it) }
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
