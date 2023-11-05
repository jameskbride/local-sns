package com.jameskbride.localsns.models

import com.google.gson.annotations.SerializedName

data class Message(
    val default: String,
    val http: String? = null,
    val https: String? = null,
    val file: String? = null,
    val slack: String? = null,
    @SerializedName("sqs") val awsSqs: String? = null,
    @SerializedName("lambda") val awsLambda: String? = null,
    @SerializedName("rabbitmq") val rabbitMq: String? = null,
    // We don't support these formats but we also don't want to break the publish action if they are included in the message.
    val sms: String? = null,
    val application: String? = null,
    val email: String? = null,
    @SerializedName("email-json") val emailJson: String? = null,
)