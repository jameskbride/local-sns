package com.jameskbride.localsns.routes.subscriptions

import com.jameskbride.localsns.*
import com.jameskbride.localsns.models.Subscription
import com.jameskbride.localsns.models.SubscriptionAttribute
import com.jameskbride.localsns.models.Topic
import com.typesafe.config.ConfigFactory
import io.vertx.ext.web.RoutingContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URL
import java.util.*
import java.util.regex.Pattern

val subscribeRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    val logger: Logger = LogManager.getLogger("subscribeRoute")
    val topicArn = getFormAttribute(ctx,"TopicArn")
    val endpoint = getFormAttribute(ctx,"Endpoint")
    val protocol = getFormAttribute(ctx,"Protocol")
    val attributes = ctx.request().formAttributes()
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
    logger.info("Attributes passed to subscribe: $attributes")
    val subscriptionAttributes:Map<String, String> = SubscriptionAttribute.parse(
        attributes.filter { it.key.startsWith("Attributes.entry") }
    )

    if (subscriptionAttributes.containsKey("RawMessageDelivery") && !listOf("true", "false").contains(subscriptionAttributes["RawMessageDelivery"])) {
        logAndReturnError(
            ctx,
            logger,
            "Invalid parameter: Attributes Reason: RawMessageDelivery: Invalid value ${subscriptionAttributes["RawMessageDelivery"]}. Must be true or false.",
        )
        return@route
    }

    val subscriptionEndpoint = if (protocol == "sqs" && (endpoint?.startsWith("http") == true || endpoint?.startsWith("https") == true)) {
        val url = URL(endpoint)
        val endpointProtocol = url.protocol
        val endpointHost = url.host
        val endpointPort = url.port
        val endpointPath = url.path
        val queueName = endpointPath.split("/").last()
        buildSqsEndpoint(queueName, endpointProtocol, endpointHost, endpointPort)
    } else {
        endpoint
    }

    val subscriptions = getSubscriptionsMap(vertx)!!
    val owner = getAwsAccountId(config = ConfigFactory.load())
    val subscription = Subscription(
        arn = "$topicArn:${UUID.randomUUID()}",
        owner = owner,
        topicArn = topicArn,
        protocol = protocol,
        endpoint = subscriptionEndpoint,
        subscriptionAttributes = subscriptionAttributes
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

private fun buildSqsEndpoint(
    queueName: String,
    endpointProtocol: String?,
    endpointHost: String?,
    endpointPort: Int
): String {
    val queryParams =
        "?accessKey=xxx&secretKey=xxx&region=us-east-1&trustAllCertificates=true&overrideEndpoint=true&uriEndpointOverride="
    return if (endpointPort > -1) {
        "aws2-sqs://$queueName$queryParams$endpointProtocol://$endpointHost:$endpointPort"
    } else {
        "aws2-sqs://$queueName$queryParams$endpointProtocol://$endpointHost"
    }
}
