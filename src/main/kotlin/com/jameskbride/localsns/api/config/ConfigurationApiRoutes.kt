package com.jameskbride.localsns.api.config

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.jameskbride.localsns.*
import com.jameskbride.localsns.models.Configuration
import com.jameskbride.localsns.models.Subscription
import com.jameskbride.localsns.models.Topic
import com.typesafe.config.ConfigFactory
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.RoutingContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.LocalDateTime
import java.time.ZoneOffset

private val logger: Logger = LogManager.getLogger("ConfigurationApiRoutes")
private val gson = Gson()

data class UpdateConfigurationRequest(
    val topics: List<Topic>? = null,
    val subscriptions: List<Subscription>? = null
)
data class ConfigurationResponse(
    val version: Int,
    val timestamp: Long,
    val topics: List<Topic>,
    val subscriptions: List<Subscription>
)
data class ErrorResponse(val error: String, val message: String)

private fun parseSubscriptionAttributes(subObj: JsonObject): Map<String, String> {
    return if (subObj.has("subscriptionAttributes") && !subObj.get("subscriptionAttributes").isJsonNull) {
        val attributesObj = subObj.get("subscriptionAttributes").asJsonObject
        if (attributesObj.entrySet().isEmpty()) {
            mapOf()
        } else {
            attributesObj.entrySet().associate { it.key to it.value.asString }
        }
    } else mapOf()
}

private fun parseConfigurationFromJson(jsonConfig: JsonObject): Configuration {
    return Configuration(
        version = jsonConfig.get("version")?.asInt ?: 1,
        timestamp = jsonConfig.get("timestamp")?.asLong ?: System.currentTimeMillis(),
        topics = if (jsonConfig.has("topics")) {
            jsonConfig.getAsJsonArray("topics").map { topicElement ->
                val topicObj = topicElement.asJsonObject
                Topic(
                    arn = topicObj.get("arn").asString,
                    name = topicObj.get("name").asString
                )
            }
        } else listOf(),
        subscriptions = if (jsonConfig.has("subscriptions")) {
            jsonConfig.getAsJsonArray("subscriptions").map { subElement ->
                val subObj = subElement.asJsonObject
                Subscription(
                    topicArn = subObj.get("topicArn").asString,
                    arn = subObj.get("arn").asString,
                    owner = subObj.get("owner").asString,
                    protocol = subObj.get("protocol").asString,
                    endpoint = subObj.get("endpoint").asString,
                    subscriptionAttributes = parseSubscriptionAttributes(subObj)
                )
            }
        } else listOf()
    )
}

val getConfigurationApiRoute: (RoutingContext) -> Unit = { ctx: RoutingContext ->
    try {
        val vertx = ctx.vertx()
        val config = ConfigFactory.load()
        val dbPath = getDbPath(config)
        
        val configuration = try {
            val dbFile = vertx.fileSystem().readFileBlocking(dbPath)
            val jsonConfig = toJsonConfig(dbFile)
            parseConfigurationFromJson(jsonConfig)
        } catch (e: Exception) {
            logger.warn("Failed to load configuration file, creating new one: $e")
            Configuration(
                version = 1,
                timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            )
        }
        
        val response = ConfigurationResponse(
            version = configuration.version,
            timestamp = configuration.timestamp,
            topics = configuration.topics,
            subscriptions = configuration.subscriptions
        )
        
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(200)
            .end(gson.toJson(response))
    } catch (ex: Exception) {
        logger.error("Error getting configuration", ex)
        sendJsonError(ctx, "INTERNAL_ERROR", ex.message ?: "Internal server error", 500)
    }
}

val updateConfigurationApiRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    try {
        val body = ctx.bodyAsString
        if (body.isNullOrBlank()) {
            sendJsonError(ctx, "INVALID_REQUEST", "Request body is required", 400)
            return@route
        }

        val request = try {
            gson.fromJson(body, UpdateConfigurationRequest::class.java)
        } catch (ex: JsonSyntaxException) {
            sendJsonError(ctx, "INVALID_JSON", "Invalid JSON in request body", 400)
            return@route
        }

        val vertx = ctx.vertx()
        val config = ConfigFactory.load()
        val dbPath = getDbPath(config)

        val currentConfiguration = try {
            val dbFile = vertx.fileSystem().readFileBlocking(dbPath)
            val jsonConfig = toJsonConfig(dbFile)
            parseConfigurationFromJson(jsonConfig)
        } catch (e: Exception) {
            Configuration(
                version = 1,
                timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            )
        }
        
        val updatedConfiguration = Configuration(
            version = currentConfiguration.version + 1,
            timestamp = System.currentTimeMillis(),
            topics = request.topics ?: currentConfiguration.topics,
            subscriptions = request.subscriptions ?: currentConfiguration.subscriptions
        )
        
        val topicsMap = getTopicsMap(vertx)!!
        val subscriptionsMap = getSubscriptionsMap(vertx)!!
        
        topicsMap.clear()
        subscriptionsMap.clear()
        
        updatedConfiguration.topics.forEach { topic ->
            topicsMap[topic.arn] = topic
        }
        
        val subscriptionsByTopic = updatedConfiguration.subscriptions.groupBy { it.topicArn }
        updatedConfiguration.topics.forEach { topic ->
            subscriptionsMap[topic.arn] = subscriptionsByTopic[topic.arn] ?: listOf()
        }
        
        val jsonObject = gson.toJson(updatedConfiguration)
        val buffer = Buffer.buffer(jsonObject)
        vertx.fileSystem().writeFileBlocking(getDbOutputPath(config), buffer)
        
        vertx.eventBus().publish("configChange", "configChange")
        
        val response = ConfigurationResponse(
            version = updatedConfiguration.version,
            timestamp = updatedConfiguration.timestamp,
            topics = updatedConfiguration.topics,
            subscriptions = updatedConfiguration.subscriptions
        )
        
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(200)
            .end(gson.toJson(response))
    } catch (ex: Exception) {
        logger.error("Error updating configuration", ex)
        sendJsonError(ctx, "INTERNAL_ERROR", ex.message ?: "Internal server error", 500)
    }
}

val resetConfigurationApiRoute: (RoutingContext) -> Unit = { ctx: RoutingContext ->
    try {
        val vertx = ctx.vertx()
        val config = ConfigFactory.load()
        
        val newConfiguration = Configuration(
            version = 1,
            timestamp = System.currentTimeMillis()
        )
        
        val topicsMap = getTopicsMap(vertx)!!
        val subscriptionsMap = getSubscriptionsMap(vertx)!!
        topicsMap.clear()
        subscriptionsMap.clear()
        
        val jsonObject = gson.toJson(newConfiguration)
        val buffer = Buffer.buffer(jsonObject)
        vertx.fileSystem().writeFileBlocking(getDbOutputPath(config), buffer)
        
        vertx.eventBus().publish("configChange", "configChange")
        
        ctx.response()
            .setStatusCode(204)
            .end()
    } catch (ex: Exception) {
        logger.error("Error resetting configuration", ex)
        sendJsonError(ctx, "INTERNAL_ERROR", ex.message ?: "Internal server error", 500)
    }
}

val createConfigurationBackupApiRoute: (RoutingContext) -> Unit = { ctx: RoutingContext ->
    try {
        val vertx = ctx.vertx()
        val config = ConfigFactory.load()
        val dbPath = getDbPath(config)

        val configuration = try {
            val dbFile = vertx.fileSystem().readFileBlocking(dbPath)
            val jsonConfig = toJsonConfig(dbFile)
            parseConfigurationFromJson(jsonConfig)
        } catch (e: Exception) {
            Configuration(
                version = 1,
                timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            )
        }
        
        val backupTimestamp = System.currentTimeMillis()
        val backupConfiguration = configuration.copy(timestamp = backupTimestamp)
        val backupPath = "${getDbOutputPath(config)}.backup.$backupTimestamp"
        
        val jsonObject = gson.toJson(backupConfiguration)
        val buffer = Buffer.buffer(jsonObject)
        vertx.fileSystem().writeFileBlocking(backupPath, buffer)
        
        val response = mapOf(
            "message" to "Backup created successfully",
            "backupPath" to backupPath,
            "timestamp" to backupTimestamp
        )
        
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(201)
            .end(gson.toJson(response))
    } catch (ex: Exception) {
        logger.error("Error creating configuration backup", ex)
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
