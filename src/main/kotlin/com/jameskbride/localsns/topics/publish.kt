package com.jameskbride.localsns.topics

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.jameskbride.localsns.getSubscriptionsMap
import com.jameskbride.localsns.models.*
import com.jameskbride.localsns.models.SubscriptionAttribute.Companion.FILTER_POLICY
import io.vertx.core.Future
import io.vertx.core.Vertx
import org.apache.camel.ProducerTemplate
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.CompletableFuture

private val logger: Logger = LogManager.getLogger("publish")

data class PublishRequest(val message: String, val messageAttributes: Map<String, MessageAttribute> = mapOf(), val topicArn: String)

fun getTopicSubscriptions(
    topicArn: String?,
    vertx: Vertx
): List<Subscription> {
    val subscriptionsMap = getSubscriptionsMap(vertx)
    val subscriptions = subscriptionsMap!!.getOrDefault(topicArn, listOf())
    return subscriptions
}

fun getTopicArn(topicArn: String?, targetArn: String?): String? {
    return topicArn ?: targetArn
}

fun publishJsonStructure(
    publishRequest: PublishRequest,
    producerTemplate: ProducerTemplate,
    vertx: Vertx,
) {
    vertx.executeBlocking<Any>({ ->
        try {
            logger.info("Publishing message to topic with json structure {}", publishRequest.topicArn)
            val subscriptions = getTopicSubscriptions(publishRequest.topicArn, vertx)
            val gson = Gson()
            val messages = gson.fromJson(publishRequest.message, JsonObject::class.java)
            subscriptions.map { subscription ->
                val messageToPublish: String = if (messages.has(subscription.protocol)) {
                    messages[subscription.protocol].asString
                } else {
                    messages["default"].asString
                }
                logger.debug("Messages to publish: $messageToPublish")
                publishToSubscription(
                    subscription,
                    messageToPublish,
                    publishRequest.messageAttributes,
                    producerTemplate
                )
                    ?.exceptionally { logger.error("Error publishing to subscription: ${subscription.arn}", it) }
            }
            Future.succeededFuture<Any>()
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
            Future.failedFuture(ex)
        }
    }, {
        if (it.failed()) {
            logger.error("Error publishing message to topic: ${publishRequest.topicArn}", it.cause())
        }
    })
}

fun publishBasicMessageToTopic(
    publishRequest: PublishRequest,
    producerTemplate: ProducerTemplate,
    vertx: Vertx,
) {
    vertx.executeBlocking({ ->
        try {
            val subscriptions = getTopicSubscriptions(publishRequest.topicArn, vertx)
            logger.info("Publishing to topic: ${publishRequest.topicArn}")
            subscriptions.map { subscription ->
                publishToSubscription(subscription, publishRequest.message, publishRequest.messageAttributes, producerTemplate)
                    ?.exceptionally { logger.error("An error occurred when publishing to: ${subscription.endpoint}", it) }
            }
            Future.succeededFuture<Any>()
        } catch (ex : Exception) {
            logger.error("Error publishing message to topic: ${publishRequest.topicArn}", ex)
            Future.failedFuture(ex)
        }
    }, { result ->
        if (result.failed()) {
            logger.error("Error publishing message to topic: ${publishRequest.topicArn}", result.cause())
        }
    })
}

private fun publishToSubscription(
    subscription: Subscription,
    message: String,
    messageAttributes: Map<String, MessageAttribute> = mapOf(),
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
    messageAttributes: Map<String, MessageAttribute> = mapOf()
):Boolean {
    if (!subscription.subscriptionAttributes.containsKey("FilterPolicy")) {
        return true
    }
    val filterPolicyScope = subscription.subscriptionAttributes.get("FilterPolicyScope")
    return when (filterPolicyScope) {
        "MessageBody" -> matchesMessageBodyFilterPolicy(subscription, message)
        else -> matchesMessageAttributesFilterPolicy(subscription, messageAttributes)
    }
}

private fun matchesMessageAttributesFilterPolicy(
    subscription: Subscription,
    messageAttributes: Map<String, MessageAttribute> = mapOf()
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
    val gson = Gson()
    val messageJson = gson.fromJson(message, JsonObject::class.java)
    val matched = filterPolicySubscriptionAttribute?.let { MessageBodyFilterPolicy(it).matches(messageJson) } ?: true
    return matched
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
