package com.jameskbride.fakesns.routes.subscriptions

import com.jameskbride.fakesns.NOT_FOUND
import com.jameskbride.fakesns.getFormAttribute
import com.jameskbride.fakesns.getSubscriptionsMap
import com.jameskbride.fakesns.logAndReturnError
import com.jameskbride.fakesns.models.Subscription
import io.vertx.ext.web.RoutingContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.*
import java.util.regex.Pattern

val unsubscribeRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    val logger: Logger = LogManager.getLogger("unsubscribeRoute")
    val vertx = ctx.vertx()
    val subscriptions = getSubscriptionsMap(vertx)
    val subscriptionArn = getFormAttribute(ctx, "SubscriptionArn")
    if (subscriptionArn == null) {
        logAndReturnError(ctx, logger, "SubscriptionArn is missing")
        return@route
    }

    if (!Pattern.matches(Subscription.arnPattern, subscriptionArn)) {
        logAndReturnError(ctx, logger, "SubscriptionArn is invalid")
        return@route
    }

    val subscription = subscriptions!!.values
        .fold(listOf<Subscription>()) { acc, subs -> acc + subs }
        .firstOrNull { it.arn == subscriptionArn }

    if (subscription == null) {
        val errorMessage = "Subscription not found: $subscriptionArn"
        logAndReturnError(ctx, logger, errorMessage, NOT_FOUND, 404)
        return@route
    }

    logger.info("Unsubscribing $subscriptionArn from ${subscription.topicArn}")
    val updatedSubscriptions = subscriptions.getOrDefault(subscriptionArn, listOf())
        .filter { it.arn != subscriptionArn }
    subscriptions[subscription.topicArn] = updatedSubscriptions
    vertx.eventBus().publish("configChange", "configChange")
    ctx.request().response()
        .putHeader("context-type", "text/xml")
        .setStatusCode(200)
        .end(
            """
              <UnsubscribeResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/">
                <ResponseMetadata>
                  <RequestId>
                    ${UUID.randomUUID()}
                  </RequestId>
                </ResponseMetadata>
              </UnsubscribeResponse>
            """.trimIndent()
        )
}