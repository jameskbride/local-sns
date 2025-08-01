package com.jameskbride.localsns.api.topics

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.jameskbride.localsns.*
import com.jameskbride.localsns.models.Topic
import com.typesafe.config.ConfigFactory
import io.vertx.core.shareddata.LocalMap
import io.vertx.ext.web.RoutingContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.regex.Pattern

private val logger: Logger = LogManager.getLogger("TopicsApiRoutes")
private val gson = Gson()

data class CreateTopicRequest(val name: String)
data class UpdateTopicRequest(val name: String)
data class TopicResponse(val arn: String, val name: String)
data class ErrorResponse(val error: String, val message: String)

// GET /api/topics - List all topics
val listTopicsApiRoute: (RoutingContext) -> Unit = { ctx: RoutingContext ->
    try {
        val vertx = ctx.vertx()
        val topics = getTopicsMap(vertx)!!
        val topicResponses = topics.values.map { topic ->
            TopicResponse(arn = topic.arn, name = topic.name)
        }
        
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(200)
            .end(gson.toJson(topicResponses))
    } catch (ex: Exception) {
        logger.error("Error listing topics", ex)
        sendJsonError(ctx, "INTERNAL_ERROR", ex.message ?: "Internal server error", 500)
    }
}

// POST /api/topics - Create a new topic
val createTopicApiRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    try {
        val body = ctx.bodyAsString
        if (body.isNullOrBlank()) {
            sendJsonError(ctx, "INVALID_REQUEST", "Request body is required", 400)
            return@route
        }

        val request = try {
            gson.fromJson(body, CreateTopicRequest::class.java)
        } catch (ex: JsonSyntaxException) {
            sendJsonError(ctx, "INVALID_JSON", "Invalid JSON in request body", 400)
            return@route
        }

        if (request.name.isBlank()) {
            sendJsonError(ctx, "MISSING_PARAMETER", "Topic name is required", 400)
            return@route
        }

        if (!Pattern.matches(Topic.namePattern, request.name)) {
            sendJsonError(ctx, "INVALID_PARAMETER", "Invalid topic name: ${request.name}", 400)
            return@route
        }

        val vertx = ctx.vertx()
        val topics = getTopicsMap(vertx)!!
        val subscriptions = getSubscriptionsMap(vertx)!!
        
        // Check if topic already exists
        val existingTopic = topics.values.find { it.name == request.name }
        if (existingTopic != null) {
            val response = TopicResponse(arn = existingTopic.arn, name = existingTopic.name)
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(200)
                .end(gson.toJson(response))
            return@route
        }

        val config = ConfigFactory.load()
        val region = getAwsRegion(config)
        val accountId = getAwsAccountId(config)
        val newTopic = Topic(arn = "arn:aws:sns:$region:$accountId:${request.name}", name = request.name)

        logger.info("Creating topic via API: $newTopic")
        topics[newTopic.arn] = newTopic
        subscriptions[newTopic.arn] = listOf()
        vertx.eventBus().publish("configChange", "configChange")

        val response = TopicResponse(arn = newTopic.arn, name = newTopic.name)
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(201)
            .end(gson.toJson(response))
    } catch (ex: Exception) {
        logger.error("Error creating topic", ex)
        sendJsonError(ctx, "INTERNAL_ERROR", ex.message ?: "Internal server error", 500)
    }
}

// GET /api/topics/:arn - Get a specific topic by ARN
val getTopicApiRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    try {
        val topicArn = ctx.pathParam("arn")
        if (topicArn.isNullOrBlank()) {
            sendJsonError(ctx, "MISSING_PARAMETER", "Topic ARN is required", 400)
            return@route
        }

        if (!Pattern.matches(Topic.arnPattern, topicArn)) {
            sendJsonError(ctx, "INVALID_PARAMETER", "Invalid topic ARN: $topicArn", 400)
            return@route
        }

        val vertx = ctx.vertx()
        val topics = getTopicsMap(vertx)!!
        
        val topic = topics[topicArn]
        if (topic == null) {
            sendJsonError(ctx, "NOT_FOUND", "Topic not found: $topicArn", 404)
            return@route
        }

        val response = TopicResponse(arn = topic.arn, name = topic.name)
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(200)
            .end(gson.toJson(response))
    } catch (ex: Exception) {
        logger.error("Error getting topic", ex)
        sendJsonError(ctx, "INTERNAL_ERROR", ex.message ?: "Internal server error", 500)
    }
}

