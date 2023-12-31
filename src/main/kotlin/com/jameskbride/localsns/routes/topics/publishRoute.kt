package com.jameskbride.localsns.routes.topics

import com.google.gson.Gson
import com.jameskbride.localsns.*
import com.jameskbride.localsns.models.*
import com.jameskbride.localsns.models.SubscriptionAttribute.Companion.FILTER_POLICY
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
    messageAttributes: Map<String, MessageAttribute>
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
                logger.debug("Messages to publish: $messageToPublish")
                publishMessage(subscription, messageToPublish as String, messageAttributes, producerTemplate, logger)
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
    messageAttributes: Map<String, MessageAttribute>
) {
    subscriptions.forEach { subscription ->
        try {
            publishMessage(subscription, message, messageAttributes, producerTemplate, logger)
        } catch (e: Exception) {
            logger.error("An error occurred when publishing to: ${subscription.endpoint}", e)
        }
    }
}

fun getTopicArn(topicArn: String?, targetArn: String?): String? {
    return topicArn ?: targetArn
}

private fun publishMessage(
    subscription: Subscription,
    message: String,
    messageAttributes: Map<String, MessageAttribute>,
    producer: ProducerTemplate,
    logger: Logger
) {
    if (subscription.subscriptionAttributes.containsKey(FILTER_POLICY)) {
        val filterPolicyScope = subscription.subscriptionAttributes.get("FilterPolicyScope")
        val match = when (filterPolicyScope) {
            "MessageBody" -> matchesFilterPolicy(subscription, message)
            else -> matchesFilterPolicy(subscription, messageAttributes)
        }
        if (!match) {
            return
        }
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

private fun matchesFilterPolicy(
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

private fun matchesFilterPolicy(subscription: Subscription, message:String): Boolean {
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
                stringMatches(attributeMatchPolicy, value)
            }

            is LinkedHashMap<*, *> -> {
                if (permittedValue.containsKey("numeric")) {
                    numericMatches(permittedValue, value)
                } else false
            }

            is Boolean -> {
                booleanMatches(permittedValue, value)
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

        4 -> {
            false
        }

        else -> false
    }
}

private fun booleanMatches(permittedValue: Any?, attribute: Any?) = permittedValue.toString() == attribute.toString()

private fun stringMatches(permittedValues: List<*>, attribute: Any?) =
    permittedValues.map { it.toString() }.contains(attribute)

private fun numberMatches(matchParams: List<*>, value: Any?): Boolean {
    val operator = matchParams[0]
    return when (operator) {
        "=" -> numEquals(value as Double, matchParams[1] as Double)
        else -> false
    }
}

private fun numEquals(messageAttribute: Double, filterPolicyValue: Double):Boolean {
    return messageAttribute == filterPolicyValue
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
    publish(subscription, messageToPublish, headers, producer, logger)
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
    publish(subscription, messageToPublish, headers + mapOf("Content-Type" to "application/json"), producer, logger)
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

    publish(subscription, messageToPublish, httpHeaders, producer, logger)
}

private fun publishToSlack(
    subscription: Subscription,
    message: String,
    headers: Map<String, String>,
    producer: ProducerTemplate,
    logger: Logger
) {
    publish(subscription, message, headers, producer, logger)
}

private fun publish(
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
