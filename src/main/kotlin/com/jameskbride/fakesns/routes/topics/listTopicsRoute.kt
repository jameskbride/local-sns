package com.jameskbride.fakesns.routes.topics

import com.jameskbride.fakesns.getTopicsMap
import io.vertx.ext.web.RoutingContext
import java.util.*

val listTopicsRoute: (RoutingContext) -> Unit = { ctx: RoutingContext ->
    val vertx = ctx.vertx()
    val sharedData = getTopicsMap(vertx)!!
    val topics = sharedData.values
    ctx.request().response()
        .putHeader("context-type", "text/xml")
        .setStatusCode(200)
        .end(
            """
              <ListTopicsResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/">
                <ListTopicsResult>
                  <Topics>${topics.map{topic -> "<member><TopicArn>${topic.arn}</TopicArn></member>"}}</Topics>
                </ListTopicsResult>
                <ResponseMetadata>
                  <RequestId>${UUID.randomUUID()}</RequestId>
                </ResponseMetadata>
              </ListTopicsResponse>
            """.trimIndent()
        )
}
