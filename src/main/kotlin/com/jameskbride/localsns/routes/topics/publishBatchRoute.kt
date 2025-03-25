package com.jameskbride.localsns.routes.topics

import com.jameskbride.localsns.*
import com.jameskbride.localsns.models.PublishBatchRequestEntry
import com.jameskbride.localsns.models.Topic
import io.vertx.ext.web.RoutingContext
import org.apache.camel.impl.DefaultCamelContext
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.util.*
import java.util.regex.Pattern

val publishBatchRoute: (RoutingContext) -> Unit = route@{ ctx: RoutingContext ->
    val logger: Logger = LogManager.getLogger("publishBatchRoute")
    val topicArn = getFormAttribute(ctx, "TopicArn")
    val formAttributes = ctx.request().formAttributes()
    val batchEntriesAttributes = formAttributes
        .filter { it.key.startsWith("PublishBatchRequestEntries.member") }

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

    val batchEntries = PublishBatchRequestEntry.parse(batchEntriesAttributes)
    if (batchEntries.isEmpty()) {
        logAndReturnError(ctx, logger, "The batch request doesn't contain any entries", EMPTY_BATCH_REQUEST, 400)
        return@route
    }

    if (batchEntries.size > 10) {
        logAndReturnError(ctx, logger, "The batch request contains more entries than permissible (more than 10)", TOO_MANY_ENTRIES_IN_BATCH_REQUEST, 400)
        return@route
    }

    val duplicateIds = batchEntries.values.groupBy { it.id }.filter { it.value.size > 1 }
    if (duplicateIds.isNotEmpty()) {
        logAndReturnError(ctx, logger, "The batch request contains duplicate entry ids: ${duplicateIds.keys}", BATCH_ENTRY_IDS_NOT_DISTINCT, 400)
        return@route
    }

    val invalidIds = batchEntries.values.filter { !PublishBatchRequestEntry.isValidId(it.id) }
    if (invalidIds.isNotEmpty()) {
        logAndReturnError(ctx, logger, "The batch request contains invalid entry ids: ${invalidIds.map { it.id }}. This identifier can have up to 80 characters. The following characters are accepted: alphanumeric characters, hyphens(-), and underscores (_)", INVALID_BATCH_ENTRY_ID, 400)
        return@route
    }

    val camelContext = DefaultCamelContext()
    val producerTemplate = camelContext.createProducerTemplate()
    camelContext.start()
    batchEntries.values.map { message ->
        if (message.messageStructure != null) {
            val success = when (message.messageStructure) {
                "json" -> {
                    publishJsonStructure(message.message, message.messageAttributes, topicArn, producerTemplate, ctx)
                }
                else -> {
                    publishBasicMessageToTopic(message.message, message.messageAttributes, topicArn, producerTemplate, ctx)
                    true
                }
            }

            if (!success) {
                logAndReturnError(ctx, logger, "Message structure is not valid")
                return@route
            }
        } else {
            publishBasicMessageToTopic(message.message, message.messageAttributes, topicArn, producerTemplate, ctx)
        }
    }

    //build Successful responses
    val successfulResponse = batchEntries.values.map { message ->
        """
            <member>
                <MessageId>${UUID.randomUUID()}</MessageId>
                <Id>${message.id}</Id>
            </member>
        """.trimIndent()
    }.joinToString("\n")

    ctx.request().response()
        .putHeader("context-type", "text/xml")
        .setStatusCode(200)
        .end(
            """
              <PublishBatchResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/">
                <PublishBatchResult>
                    <Failed />
                    <Successful>
                        $successfulResponse
                    </Successful>
                </PublishBatchResult>
                <ResponseMetadata>
                    <RequestId>${UUID.randomUUID()}</RequestId>
                </ResponseMetadata>
            </PublishBatchResponse>
            """.trimIndent()
        )
}