// PUT /api/topics/:arn - Update a topic (currently only supports name changes)
val updateTopicApiRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    try {
        val topicArn = ctx.pathParam("arn")
        if (topicArn.isNullOrBlank()) {
            sendJsonError(ctx, "MISSING_PARAMETER", "Topic ARN is required", 400)
            return@route
        }

        if (!Pattern.matches(Topic.arnPattern, topicArn)) {
            sendJsonError(ctx, "INVALID_PARAMETER", "Invalid topic ARN: $topicArn", 400)
            return@route
        }

        val body = ctx.bodyAsString
        if (body.isNullOrBlank()) {
            sendJsonError(ctx, "INVALID_REQUEST", "Request body is required", 400)
            return@route
        }

        val request = try {
            gson.fromJson(body, UpdateTopicRequest::class.java)
        } catch (ex: JsonSyntaxException) {
            sendJsonError(ctx, "INVALID_JSON", "Invalid JSON in request body", 400)
            return@route
        }

        if (request.name.isBlank()) {
            sendJsonError(ctx, "MISSING_PARAMETER", "Topic name is required", 400)
            return@route
        }

        if (!Pattern.matches(Topic.namePattern, request.name)) {
            sendJsonError(ctx, "INVALID_PARAMETER", "Invalid topic name: ${request.name}", 400)
            return@route
        }

        val vertx = ctx.vertx()
        val topics = getTopicsMap(vertx)!!
        val subscriptions = getSubscriptionsMap(vertx)!!
        
        val existingTopic = topics[topicArn]
        if (existingTopic == null) {
            sendJsonError(ctx, "NOT_FOUND", "Topic not found: $topicArn", 404)
            return@route
        }

        // Create a new topic with updated name but same ARN
        val config = ConfigFactory.load()
        val region = getAwsRegion(config)
        val accountId = getAwsAccountId(config)
        val newArn = "arn:aws:sns:$region:$accountId:${request.name}"
        val updatedTopic = Topic(arn = newArn, name = request.name)

        logger.info("Updating topic via API: $existingTopic -> $updatedTopic")
        
        // Remove old topic and add updated one
        topics.remove(topicArn)
        topics[updatedTopic.arn] = updatedTopic
        
        // Update subscriptions map
        val topicSubscriptions = subscriptions.remove(topicArn) ?: listOf()
        subscriptions[updatedTopic.arn] = topicSubscriptions
        
        vertx.eventBus().publish("configChange", "configChange")

        val response = TopicResponse(arn = updatedTopic.arn, name = updatedTopic.name)
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(200)
            .end(gson.toJson(response))
    } catch (ex: Exception) {
        logger.error("Error updating topic", ex)
        sendJsonError(ctx, "INTERNAL_ERROR", ex.message ?: "Internal server error", 500)
    }
}

// DELETE /api/topics/:arn - Delete a topic
val deleteTopicApiRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    try {
        val topicArn = ctx.pathParam("arn")
        if (topicArn.isNullOrBlank()) {
            sendJsonError(ctx, "MISSING_PARAMETER", "Topic ARN is required", 400)
            return@route
        }

        if (!Pattern.matches(Topic.arnPattern, topicArn)) {
            sendJsonError(ctx, "INVALID_PARAMETER", "Invalid topic ARN: $topicArn", 400)
            return@route
        }

        val vertx = ctx.vertx()
        val topics = getTopicsMap(vertx)!!
        val subscriptions = getSubscriptionsMap(vertx)!!
        
        val topic = topics[topicArn]
        if (topic == null) {
            sendJsonError(ctx, "NOT_FOUND", "Topic not found: $topicArn", 404)
            return@route
        }

        logger.info("Deleting topic via API: $topic")
        topics.remove(topicArn)
        subscriptions.remove(topicArn)
        vertx.eventBus().publish("configChange", "configChange")

        ctx.response()
            .setStatusCode(204)
            .end()
    } catch (ex: Exception) {
        logger.error("Error deleting topic", ex)
        sendJsonError(ctx, "INTERNAL_ERROR", ex.message ?: "Internal server error", 500)
    }
}

private fun sendJsonError(ctx: RoutingContext, error: String, message: String, statusCode: Int) {
    val errorResponse = ErrorResponse(error = error, message = message)
    ctx.response()
        .putHeader("Content-Type", "application/json")
        .setStatusCode(statusCode)
        .end(gson.toJson(errorResponse))
}
