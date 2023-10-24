package com.jameskbride.localsns.routes

import com.jameskbride.localsns.routes.subscriptions.*
import com.jameskbride.localsns.routes.topics.createTopicRoute
import com.jameskbride.localsns.routes.topics.deleteTopicRoute
import com.jameskbride.localsns.routes.topics.listTopicsRoute
import com.jameskbride.localsns.routes.topics.publishRoute
import io.vertx.ext.web.RoutingContext
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
)

val getRoute: (RoutingContext) -> Unit = { ctx: RoutingContext ->
    val action = ctx.request().getFormAttribute("Action")
    val route = routeMapping.getOrDefault(action, rootRoute)
    route(ctx)
}