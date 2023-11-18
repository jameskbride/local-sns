package com.jameskbride.localsns.models

import com.google.gson.annotations.SerializedName

data class LambdaRecord(
    @SerializedName("EventSource") val eventSource:String,
    @SerializedName("EventSubscriptionArn") val eventSubscriptionArn:String,
    @SerializedName("EventVersion") val eventVersion:Double,
    @SerializedName("Sns") val message: SnsMessage,
)
data class LambdaEvent(
    @SerializedName("Records") val records:List<LambdaRecord> = listOf()
)