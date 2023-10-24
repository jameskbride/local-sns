package com.jameskbride.localsns.verticles

import com.jameskbride.localsns.routes.getRoute
import com.jameskbride.localsns.routes.healthRoute
import com.typesafe.config.ConfigFactory
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
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
    router.route("/").handler(getRoute)
    router.route("/health").handler(healthRoute)

    val config = ConfigFactory.load()
    val httpInterface = System.getenv("HTTP_INTERFACE") ?: config.getString("http.interface")
    val httpPortEnv = System.getenv("HTTP_PORT")
    val port = if (httpPortEnv !== null) {
      Integer.parseInt(httpPortEnv)
    } else { config.getInt("http.port") }

    val socketAddress = inetSocketAddress(port, httpInterface)

    val server = vertx.createHttpServer()
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
