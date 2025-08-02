package com.jameskbride.localsns.api.subscriptions

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.jameskbride.localsns.*
import com.jameskbride.localsns.models.Subscription
import com.jameskbride.localsns.models.Topic
import com.typesafe.config.ConfigFactory
import io.vertx.ext.web.RoutingContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URI
import java.util.*
import java.util.regex.Pattern

private val logger: Logger = LogManager.getLogger("SubscriptionsApiRoutes")
private val gson = Gson()

data class CreateSubscriptionRequest(
    val topicArn: String,
    val protocol: String,
    val endpoint: String,
    val attributes: Map<String, String> = mapOf()
)

data class UpdateSubscriptionRequest(
    val attributes: Map<String, String>
)

data class SubscriptionResponse(
    val arn: String,
    val owner: String,
    val topicArn: String,
    val protocol: String,
    val endpoint: String?,
    val attributes: Map<String, String>
)

data class ErrorResponse(val error: String, val message: String)

val listSubscriptionsApiRoute: (RoutingContext) -> Unit = { ctx: RoutingContext ->
    try {
        val vertx = ctx.vertx()
        val subscriptionsMap = getSubscriptionsMap(vertx)!!
        val allSubscriptions = subscriptionsMap.values.fold(listOf<Subscription>()) { acc, subs -> acc + subs }
        
        val subscriptionResponses = allSubscriptions.map { subscription ->
            SubscriptionResponse(
                arn = subscription.arn,
                owner = subscription.owner,
                topicArn = subscription.topicArn,
                protocol = subscription.protocol,
                endpoint = subscription.endpoint,
                attributes = subscription.subscriptionAttributes
            )
        }
        
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(200)
            .end(gson.toJson(subscriptionResponses))
    } catch (ex: Exception) {
        logger.error("Error listing subscriptions", ex)
        sendJsonError(ctx, "INTERNAL_ERROR", ex.message ?: "Internal server error", 500)
    }
}

// GET /api/topics/:topicArn/subscriptions - List subscriptions for a specific topic
val listSubscriptionsByTopicApiRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    try {
        val topicArn = ctx.pathParam("topicArn")
        if (topicArn.isNullOrBlank()) {
            sendJsonError(ctx, "MISSING_PARAMETER", "Topic ARN is required", 400)
            return@route
        }

        if (!Pattern.matches(Topic.arnPattern, topicArn)) {
            sendJsonError(ctx, "INVALID_PARAMETER", "Invalid topic ARN: $topicArn", 400)
            return@route
        }

        val vertx = ctx.vertx()
        val topicsMap = getTopicsMap(vertx)!!
        
        if (!topicsMap.containsKey(topicArn)) {
            sendJsonError(ctx, "NOT_FOUND", "Topic not found: $topicArn", 404)
            return@route
        }

        val subscriptionsMap = getSubscriptionsMap(vertx)!!
        val topicSubscriptions = subscriptionsMap.getOrDefault(topicArn, listOf())
        
        val subscriptionResponses = topicSubscriptions.map { subscription ->
            SubscriptionResponse(
                arn = subscription.arn,
                owner = subscription.owner,
                topicArn = subscription.topicArn,
                protocol = subscription.protocol,
                endpoint = subscription.endpoint,
                attributes = subscription.subscriptionAttributes
            )
        }
        
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(200)
            .end(gson.toJson(subscriptionResponses))
    } catch (ex: Exception) {
        logger.error("Error listing subscriptions for topic", ex)
        sendJsonError(ctx, "INTERNAL_ERROR", ex.message ?: "Internal server error", 500)
    }
}

