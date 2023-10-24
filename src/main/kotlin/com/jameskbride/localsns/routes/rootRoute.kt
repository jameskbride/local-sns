import io.vertx.ext.web.RoutingContext

val rootRoute: (RoutingContext) -> Unit = { ctx: RoutingContext ->
    ctx.request().response()
        .putHeader("context-type", "text/plain")
        .end("Hello, vert.x")
}