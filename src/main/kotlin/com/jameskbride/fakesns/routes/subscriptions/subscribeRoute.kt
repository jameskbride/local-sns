package com.jameskbride.fakesns.routes.subscriptions

import com.jameskbride.fakesns.*
import com.jameskbride.fakesns.models.MessageAttribute
import com.jameskbride.fakesns.models.Subscription
import com.jameskbride.fakesns.models.Topic
import com.typesafe.config.ConfigFactory
import io.vertx.ext.web.RoutingContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.*
import java.util.regex.Pattern

val subscribeRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    val logger: Logger = LogManager.getLogger("subscribeRoute")
    val topicArn = getFormAttribute(ctx,"TopicArn")
    val endpoint = getFormAttribute(ctx,"Endpoint")
    val protocol = getFormAttribute(ctx,"Protocol")
    val attributes = ctx.request().formAttributes()
        .filter { it.key.startsWith("Attributes.entry") }
        .filterNot { it.key.matches(".*\\.DataType.*".toRegex()) }
    val vertx = ctx.vertx()
    val topicsMap = getTopicsMap(vertx)
    if (topicArn == null || protocol == null) {
        logAndReturnError(
            ctx,
            logger,
            "Invalid TopicArn or Protocol. topic: $topicArn, protocol: $protocol",
        )
        return@route
    }

    if (!Pattern.matches(Topic.arnPattern, topicArn)) {
        logAndReturnError(
            ctx,
            logger,
            "Subscribe: Invalid TopicArn. topic: $topicArn, endpoint: $endpoint"
        )
        return@route
    }

    if (!topicsMap!!.containsKey(topicArn)) {
        logAndReturnError(
            ctx,
            logger,
            "Invalid TopicArn: $topicArn",
        )
        return@route
    }

    val messageAttributes = MessageAttribute.parse(attributes)

    val subscriptions = getSubscriptionsMap(vertx)!!
    val owner = getAwsAccountId(config = ConfigFactory.load())
    val subscription = Subscription(
        arn = "$topicArn:${UUID.randomUUID()}",
        owner = owner,
        topicArn = topicArn,
        protocol = protocol,
        endpoint = endpoint,
        subscriptionAttributes = messageAttributes.map { mapOf(it.name to it.value) }.fold(mapOf()) { acc, map -> acc + map }
    )
    logger.info("Creating subscription: {}", subscription)
    val updatedSubscriptions = subscriptions.getOrDefault(topicArn, listOf()) + subscription
    subscriptions[topicArn] = updatedSubscriptions
    vertx.eventBus().publish("configChange", "configChange")
    ctx.request().response()
        .setStatusCode(200)
        .putHeader("context-type", "text/xml")
        .end(
            """
                      <SubscribeResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/">
                        <SubscribeResult>
                          <SubscriptionArn>${subscription.arn}</SubscriptionArn>
                        </SubscribeResult>
                        <ResponseMetadata>
                          <RequestId>${UUID.randomUUID()}</RequestId>
                        </ResponseMetadata>
                      </SubscribeResponse>
                    """.trimIndent()
        )
}