package com.jameskbride.localsns.models

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject

class MessageAttributeFilterPolicy(filterPolicy: String) {
    private val gson = Gson()
    private val filterPolicyJsonObject = gson.fromJson(filterPolicy, JsonObject::class.java)

    fun matches(messageAttributes: Map<String, MessageAttribute>): Boolean {
        val allKeysPresent = filterPolicyJsonObject.asMap().all { messageAttributes.containsKey(it.key) }
        if (!allKeysPresent) {
            return false
        }

        val attributePolicies = filterPolicyJsonObject.asMap().map {
            val attributePolicy = when(it.value) {
                is JsonArray -> {
                    StringAttributeFilterPolicy(it.key, (it.value as JsonArray).map { value -> value.asString })
                }
                else -> throw IllegalArgumentException("Unsupported filter policy value type")
            }

            it.key to attributePolicy
        }.toMap()

        return attributePolicies.all {
            val messageAttribute = messageAttributes[it.key]
            it.value.matches(messageAttribute!!)
        }
    }
}

class StringAttributeFilterPolicy(private val attribute: String, val values: List<String>) {
    fun matches(messageAttribute: MessageAttribute): Boolean {
        return messageAttribute.name == attribute && values.contains(messageAttribute.value)
    }
}