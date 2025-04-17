package com.jameskbride.localsns.routes

import com.jameskbride.localsns.INTERNAL_ERROR
import com.jameskbride.localsns.logAndReturnError
import com.jameskbride.localsns.routes.subscriptions.*
import com.jameskbride.localsns.routes.topics.*
import io.vertx.ext.web.RoutingContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import rootRoute

val routeMapping = mapOf(
    "ListTopics" to listTopicsRoute,
    "DeleteTopic" to deleteTopicRoute,
    "CreateTopic" to createTopicRoute,
    "ListSubscriptions" to listSubscriptionsRoute,
    "ListSubscriptionsByTopic" to listSubscriptionsByTopicRoute,
    "Subscribe" to subscribeRoute,
    "Unsubscribe" to unsubscribeRoute,
    "GetSubscriptionAttributes" to getSubscriptionAttributesRoute,
    "SetSubscriptionAttributes" to setSubscriptionAttributesRoute,
    "Publish" to publishRoute,
    "PublishBatch" to publishBatchRoute,
)

val getRoute: (RoutingContext) -> Unit = { ctx: RoutingContext ->
    val logger: Logger = LogManager.getLogger("routes")
    try {
        val action = ctx.request().getFormAttribute("Action")
        val route = routeMapping.getOrDefault(action, rootRoute)
        route(ctx)
    } catch(ex: Exception) {
        logger.error(ex)
        logAndReturnError(ctx, logger, ex.message ?: INTERNAL_ERROR, INTERNAL_ERROR, 500)
    }
}