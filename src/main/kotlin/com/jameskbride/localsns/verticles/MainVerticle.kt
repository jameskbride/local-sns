package com.jameskbride.localsns.verticles

import com.jameskbride.localsns.api.config.*
import com.jameskbride.localsns.api.publishMessageApiRoute
import com.jameskbride.localsns.api.subscriptions.*
import com.jameskbride.localsns.api.topics.*
import com.jameskbride.localsns.getHttpInterface
import com.jameskbride.localsns.getPort
import com.jameskbride.localsns.routes.configRoute
import com.jameskbride.localsns.routes.getRoute
import com.jameskbride.localsns.routes.healthRoute
import com.typesafe.config.ConfigFactory
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.net.SocketAddress.inetSocketAddress
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class MainVerticle : AbstractVerticle() {

  private val logger: Logger = LogManager.getLogger(MainVerticle::class.java)

  override fun start(startPromise: Promise<Void>) {
    val router = Router.router(vertx)
    router.route().handler(BodyHandler.create())
    
    // AWS SNS compatible routes (XML)
    router.route("/").handler(getRoute)
    router.route("/health").handler(healthRoute)
    router.route("/config").handler(configRoute)
    
    // Configuration API routes
    router.get("/api/config").handler(getConfigurationApiRoute)
    router.put("/api/config").handler(updateConfigurationApiRoute)
    router.delete("/api/config").handler(resetConfigurationApiRoute)
    router.post("/api/config/backup").handler(createConfigurationBackupApiRoute)
    
    // JSON REST API routes
    router.get("/api/topics").handler(listTopicsApiRoute)
    router.post("/api/topics").handler(createTopicApiRoute)
    router.get("/api/topics/:arn").handler(getTopicApiRoute)
    router.put("/api/topics/:arn").handler(updateTopicApiRoute)
    router.delete("/api/topics/:arn").handler(deleteTopicApiRoute)
    
    // Subscriptions API routes
    router.get("/api/subscriptions").handler(listSubscriptionsApiRoute)
    router.get("/api/topics/:topicArn/subscriptions").handler(listSubscriptionsByTopicApiRoute)
    router.post("/api/subscriptions").handler(createSubscriptionApiRoute)
    router.get("/api/subscriptions/:arn").handler(getSubscriptionApiRoute)
    router.put("/api/subscriptions/:arn").handler(updateSubscriptionApiRoute)
    router.delete("/api/subscriptions/:arn").handler(deleteSubscriptionApiRoute)
    
    // Publish API routes
    router.post("/api/topics/:topicArn/publish").handler(publishMessageApiRoute)
    router.post("/api/publish").handler(publishMessageApiRoute)

    val config = ConfigFactory.load()
    val httpInterface = getHttpInterface(config)
    val port = getPort(config)

    val socketAddress = inetSocketAddress(port, httpInterface)

    val server = vertx.createHttpServer(HttpServerOptions().setMaxFormAttributeSize(-1))
    server.requestHandler(router).listen(socketAddress) { http ->
      if (http.succeeded()) {
        logger.info("HTTP server started at $httpInterface:$port")
        startPromise.complete()
      } else {
        logger.error("Failed to start server $httpInterface:$port")
        http.cause().printStackTrace()
        startPromise.fail(http.cause())
      }
    }
  }
}
