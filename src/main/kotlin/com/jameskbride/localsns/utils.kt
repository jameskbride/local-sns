package com.jameskbride.localsns

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.gson.*
import com.jameskbride.localsns.models.Configuration
import com.jameskbride.localsns.models.Subscription
import com.jameskbride.localsns.models.Topic
import com.typesafe.config.Config
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.core.shareddata.LocalMap
import io.vertx.ext.web.RoutingContext
import org.apache.logging.log4j.Logger
import java.util.*

const val NOT_FOUND = "NotFound"
const val INVALID_PARAMETER = "InvalidParameter"
const val INTERNAL_ERROR = "InternalError"
const val EMPTY_BATCH_REQUEST = "EmptyBatchRequest"
const val TOO_MANY_ENTRIES_IN_BATCH_REQUEST = "TooManyEntriesInBatchRequest"
const val BATCH_ENTRY_IDS_NOT_DISTINCT = "BatchEntryIdNotDistinct"
const val INVALID_BATCH_ENTRY_ID = "InvalidBatchEntryId"

fun getDbPath(config: Config): String? =
    System.getenv("DB_PATH") ?: config.getString("db.path")

fun getDbOutputPath(config: Config): String =
    System.getenv("DB_OUTPUT_PATH") ?: config.getString("db.outputPath")

fun toJsonConfig(configFile: Buffer?): JsonObject {
    val gson = Gson()
    val buffer = configFile.toString()
    return gson.fromJson(buffer, JsonObject::class.java)
}

fun bufferFromConfiguration(configuration: Configuration): Buffer {
    val gson  = Gson()
    val jsonObject = gson.toJson(configuration)
    return Buffer.buffer(jsonObject)
}

fun getAwsAccountId(config: Config): String =
    System.getenv("AWS_ACCOUNT_ID") ?: config.getString("aws.accountId")

fun getAwsRegion(config: Config): String =
    System.getenv("AWS_DEFAULT_REGION") ?: config.getString("aws.region")

fun getPort(config: Config): Int {
    val httpPortEnv = System.getenv("HTTP_PORT")
    val port = if (httpPortEnv !== null) {
        Integer.parseInt(httpPortEnv)
    } else {
        config.getInt("http.port")
    }
    return port
}

fun getHttpInterface(config: Config): String? =
    System.getenv("HTTP_INTERFACE") ?: config.getString("http.interface")

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

fun configureObjectMappers() {
    val mapper = DatabindCodec.mapper()
    mapper.registerKotlinModule()

    val prettyMapper = DatabindCodec.prettyMapper()
    prettyMapper.registerKotlinModule()
}

fun getElementType(firstElement: JsonElement?): Any? {
    return when (firstElement) {
        is JsonArray -> "Array"
        is JsonObject -> "Object"
        is JsonPrimitive -> {
            if (firstElement.isString) {
                "String"
            } else if (firstElement.isBoolean) {
                "Boolean"
            } else if (firstElement.isNumber) {
                "Number"
            } else if (firstElement.isJsonNull) {
                null
            } else {
                null
            }
        }
        else -> null
    }
}

fun validateNumericMatcher(numericMatcher: JsonObject?): Double {
    if (numericMatcher == null) {
        throw IllegalArgumentException("Numeric matcher cannot be null")
    }
    if (!numericMatcher.has("numeric")) {
        throw IllegalArgumentException("Only numeric matcher is supported")
    }
    val numericMatcherArray = numericMatcher.getAsJsonArray("numeric")
    if (numericMatcherArray.size() != 2) {
        throw IllegalArgumentException("Numeric matcher must have exactly 2 elements")
    }

    val operator = numericMatcherArray[0].asString
    if (operator != "=") {
        throw IllegalArgumentException("Only equality operator is supported")
    }
    return numericMatcherArray[1].asDouble
}