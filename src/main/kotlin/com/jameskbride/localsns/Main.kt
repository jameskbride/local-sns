package com.jameskbride.localsns

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jameskbride.localsns.models.Configuration
import com.typesafe.config.ConfigFactory
import io.vertx.core.AsyncResult
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import org.apache.camel.impl.DefaultCamelContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.time.LocalDateTime
import java.time.ZoneOffset

class Main {
    companion object {
        private val logger: Logger = LogManager.getLogger(Main::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            configureObjectMappers()
            val config = ConfigFactory.load()
            val vertx = Vertx.vertx()
            val dbPath = getDbPath(config)
            vertx.fileSystem()
                .readFile(dbPath)
                .onComplete { result ->
                    if (!result.succeeded()) {
                        logger.error("Failed to load configuration: " + result.cause())
                        logger.info("Creating an empty configuration...")
                        val configuration = Configuration(
                            version = 1,
                            timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
                        )
                        storeConfiguration(vertx, configuration)
                        start(vertx)
                    } else {
                        val configuration = readConfiguration(result)
                        logger.info("Configuration loaded successfully.")

                        storeConfiguration(vertx, configuration)
                        start(vertx)
                    }
                }
        }

        private fun start(vertx: Vertx) {
            val camelContext = DefaultCamelContext()
            camelContext.start()

            vertx.deployVerticle("com.jameskbride.localsns.verticles.MainVerticle")
            vertx.deployVerticle("com.jameskbride.localsns.verticles.DatabaseVerticle")
        }

        private fun storeConfiguration(vertx: Vertx, configuration: Configuration) {
            val topicSubscriptions = configuration.subscriptions.groupBy { it.topicArn }
            val topicsMap = getTopicsMap(vertx)!!
            val topics = configuration.topics
            topics.forEach { topic ->
                topicsMap[topic.arn] = topic
            }

            val subscriptionsMap = getSubscriptionsMap(vertx)!!
            topicSubscriptions.forEach { entry ->
                val topic = configuration.topics.first{ it.arn == entry.key }
                subscriptionsMap[topic.arn] = entry.value
            }
        }

        private fun readConfiguration(result: AsyncResult<Buffer>): Configuration {
            val configFile = result.result()
            val jsonConfig = JsonObject(configFile)
            logger.info("Loading configuration: $jsonConfig")

            return jsonConfig.mapTo(Configuration::class.java)
        }

        private fun configureObjectMappers() {
            val mapper = DatabindCodec.mapper()
            mapper.registerKotlinModule()

            val prettyMapper = DatabindCodec.prettyMapper()
            prettyMapper.registerKotlinModule()
        }
    }
}