package com.jameskbride.localsns.routes.subscriptions

import com.jameskbride.localsns.getFormAttribute
import com.jameskbride.localsns.getSubscriptionsMap
import com.jameskbride.localsns.getTopicsMap
import com.jameskbride.localsns.logAndReturnError
import com.jameskbride.localsns.models.Topic
import io.vertx.ext.web.RoutingContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URLEncoder
import java.util.*
import java.util.regex.Pattern

val listSubscriptionsByTopicRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    val logger: Logger = LogManager.getLogger("listSubscriptionsByTopicRoute")
    val topicArn =getFormAttribute(ctx, "TopicArn")
    val vertx = ctx.vertx()
    val topicsMap = getTopicsMap(vertx)
    if (topicArn == null) {
        logAndReturnError(ctx, logger, "TopicArn is missing")
        return@route
    }

    if (!Pattern.matches(Topic.arnPattern, topicArn)) {
        logAndReturnError(ctx, logger, "Invalid TopicArn: $topicArn")
        return@route
    }

    if (!topicsMap!!.contains(topicArn)) {
        logAndReturnError(ctx, logger, "Invalid TopicArn: $topicArn")
        return@route
    }

    val sharedData = getSubscriptionsMap(vertx)
    val subscriptions = sharedData!!.getOrDefault(topicArn, listOf())
    val subscriptionsContent = subscriptions.map { subscription ->
        val endpoint = URLEncoder.encode(subscription.endpoint.orEmpty(), "UTF-8")
        """
            <member>
                <Owner>${subscription.owner}</Owner>
                <Protocol>${subscription.protocol}</Protocol>
                <Endpoint>${endpoint}</Endpoint>
                <SubscriptionArn>${subscription.arn}</SubscriptionArn>
                <TopicArn>${subscription.topicArn}</TopicArn>
            </member>
        """.trimIndent()
    }
    ctx.request().response()
        .putHeader("context-type", "text/xml")
        .setStatusCode(200)
        .end(
            """
              <ListSubscriptionsByTopicResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/">
                <ListSubscriptionsByTopicResult>
                  <Subscriptions>
                    $subscriptionsContent
                  </Subscriptions>
                </ListSubscriptionsByTopicResult>
                <ResponseMetadata>
                  <RequestId>${UUID.randomUUID()}</RequestId>
                </ResponseMetadata>
              </ListSubscriptionsByTopicResponse>
            """.trimIndent()
        )
}
