package com.jameskbride.localsns.verticles

import com.jameskbride.localsns.getDbOutputPath
import com.jameskbride.localsns.getSubscriptionsMap
import com.jameskbride.localsns.getTopicsMap
import com.jameskbride.localsns.models.Configuration
import com.typesafe.config.ConfigFactory
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.LocalDateTime
import java.time.ZoneOffset

class DatabaseVerticle: AbstractVerticle() {

    private val logger: Logger = LogManager.getLogger(DatabaseVerticle::class.java)

    override fun start(startPromise: Promise<Void>) {
        logger.info("Starting database service...")
        val config = ConfigFactory.load()
        val vertx = this.vertx
        val dbPath = getDbOutputPath(config)
        startPromise.complete()
        vertx.eventBus().consumer<String>("configChange") {
            val topics = getTopicsMap(vertx)
            val subscriptions = getSubscriptionsMap(vertx)
            val newConfig = Configuration(
                1,
                System.currentTimeMillis(),
                topics = topics!!.values.toList(),
                subscriptions = subscriptions!!.values.toList().flatten().distinct()
            )
            val jsonObject = JsonObject.mapFrom(newConfig)
            val prettyPrintedConfig = jsonObject.encodePrettily()
            val buffer = Buffer.buffer(prettyPrintedConfig)
            vertx.fileSystem()
                .writeFile(dbPath, buffer)
                .onComplete {
                    logger.info("Updated config at: $dbPath with: $jsonObject")
                    vertx.eventBus().publish("configChangeComplete", "configChangeComplete")
                }
                .onFailure {
                    logger.error("Unable to config at: $dbPath", it)
                }
        }
    }
}