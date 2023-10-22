package com.jameskbride.fakesns.routes

import io.vertx.ext.web.RoutingContext

val healthRoute: (RoutingContext) -> Unit = { ctx: RoutingContext ->
    ctx.request().response()
        .putHeader("context-type", "text/plain")
        .end("ok")
}