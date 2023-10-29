package com.jameskbride.localsns

import com.jameskbride.localsns.models.Subscription
import com.jameskbride.localsns.models.Topic
import com.typesafe.config.Config
import io.vertx.core.Vertx
import io.vertx.core.shareddata.LocalMap
import io.vertx.ext.web.RoutingContext
import org.apache.logging.log4j.Logger
import java.util.*

const val NOT_FOUND = "NotFound"
const val INVALID_PARAMETER = "InvalidParameter"
const val INTERNAL_ERROR = "InternalError"

fun getDbPath(config: Config): String? =
    System.getenv("DB_PATH") ?: config.getString("db.path")

fun getDbOutputPath(config: Config): String =
    System.getenv("DB_OUTPUT_PATH") ?: config.getString("db.outputPath")

fun getAwsAccountId(config: Config): String =
    System.getenv("AWS_ACCOUNT_ID") ?: config.getString("aws.accountId")

fun getAwsRegion(config: Config): String =
    System.getenv("AWS_DEFAULT_REGION") ?: config.getString("aws.region")

fun getTopicsMap(vertx: Vertx): LocalMap<String, Topic>? =
    vertx.sharedData().getLocalMap("topics")

fun getSubscriptionsMap(vertx: Vertx): LocalMap<String, List<Subscription>>? =
    vertx.sharedData().getLocalMap("subscriptions")

fun logAndReturnError(ctx: RoutingContext, logger: Logger, errorMessage: String, code: String = INVALID_PARAMETER, statusCode: Int = 400) {
    logger.error(errorMessage)
    ctx.request().response()
        .setStatusCode(statusCode)
        .end("""
            <ErrorResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/">
                <Error>
                    <Type>Sender</Type>
                    <Code>$code</Code>
                    <Message>$errorMessage</Message>
                </Error>
                <RequestId>${UUID.randomUUID()}</RequestId>
            </ErrorResponse>
        """.trimIndent())
}

fun getFormAttribute(
    ctx: RoutingContext,
    attribute: String
): String? = ctx.request().getFormAttribute(attribute)
