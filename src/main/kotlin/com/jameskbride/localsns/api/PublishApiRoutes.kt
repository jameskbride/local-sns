package com.jameskbride.localsns.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.jameskbride.localsns.getTopicsMap
import com.jameskbride.localsns.models.MessageAttribute
import com.jameskbride.localsns.models.Topic
import com.jameskbride.localsns.topics.PublishRequest
import com.jameskbride.localsns.topics.getTopicArn
import com.jameskbride.localsns.topics.publishBasicMessageToTopic
import com.jameskbride.localsns.topics.publishJsonStructure
import io.vertx.ext.web.RoutingContext
import org.apache.camel.impl.DefaultCamelContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URLDecoder
import java.util.*
import java.util.regex.Pattern

private val logger: Logger = LogManager.getLogger("PublishApiRoutes")
private val gson = GsonBuilder().disableHtmlEscaping().create()

data class PublishApiRequest(
    val topicArn: String? = null,
    val targetArn: String? = null,
    val message: JsonElement? = null,
    val messageStructure: String? = null,
    val messageAttributes: Map<String, MessageAttribute>? = null
)

data class PublishApiResponse(
    val messageId: String,
    val topicArn: String
)

val publishMessageApiRoute: (RoutingContext) -> Unit = lambda@{ ctx ->
    try {
        val body = ctx.body().asString()
        if (body.isNullOrBlank()) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(gson.toJson(mapOf("error" to "Request body is required")))
            return@lambda
        }

        val request = try {
            gson.fromJson(body, PublishApiRequest::class.java)
        } catch (e: JsonSyntaxException) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(gson.toJson(mapOf("error" to "Invalid JSON format")))
            return@lambda
        }

        val pathTopicArn = ctx.pathParam("topicArn")?.let { URLDecoder.decode(it, "UTF-8") }
        val topicArn = getTopicArn(
            pathTopicArn ?: request.topicArn,
            request.targetArn
        )

        if (topicArn == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(gson.toJson(mapOf("error" to "Either topicArn or targetArn is required")))
            return@lambda
        }

        if (!Pattern.matches(Topic.arnPattern, topicArn)) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(gson.toJson(mapOf("error" to "Invalid TopicArn or TargetArn format: $topicArn")))
            return@lambda
        }

        val topicsMap = getTopicsMap(ctx.vertx())
        if (topicsMap == null || !topicsMap.contains(topicArn)) {
            ctx.response()
                .setStatusCode(404)
                .putHeader("Content-Type", "application/json")
                .end(gson.toJson(mapOf("error" to "Topic not found: $topicArn")))
            return@lambda
        }

        if (request.messageStructure != null && request.messageStructure != "json") {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(gson.toJson(mapOf("error" to "MessageStructure must be 'json' if specified")))
            return@lambda
        }

        if (request.message == null || request.message.isJsonNull) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(gson.toJson(mapOf("error" to "Message is required")))
            return@lambda
        }

        // Convert message to string format for processing
        val messageAsString = if (request.message.isJsonPrimitive && request.message.asJsonPrimitive.isString) {
            // It's already a string
            val stringValue = request.message.asString
            if (stringValue.isBlank()) {
                ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(gson.toJson(mapOf("error" to "Message cannot be empty")))
                return@lambda
            }
            stringValue
        } else {
            // It's a JSON object/array, convert to JSON string
            gson.toJson(request.message)
        }

        val messageAttributes = request.messageAttributes ?: emptyMap()
        
        val publishRequest = PublishRequest(
            message = messageAsString,
            messageAttributes = messageAttributes,
            topicArn = topicArn
        )

        if (request.messageStructure == "json") {
            try {
                val messages = gson.fromJson(messageAsString, JsonObject::class.java)
                if (!messages.has("default")) {
                    ctx.response()
                        .setStatusCode(400)
                        .putHeader("Content-Type", "application/json")
                        .end(gson.toJson(mapOf("error" to "Attribute 'default' is required when messageStructure is 'json'")))
                    return@lambda
                }
            } catch (e: JsonSyntaxException) {
                ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(gson.toJson(mapOf("error" to "Invalid JSON in message when messageStructure is 'json'")))
                return@lambda
            }
        }

        val camelContext = DefaultCamelContext()
        camelContext.start()

        if (request.messageStructure == "json") {
            publishJsonStructure(
                publishRequest,
                camelContext.createProducerTemplate(),
                ctx.vertx()
            )
        } else {
            publishBasicMessageToTopic(
                publishRequest,
                camelContext.createProducerTemplate(),
                ctx.vertx()
            )
        }

        val messageId = UUID.randomUUID().toString()
        val response = PublishApiResponse(messageId, topicArn)

        ctx.response()
            .setStatusCode(200)
            .putHeader("Content-Type", "application/json")
            .end(gson.toJson(response))

        logger.info("Successfully published message to topic: $topicArn with messageId: $messageId")

    } catch (e: Exception) {
        logger.error("Error publishing message", e)
        ctx.response()
            .setStatusCode(500)
            .putHeader("Content-Type", "application/json")
            .end(gson.toJson(mapOf("error" to "Internal server error: ${e.message}")))
    }
}
