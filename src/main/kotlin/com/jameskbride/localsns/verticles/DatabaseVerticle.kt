package com.jameskbride.localsns.verticles

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.jameskbride.localsns.*
import com.jameskbride.localsns.models.Configuration
import com.jameskbride.localsns.models.Subscription
import com.jameskbride.localsns.models.Topic
import com.jameskbride.localsns.serialization.ConfigurationTypeAdapter
import com.jameskbride.localsns.serialization.SubscriptionTypeAdapter
import com.jameskbride.localsns.serialization.TopicTypeAdapter
import com.typesafe.config.ConfigFactory
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.LocalDateTime
import java.time.ZoneOffset

class DatabaseVerticle: AbstractVerticle() {

    private val logger: Logger = LogManager.getLogger(DatabaseVerticle::class.java)

    override fun start(startPromise: Promise<Void>) {
        vertx.eventBus().consumer<String>("loadConfig") {
            logger.info("Loading configuration...")
            val config = ConfigFactory.load()
            val vertx = this.vertx
            vertx.fileSystem()
                .readFile(getDbPath(config))
                .recover { throwable ->
                    logger.error("Failed to load configuration: ${throwable.message}")
                    logger.info("Creating an empty configuration...")
                    val configuration = Configuration(
                        version = 1,
                        timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
                    )
                    Future.succeededFuture(
                        bufferFromConfiguration(configuration)
                    )

                }.compose { buffer ->
                    val configuration = readConfiguration(buffer)
                    logger.info("Configuration loaded successfully.")
                    storeConfiguration(vertx, configuration).map(configuration)
                }
                .onFailure { throwable ->
                    logger.error("Failed to initialize the application: ${throwable.message}")
                }
        }
        vertx.eventBus().consumer<String>("configChange") {
            val topics = getTopicsMap(vertx)
            val subscriptions = getSubscriptionsMap(vertx)
            val config = ConfigFactory.load()
            val dbPath = getDbOutputPath(config)
            val newConfig = Configuration(
                1,
                System.currentTimeMillis(),
                topics = topics!!.values.toList(),
                subscriptions = subscriptions!!.values.toList().flatten().distinct()
            )
            val gson = Gson()
            val jsonObject = gson.toJson(newConfig)
            val buffer = Buffer.buffer(jsonObject)
            vertx.fileSystem()
                .writeFile(dbPath, buffer)
                .onComplete {
                    logger.info("Updated config at: $dbPath with: $jsonObject")
                    vertx.eventBus().publish("configChangeComplete", "configChangeComplete")
                }
                .onFailure {
                    logger.error("Unable to save config to: $dbPath", it)
                }
        }

        startPromise.complete()
    }

    private fun storeConfiguration(vertx: Vertx, configuration: Configuration): Future<Void> {
        return try {
            val topicSubscriptions = configuration.subscriptions.groupBy { it.topicArn }
            val topicsMap = getTopicsMap(vertx)!!
            val topics = configuration.topics
            topics.forEach { topic ->
                topicsMap[topic.arn] = topic
            }

            val subscriptionsMap = getSubscriptionsMap(vertx)!!
            topicSubscriptions.forEach { entry ->
                val topic = configuration.topics.first { it.arn == entry.key }
                subscriptionsMap[topic.arn] = entry.value
            }

           Future.succeededFuture()
        } catch (e: Exception) {
            Future.failedFuture(e)
        }
    }

    private fun readConfiguration(result: Buffer): Configuration {
        val jsonString = result.toString()
        logger.info("Loading configuration: $jsonString")
        val gson = GsonBuilder()
            .registerTypeAdapter(Configuration::class.java, ConfigurationTypeAdapter())
            .registerTypeAdapter(Subscription::class.java, SubscriptionTypeAdapter())
            .registerTypeAdapter(Topic::class.java, TopicTypeAdapter())
            .create()
        val configuration = gson.fromJson(jsonString, Configuration::class.java)

        return configuration
    }
}