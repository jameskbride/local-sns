package com.jameskbride.localsns.models

import com.fasterxml.jackson.annotation.JsonIgnore
import java.io.Serializable
import java.net.URLDecoder

data class Subscription(
  val arn: String,
  val owner: String,
  val topicArn: String,
  val protocol: String,
  val endpoint: String?,
  val subscriptionAttributes: Map<String, String> = mapOf()
): Serializable {
  companion object {
    val arnPattern = """([\w+_:-]{1,512})"""
  }

  @JsonIgnore fun decodedEndpointUrl():String {
    return URLDecoder.decode(endpoint, "UTF-8")
  }

  @JsonIgnore fun isRawMessageDelivery(): Boolean {
    return subscriptionAttributes.getOrDefault("RawMessageDelivery", "false") == "true"
  }

  @JsonIgnore fun xmlEncodeEndpointUrl(): String? {
    return endpoint?.replace("&", "&amp;") ?: endpoint
  }
}
