package com.jameskbride.fakesns.routes.subscriptions

import com.jameskbride.fakesns.NOT_FOUND
import com.jameskbride.fakesns.getFormAttribute
import com.jameskbride.fakesns.getSubscriptionsMap
import com.jameskbride.fakesns.logAndReturnError
import com.jameskbride.fakesns.models.Subscription
import io.vertx.ext.web.RoutingContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.net.URLEncoder
import java.util.*
import java.util.regex.Pattern

var getSubscriptionAttributesRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    val logger: Logger = LogManager.getLogger("getSubscriptionAttributesRoute")
    val vertx = ctx.vertx()
    val subscriptionArn = getFormAttribute(ctx, "SubscriptionArn")

    if (subscriptionArn == null) {
        logAndReturnError(ctx, logger, "SubscriptionArn is missing")
        return@route
    }

    if (!Pattern.matches(Subscription.arnPattern, subscriptionArn)) {
        logAndReturnError(ctx, logger, "Invalid SubscriptionArn: $subscriptionArn")
        return@route
    }

    val subscriptionsMap = getSubscriptionsMap(vertx)
    val subscription = subscriptionsMap!!.values
        .fold (listOf<Subscription>()) { acc, subscriptions -> acc + subscriptions }
        .firstOrNull { it.arn == subscriptionArn }

    if (subscription == null) {
        logAndReturnError(ctx, logger, "Invalid SubscriptionArn: $subscriptionArn", NOT_FOUND, 404)
        return@route
    }

    val endpoint = URLEncoder.encode(subscription.endpoint.orEmpty(), "UTF-8")
    val mergedAttributes = mapOf(
        "SubscriptionArn" to subscription.arn,
        "TopicArn" to subscription.topicArn,
        "Owner" to subscription.owner,
        "Endpoint" to endpoint,
        "Protocol" to subscription.protocol,
        "PendingConfirmation" to "false",
        "SubscriptionPrincipal" to "",
        "ConfirmationWasAuthenticated" to "false",
        "RawMessageDelivery" to "false"
    ) + subscription.subscriptionAttributes
    val entries = mergedAttributes.map {
        """
           <entry> 
             <key>${it.key}</key>
             <value>${it.value}</value> 
           </entry> 
        """.trimIndent()
    }
    ctx.request().response()
        .putHeader("context-type", "text/xml")
        .setStatusCode(200)
        .end(
            """
                 <GetSubscriptionAttributesResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/"> 
                    <GetSubscriptionAttributesResult>
                      <Attributes> 
                        $entries
                     </Attributes> 
                   </GetSubscriptionAttributesResult>
                   <ResponseMetadata>
                     <RequestId>${UUID.randomUUID()}</RequestId>
                   </ResponseMetadata> 
                 </GetSubscriptionAttributesResponse>
            """.trimIndent()
        )
}