package com.jameskbride.localsns.models

import com.google.gson.annotations.SerializedName

data class SnsMessage(
    @SerializedName("Message") val message:String,
    @SerializedName("MessageId") val messageId:String,
    @SerializedName("Signature") val signature:String,
    @SerializedName("SignatureVersion") val signatureVersion: Int,
    @SerializedName("SigningCertUrl") val signingCertUrl:String,
    @SerializedName("Subject") val subject: String? = null,
    @SerializedName("Timestamp") val timestamp: String,
    @SerializedName("TopicArn") val topicArn: String,
    @SerializedName("Type") val type: String = "Notification",
    @SerializedName("UnsubscribeUrl") val unsubscribeUrl: String? = null,
)