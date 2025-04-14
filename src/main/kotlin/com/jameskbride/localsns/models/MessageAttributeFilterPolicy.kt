package com.jameskbride.localsns.models

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.jameskbride.localsns.getElementType
import com.jameskbride.localsns.validateNumericMatcher

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
                    val firstElement = (it.value.asJsonArray).firstOrNull()
                    val elementType = getElementType(firstElement)
                    when (elementType) {
                        "String" -> StringAttributeFilterPolicy(it.key, (it.value.asJsonArray).map { value -> value.asString })
                        "Object" -> NumberAttributeFilterPolicy(it.key, firstElement?.asJsonObject)
                        else -> throw IllegalArgumentException("Unsupported filter policy value type")
                    }
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

interface AttributeFilterPolicy {
    fun matches(messageAttribute: MessageAttribute): Boolean
}

class NumberAttributeFilterPolicy(private val attribute: String?, numericMatcher: JsonObject?): AttributeFilterPolicy {
    private var value: Double

    init {
        value = validateNumericMatcher(numericMatcher)
    }

    override fun matches(messageAttribute: MessageAttribute): Boolean {
        return messageAttribute.name == attribute && messageAttribute.value.toDouble() == value
    }
}

class StringAttributeFilterPolicy(private val attribute: String, val values: List<String>): AttributeFilterPolicy {
    override fun matches(messageAttribute: MessageAttribute): Boolean {
        return messageAttribute.name == attribute && values.contains(messageAttribute.value)
    }
}