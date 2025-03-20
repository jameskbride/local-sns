package com.jameskbride.localsns.routes.topics

import com.jameskbride.localsns.*
import com.jameskbride.localsns.models.PublishBatchRequestEntry
import com.jameskbride.localsns.models.Topic
import io.vertx.ext.web.RoutingContext
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

    ctx.request().response()
        .putHeader("context-type", "text/xml")
        .setStatusCode(200)
        .end(
            """
              <PublishBatchResponse xmlns="http://sns.amazonaws.com/doc/2010-03-31/">
                <PublishBatchResult>
                    <Failed />
                    <Successful>
                        <member>
                            <MessageId>605c74b2-37fb-536f-b349-14e757d832dd</MessageId>
                            <Id>1</Id>
                        </member>
                        <member>
                            <MessageId>4446707a-a051-572a-ba8e-102fc072c698</MessageId>
                            <Id>2</Id>
                        </member>
                        <member>
                            <MessageId>ae2ee57f-5158-5edc-9732-852a317b5f6e</MessageId>
                            <Id>3</Id>
                        </member>
                    </Successful>
                </PublishBatchResult>
                <ResponseMetadata>
                    <RequestId>${UUID.randomUUID()}</RequestId>
                </ResponseMetadata>
            </PublishBatchResponse>
            """.trimIndent()
        )
}