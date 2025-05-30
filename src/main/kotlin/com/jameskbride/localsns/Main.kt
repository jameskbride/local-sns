package com.jameskbride.localsns

import com.typesafe.config.ConfigFactory
import io.vertx.core.DeploymentOptions
import io.vertx.core.ThreadingModel
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.eventbus.EventBusOptions
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class Main {
    companion object {
        private val logger: Logger = LogManager.getLogger(Main::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            configureObjectMappers()
            val config = ConfigFactory.load()
            val version = config.getValue("version").unwrapped()
            logger.info("Starting local-sns-$version")
            val httpInterface = getHttpInterface(config)
            val port = getPort(config)
            logger.info("Health endpoint: http://$httpInterface:$port/health")
            logger.info("Configuration endpoint: http://${httpInterface}:$port/config")
            val vertx = Vertx.vertx()
            start(vertx)
        }

        private fun start(vertx: Vertx) {
            vertx.deployVerticle("com.jameskbride.localsns.verticles.MainVerticle")
            val options = DeploymentOptions().setThreadingModel(ThreadingModel.WORKER)
            vertx.deployVerticle("com.jameskbride.localsns.verticles.DatabaseVerticle", options)
                .onComplete {
                    vertx.eventBus().publish("loadConfig", null)
                }
        }
    }
}