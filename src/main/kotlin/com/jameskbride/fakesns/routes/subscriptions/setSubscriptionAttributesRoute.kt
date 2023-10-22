package com.jameskbride.fakesns.routes.subscriptions

import com.jameskbride.fakesns.*
import com.jameskbride.fakesns.models.Subscription
import io.vertx.ext.web.RoutingContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.*

val setSubscriptionAttributesRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    val logger: Logger = LogManager.getLogger("setSubscriptionAttributesRoute")
    val vertx = ctx.vertx()
    val subscriptionsMap = getSubscriptionsMap(vertx)!!
    val subscriptionArn = getFormAttribute(ctx, "SubscriptionArn")
    val attributeName = getFormAttribute(ctx, "AttributeName")
    val attributeValue = getFormAttribute(ctx, "AttributeValue")

    if (subscriptionArn == null || attributeName == null || attributeValue == null) {
        logAndReturnError(
            ctx,
            logger,
            "SubscriptionArn, AttributeName, AttributeValue are required. SubscriptionArn: $subscriptionArn",
            INVALID_PARAMETER,
            statusCode = 400
        )
        return@route
    }

    val subscription = subscriptionsMap.values
        .fold (listOf<Subscription>()) { acc, subscriptions -> acc + subscriptions }
        .firstOrNull { it.arn == subscriptionArn }
    if (subscription == null) {
        logAndReturnError(ctx, logger, "Invalid SubscriptionArn: $subscriptionArn", NOT_FOUND, 404)
        return@route
    }

    val updatedAttributes = subscription.subscriptionAttributes + mapOf(
        attributeName to attributeValue
    )
    val updatedSubscription = subscription.copy(subscriptionAttributes = updatedAttributes)
    val updatedSubscriptions = subscriptionsMap.getOrDefault(subscription.topicArn, listOf()).filter { sub ->
        sub.arn != updatedSubscription.arn
    } + listOf(updatedSubscription)
    subscriptionsMap[subscription.topicArn] = updatedSubscriptions
    ctx.request().response()
        .putHeader("context-type", "text/xml")
        .setStatusCode(200)
        .end(
            """
                <SetSubscriptionAttributesResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/"> 
                  <ResponseMetadata>
                    <RequestId>${UUID.randomUUID()}</RequestId>
                  </ResponseMetadata> 
                </SetSubscriptionAttributesResponse> 
            """.trimIndent()
        )
}