// POST /api/subscriptions - Create a new subscription
val createSubscriptionApiRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    try {
        val body = ctx.body().asString()
        if (body.isNullOrBlank()) {
            sendJsonError(ctx, "INVALID_REQUEST", "Request body is required", 400)
            return@route
        }

        val request = try {
            gson.fromJson(body, CreateSubscriptionRequest::class.java)
        } catch (ex: JsonSyntaxException) {
            sendJsonError(ctx, "INVALID_JSON", "Invalid JSON in request body", 400)
            return@route
        }

        if (request.topicArn.isBlank() || request.protocol.isBlank() || request.endpoint.isBlank()) {
            sendJsonError(ctx, "MISSING_PARAMETER", "topicArn, protocol, and endpoint are required", 400)
            return@route
        }

        if (!Pattern.matches(Topic.arnPattern, request.topicArn)) {
            sendJsonError(ctx, "INVALID_PARAMETER", "Invalid topic ARN: ${request.topicArn}", 400)
            return@route
        }

        val vertx = ctx.vertx()
        val topicsMap = getTopicsMap(vertx)!!
        
        if (!topicsMap.containsKey(request.topicArn)) {
            sendJsonError(ctx, "NOT_FOUND", "Topic not found: ${request.topicArn}", 404)
            return@route
        }

        if (request.attributes.containsKey("RawMessageDelivery") && 
            !listOf("true", "false").contains(request.attributes["RawMessageDelivery"])) {
            sendJsonError(
                ctx, 
                "INVALID_PARAMETER", 
                "RawMessageDelivery must be 'true' or 'false', got: ${request.attributes["RawMessageDelivery"]}", 
                400
            )
            return@route
        }

        val subscriptionEndpoint = try {
            buildSubscriptionEndpointData(request.protocol, request.endpoint)
        } catch (e: Exception) {
            sendJsonError(ctx, "INVALID_PARAMETER", "Invalid endpoint: ${request.endpoint}", 400)
            return@route
        }

        val subscriptionsMap = getSubscriptionsMap(vertx)!!
        val config = ConfigFactory.load()
        val owner = getAwsAccountId(config)
        
        val subscription = Subscription(
            arn = "${request.topicArn}:${UUID.randomUUID()}",
            owner = owner,
            topicArn = request.topicArn,
            protocol = request.protocol,
            endpoint = subscriptionEndpoint,
            subscriptionAttributes = request.attributes
        )

        logger.info("Creating subscription via API: $subscription")
        val updatedSubscriptions = subscriptionsMap.getOrDefault(request.topicArn, listOf()) + subscription
        subscriptionsMap[request.topicArn] = updatedSubscriptions
        vertx.eventBus().publish("configChange", "configChange")

        val response = SubscriptionResponse(
            arn = subscription.arn,
            owner = subscription.owner,
            topicArn = subscription.topicArn,
            protocol = subscription.protocol,
            endpoint = subscription.endpoint,
            attributes = subscription.subscriptionAttributes
        )
        
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(201)
            .end(gson.toJson(response))
    } catch (ex: Exception) {
        logger.error("Error creating subscription", ex)
        sendJsonError(ctx, "INTERNAL_ERROR", ex.message ?: "Internal server error", 500)
    }
}

val getSubscriptionApiRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    try {
        val subscriptionArn = ctx.pathParam("arn")
        if (subscriptionArn.isNullOrBlank()) {
            sendJsonError(ctx, "MISSING_PARAMETER", "Subscription ARN is required", 400)
            return@route
        }

        if (!Pattern.matches(Subscription.arnPattern, subscriptionArn)) {
            sendJsonError(ctx, "INVALID_PARAMETER", "Invalid subscription ARN: $subscriptionArn", 400)
            return@route
        }

        val vertx = ctx.vertx()
        val subscriptionsMap = getSubscriptionsMap(vertx)!!
        
        val subscription = subscriptionsMap.values
            .fold(listOf<Subscription>()) { acc, subs -> acc + subs }
            .firstOrNull { it.arn == subscriptionArn }

        if (subscription == null) {
            sendJsonError(ctx, "NOT_FOUND", "Subscription not found: $subscriptionArn", 404)
            return@route
        }

        val response = SubscriptionResponse(
            arn = subscription.arn,
            owner = subscription.owner,
            topicArn = subscription.topicArn,
            protocol = subscription.protocol,
            endpoint = subscription.endpoint,
            attributes = subscription.subscriptionAttributes
        )
        
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(200)
            .end(gson.toJson(response))
    } catch (ex: Exception) {
        logger.error("Error getting subscription", ex)
        sendJsonError(ctx, "INTERNAL_ERROR", ex.message ?: "Internal server error", 500)
    }
}

val updateSubscriptionApiRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    try {
        val subscriptionArn = ctx.pathParam("arn")
        if (subscriptionArn.isNullOrBlank()) {
            sendJsonError(ctx, "MISSING_PARAMETER", "Subscription ARN is required", 400)
            return@route
        }

        if (!Pattern.matches(Subscription.arnPattern, subscriptionArn)) {
            sendJsonError(ctx, "INVALID_PARAMETER", "Invalid subscription ARN: $subscriptionArn", 400)
            return@route
        }

        val body = ctx.body().asString()
        if (body.isNullOrBlank()) {
            sendJsonError(ctx, "INVALID_REQUEST", "Request body is required", 400)
            return@route
        }

        val request = try {
            gson.fromJson(body, UpdateSubscriptionRequest::class.java)
        } catch (ex: JsonSyntaxException) {
            sendJsonError(ctx, "INVALID_JSON", "Invalid JSON in request body", 400)
            return@route
        }

        if (request.attributes.containsKey("RawMessageDelivery") && 
            !listOf("true", "false").contains(request.attributes["RawMessageDelivery"])) {
            sendJsonError(
                ctx, 
                "INVALID_PARAMETER", 
                "RawMessageDelivery must be 'true' or 'false', got: ${request.attributes["RawMessageDelivery"]}", 
                400
            )
            return@route
        }

        val vertx = ctx.vertx()
        val subscriptionsMap = getSubscriptionsMap(vertx)!!
        
        val subscription = subscriptionsMap.values
            .fold(listOf<Subscription>()) { acc, subs -> acc + subs }
            .firstOrNull { it.arn == subscriptionArn }

        if (subscription == null) {
            sendJsonError(ctx, "NOT_FOUND", "Subscription not found: $subscriptionArn", 404)
            return@route
        }

        val updatedSubscription = subscription.copy(
            subscriptionAttributes = subscription.subscriptionAttributes + request.attributes
        )

        logger.info("Updating subscription via API: $subscription -> $updatedSubscription")
        
        val topicSubscriptions = subscriptionsMap.getOrDefault(subscription.topicArn, listOf())
        val updatedSubscriptions = topicSubscriptions.filter { it.arn != subscriptionArn } + updatedSubscription
        subscriptionsMap[subscription.topicArn] = updatedSubscriptions
        vertx.eventBus().publish("configChange", "configChange")

        val response = SubscriptionResponse(
            arn = updatedSubscription.arn,
            owner = updatedSubscription.owner,
            topicArn = updatedSubscription.topicArn,
            protocol = updatedSubscription.protocol,
            endpoint = updatedSubscription.endpoint,
            attributes = updatedSubscription.subscriptionAttributes
        )
        
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(200)
            .end(gson.toJson(response))
    } catch (ex: Exception) {
        logger.error("Error updating subscription", ex)
        sendJsonError(ctx, "INTERNAL_ERROR", ex.message ?: "Internal server error", 500)
    }
}

val deleteSubscriptionApiRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    try {
        val subscriptionArn = ctx.pathParam("arn")
        if (subscriptionArn.isNullOrBlank()) {
            sendJsonError(ctx, "MISSING_PARAMETER", "Subscription ARN is required", 400)
            return@route
        }

        if (!Pattern.matches(Subscription.arnPattern, subscriptionArn)) {
            sendJsonError(ctx, "INVALID_PARAMETER", "Invalid subscription ARN: $subscriptionArn", 400)
            return@route
        }

        val vertx = ctx.vertx()
        val subscriptionsMap = getSubscriptionsMap(vertx)!!
        
        val subscription = subscriptionsMap.values
            .fold(listOf<Subscription>()) { acc, subs -> acc + subs }
            .firstOrNull { it.arn == subscriptionArn }

        if (subscription == null) {
            sendJsonError(ctx, "NOT_FOUND", "Subscription not found: $subscriptionArn", 404)
            return@route
        }

        logger.info("Deleting subscription via API: $subscription")
        
        val topicSubscriptions = subscriptionsMap.getOrDefault(subscription.topicArn, listOf())
        val updatedSubscriptions = topicSubscriptions.filter { it.arn != subscriptionArn }
        subscriptionsMap[subscription.topicArn] = updatedSubscriptions
        vertx.eventBus().publish("configChange", "configChange")

        ctx.response()
            .setStatusCode(204)
            .end()
    } catch (ex: Exception) {
        logger.error("Error deleting subscription", ex)
        sendJsonError(ctx, "INTERNAL_ERROR", ex.message ?: "Internal server error", 500)
    }
}

private fun buildSubscriptionEndpointData(protocol: String, endpoint: String): String {
    return if (protocol == "sqs" && (endpoint.startsWith("http") || endpoint.startsWith("https"))) {
        val url = URI(endpoint)
        val endpointProtocol = url.scheme
        val endpointHost = url.host
        val endpointPort = url.port
        val endpointPath = url.path
        val pathParts = endpointPath.split("/")
        val queueName = pathParts.last()
        val accountId = pathParts[pathParts.size - 2]
        buildSqsEndpoint(queueName, endpointProtocol, endpointHost, endpointPort, accountId)
    } else {
        endpoint
    }
}

private fun buildSqsEndpoint(
    queueName: String,
    endpointProtocol: String?,
    endpointHost: String?,
    endpointPort: Int,
    accountId: String?
): String {
    val hostAndPort = if (endpointPort > -1) {
        "$endpointProtocol://$endpointHost:$endpointPort"
    } else {
        "$endpointProtocol://$endpointHost"
    }
    val queryParams = mapOf(
        "accessKey" to "xxx",
        "secretKey" to "xxx",
        "region" to "us-east-1",
        "trustAllCertificates" to "true",
        "overrideEndpoint" to "true",
        "queueOwnerAWSAccountId" to (accountId ?: getAwsAccountId(ConfigFactory.load())),
        "uriEndpointOverride" to hostAndPort,
    )
        .map { (key, value) -> "$key=$value" }
        .joinToString("&", "?")
    return "aws2-sqs://$queueName$queryParams"
}

private fun sendJsonError(ctx: RoutingContext, error: String, message: String, statusCode: Int) {
    val errorResponse = ErrorResponse(error = error, message = message)
    ctx.response()
        .putHeader("Content-Type", "application/json")
        .setStatusCode(statusCode)
        .end(gson.toJson(errorResponse))
}
