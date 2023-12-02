package com.jameskbride.localsns.routes.subscriptions

import com.jameskbride.localsns.getSubscriptionsMap
import com.jameskbride.localsns.models.Subscription
import io.vertx.ext.web.RoutingContext
import java.net.URLEncoder
import java.util.*

val listSubscriptionsRoute: (RoutingContext) -> Unit = { ctx: RoutingContext ->
    val vertx = ctx.vertx()
    val sharedData = getSubscriptionsMap(vertx)
    val subscriptions = sharedData!!.values.fold(listOf<Subscription>()) { acc, subscriptions -> acc + subscriptions }
    val subscriptionsContent = subscriptions.map{subscription ->
        val endpoint = subscription.xmlEncodeEndpointUrl()
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
              <ListSubscriptionsResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/">
                <ListSubscriptionsResult>
                  <Subscriptions>
                    $subscriptionsContent
                  </Subscriptions>
                </ListSubscriptionsResult>
                <ResponseMetadata>
                  <RequestId>${UUID.randomUUID()}</RequestId>
                </ResponseMetadata>
              </ListSubscriptionsResponse>
            """.trimIndent()
        )
}
