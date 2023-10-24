package com.jameskbride.localsns.models

import com.fasterxml.jackson.annotation.JsonIgnore
import java.io.Serializable

data class Subscription(
  val arn: String,
  val owner: String,
  val topicArn: String,
  val protocol: String,
  val endpoint: String?,
  @JsonIgnore val subscriptionAttributes: Map<String, String> = mapOf()
): Serializable {
  companion object {
    val namePattern = """([\w+_-]{1,256})"""
    val arnPattern = """([\w+_:-]{1,512})"""
  }
}