package com.jameskbride.localsns.models

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

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

    private fun getElementType(firstElement: JsonElement?): Any? {
        return when (firstElement) {
            is JsonArray -> "Array"
            is JsonObject -> "Object"
            is JsonPrimitive -> {
                if (firstElement.isString) {
                    "String"
                } else if (firstElement.isBoolean) {
                    "Boolean"
                } else if (firstElement.isNumber) {
                    "Number"
                } else if (firstElement.isJsonNull) {
                    null
                } else {
                    null
                }
            }
            else -> null
        }
    }
}

interface AttributeFilterPolicy {
    fun matches(messageAttribute: MessageAttribute): Boolean
}

class NumberAttributeFilterPolicy(private val attribute: String?, numericMatcher: JsonObject?): AttributeFilterPolicy {
    private var value: Double

    init {
        if (numericMatcher == null) {
            throw IllegalArgumentException("Numeric matcher cannot be null")
        }
        if (!numericMatcher.has("numeric")) {
            throw IllegalArgumentException("Only numeric matcher is supported")
        }
        val numericMatcherArray = numericMatcher.getAsJsonArray("numeric")
        if (numericMatcherArray.size() != 2) {
            throw IllegalArgumentException("Numeric matcher must have exactly 2 elements")
        }

        val operator = numericMatcherArray[0].asString
        if (operator != "=") {
            throw IllegalArgumentException("Only equality operator is supported")
        }
        value = numericMatcherArray[1].asDouble
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