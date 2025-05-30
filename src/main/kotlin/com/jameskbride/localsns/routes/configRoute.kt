package com.jameskbride.localsns.routes

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.jameskbride.localsns.getDbOutputPath
import com.jameskbride.localsns.getDbPath
import com.jameskbride.localsns.models.Configuration
import com.jameskbride.localsns.toJsonConfig
import com.typesafe.config.ConfigFactory
import io.vertx.ext.web.RoutingContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.LocalDateTime
import java.time.ZoneOffset

val configRoute: (RoutingContext) -> Unit = { ctx: RoutingContext ->
    val logger: Logger = LogManager.getLogger("configRoute")
    val config = ConfigFactory.load()
    val vertx = ctx.vertx()
    val dbPath = if (vertx.fileSystem().existsBlocking(getDbOutputPath(config))) {
        getDbOutputPath(config)
    } else {
        getDbPath(config)
    }
    val configuration = try {
        val dbFile = vertx.fileSystem().readFileBlocking(dbPath)
        toJsonConfig(dbFile)
    } catch(e: Exception) {
        logger.error("Failed to load configuration: $e")
        createNewConfig()
    }

    ctx.request().response()
        .setStatusCode(200)
        .putHeader("context-type", "application/json")
        .end(configuration.toString())
}

fun createNewConfig(): JsonObject {
    val configuration = Configuration(
        version = 1,
        timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
    )
    val gson = Gson()
    return gson.fromJson(gson.toJson(configuration), JsonObject::class.java)
}