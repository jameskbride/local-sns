package com.jameskbride.localsns.routes.topics

import com.jameskbride.localsns.NOT_FOUND
import com.jameskbride.localsns.getFormAttribute
import com.jameskbride.localsns.getTopicsMap
import com.jameskbride.localsns.logAndReturnError
import com.jameskbride.localsns.models.Topic
import io.vertx.ext.web.RoutingContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.*
import java.util.regex.Pattern

val publishBatchRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    val logger: Logger = LogManager.getLogger("publishBatchRoute")
    val topicArn = getFormAttribute(ctx, "TopicArn")

    if (topicArn == null) {
        val errorMessage =
            "Either TopicArn or TargetArn is required. TopicArn: $topicArn"
        logAndReturnError(ctx, logger, errorMessage)
        return@route
    }

    if (!Pattern.matches(Topic.arnPattern, topicArn)) {
        logAndReturnError(ctx, logger, "Invalid TopicARN or TargetArn: $topicArn")
        return@route
    }

    val vertx = ctx.vertx()
    val topicsMap = getTopicsMap(vertx)!!
    if (!topicsMap.contains(topicArn)) {
        logAndReturnError(ctx, logger, "Invalid TopicARN or TargetArn: $topicArn", NOT_FOUND, 404)
        return@route
    }

    ctx.request().response()
        .putHeader("context-type", "text/xml")
        .setStatusCode(200)
        .end(
            """
              <PublishBatchResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/">
                <ResponseMetadata>
                  <RequestId>${UUID.randomUUID()}</RequestId>
                </ResponseMetadata>
              </PublishResponse>
            """.trimIndent()
        )
}