package com.jameskbride.localsns.routes

import com.jameskbride.localsns.getDbOutputPath
import com.jameskbride.localsns.toJsonConfig
import com.typesafe.config.ConfigFactory
import io.vertx.core.Vertx
import io.vertx.ext.web.RoutingContext

val configRoute: (RoutingContext) -> Unit = { ctx: RoutingContext ->
    val config = ConfigFactory.load()
    val vertx = Vertx.vertx()
    val dbPath = getDbOutputPath(config)
    val dbFile = vertx.fileSystem().readFileBlocking(dbPath)
    val configuration = toJsonConfig(dbFile)
    ctx.request().response()
        .setStatusCode(200)
        .putHeader("context-type", "application/json")
        .end(configuration.toString())
}