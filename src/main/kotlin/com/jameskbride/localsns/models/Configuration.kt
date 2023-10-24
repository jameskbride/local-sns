package com.jameskbride.localsns.models

import java.io.Serializable

data class Configuration(
  val version: Int,
  val timestamp: Long,
  val subscriptions: List<Subscription> = listOf(),
  val topics: List<Topic> = listOf(),
): Serializable
