package com.jameskbride.localsns.models

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.jameskbride.localsns.subscriptions.getElementType
import com.jameskbride.localsns.subscriptions.validateNumericMatcher

class MessageBodyFilterPolicy(filterPolicy: String) {
    private val gson = Gson()
    private val filterPolicyJsonObject = gson.fromJson(filterPolicy, JsonObject::class.java)

    fun matches(messageJsonObject: JsonObject): Boolean {
        val allKeysPresent = filterPolicyJsonObject.asMap().all { messageJsonObject.has(it.key) }
        if (!allKeysPresent) {
            return false
        }

        val attributePolicies = filterPolicyJsonObject.asMap().map {
            val attributeType = getElementType(messageJsonObject.get(it.key))
            if (!listOf("String", "Number", "Boolean", "null").contains(attributeType)) {
                it.key to NonMatchingMessageBodyFilterPolicy()
            } else {
                val filterValue = it.value.asJsonArray
                val firstElement = filterValue.asList().firstOrNull()
                val elementType = getElementType(firstElement)
                val attributePolicy = when (elementType) {
                    "String" -> {
                        StringMessageBodyFilterPolicy(it.key, filterValue.map { value -> value.asString })
                    }
                    "Boolean" -> {
                        val booleanValue = firstElement?.asBoolean
                        BooleanMessageBodyFilterPolicy(it.key, listOf(booleanValue!!))
                    }
                    "Object" -> {
                        val numericMatcher = firstElement?.asJsonObject
                        NumberMessageBodyFilterPolicy(it.key, numericMatcher!!)
                    }
                    "null" -> {
                        NullMessageBodyFilterPolicy(it.key)
                    }
                    else -> {
                        throw IllegalArgumentException("Unsupported filter policy value type")
                    }
                }

                it.key to attributePolicy
            }

        }.toMap()

        return attributePolicies.all { policy ->
            policy.value.matches(messageJsonObject)
        }
    }
}

interface MessageFilterPolicy {
    fun matches(message: JsonObject): Boolean
}

class StringMessageBodyFilterPolicy(private val attribute: String, private val values: List<String>): MessageFilterPolicy {
    override fun matches(message: JsonObject): Boolean {
        val messageAttribute = message.get(attribute)?.asString ?: return false
        return values.contains(messageAttribute)
    }
}

class BooleanMessageBodyFilterPolicy(private val attribute: String, private val values: List<Boolean>): MessageFilterPolicy {
    override fun matches(message: JsonObject): Boolean {
        val messageAttribute = message.get(attribute)?.asBoolean ?: return false
        return values.contains(messageAttribute)
    }
}

class NullMessageBodyFilterPolicy(private val attribute: String): MessageFilterPolicy {
    override fun matches(message: JsonObject): Boolean {
        return message.get(attribute)?.isJsonNull ?: false
    }
}

class NumberMessageBodyFilterPolicy(private val attribute: String, private val numericMatcher: JsonObject): MessageFilterPolicy {
    private var value: Double

    init {
        value = validateNumericMatcher(numericMatcher)
    }

    override fun matches(message: JsonObject): Boolean {
        val messageAttribute = message.get(attribute)?.asDouble ?: return false
        return value == messageAttribute
    }
}

class NonMatchingMessageBodyFilterPolicy: MessageFilterPolicy {
    override fun matches(message: JsonObject): Boolean {
        return false
    }
}