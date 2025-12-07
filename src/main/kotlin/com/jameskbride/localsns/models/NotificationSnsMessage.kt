package com.jameskbride.localsns.models

import com.google.gson.annotations.SerializedName

data class NotificationSnsMessage(
    @SerializedName("Message") val message:String,
    @SerializedName("MessageId") val messageId:String,
    @SerializedName("Signature") val signature:String,
    @SerializedName("SignatureVersion") val signatureVersion: String,
    @SerializedName("SigningCertUrl") val signingCertUrl:String,
    @SerializedName("Subject") val subject: String? = null,
    @SerializedName("Timestamp") val timestamp: String,
    @SerializedName("TopicArn") val topicArn: String,
    @SerializedName("Type") val type: String = "Notification",
    @SerializedName("UnsubscribeURL") val unsubscribeUrl: String,
)