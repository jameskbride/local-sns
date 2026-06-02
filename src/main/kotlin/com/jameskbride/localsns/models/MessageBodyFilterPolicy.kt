package com.jameskbride.localsns.models

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.jameskbride.localsns.subscriptions.getElementType
import com.jameskbride.localsns.subscriptions.validateNumericMatcher

class MessageBodyFilterPolicy(filterPolicy: String) {
    private val gson = Gson()
    private val filterPolicyJsonObject = gson.fromJson(filterPolicy, JsonObject::class.java)
    private val reservedOrOperatorKeywords = setOf(
        "numeric",
        "prefix",
        "anything-but",
        "exists",
        "cidr",
        "suffix",
        "equals-ignore-case",
        "wildcard"
    )

    fun matches(messageJsonObject: JsonObject): Boolean {
        return matchesPolicy(filterPolicyJsonObject, messageJsonObject)
    }

    private fun matchesPolicy(policyJsonObject: JsonObject, messageJsonObject: JsonObject): Boolean {
        val andPolicyEntries = policyJsonObject.asMap().filterKeys { it != "\$or" }
        val andEntriesMatch = andPolicyEntries.all {
            matchesSingleAttributePolicy(it.key, it.value, messageJsonObject)
        }
        if (!andEntriesMatch) {
            return false
        }

        val orPolicyValue = policyJsonObject.get("\$or") ?: return true
        if (!isRecognizedOrOperator(orPolicyValue)) {
            return matchesSingleAttributePolicy("\$or", orPolicyValue, messageJsonObject)
        }

        return orPolicyValue.asJsonArray.any {
            matchesPolicy(it.asJsonObject, messageJsonObject)
        }
    }

    private fun isRecognizedOrOperator(orPolicyValue: Any?): Boolean {
        if (orPolicyValue !is com.google.gson.JsonArray || orPolicyValue.size() < 2) {
            return false
        }

        return orPolicyValue.all { branch ->
            if (!branch.isJsonObject) {
                return@all false
            }

            val branchFields = branch.asJsonObject.keySet()
            branchFields.none { reservedOrOperatorKeywords.contains(it) }
        }
    }

    private fun matchesSingleAttributePolicy(key: String, policyValue: Any?, messageJsonObject: JsonObject): Boolean {
        val attributeType = getElementType(messageJsonObject.get(key))
        if (!listOf("String", "Number", "Boolean", "null").contains(attributeType)) {
            return false
        }

        val attributePolicy = buildAttributePolicy(key, policyValue)
        return attributePolicy.matches(messageJsonObject)
    }

    private fun buildAttributePolicy(key: String, policyValue: Any?): MessageFilterPolicy {
        return when (policyValue) {
            is com.google.gson.JsonArray -> {
                val firstElement = policyValue.asList().firstOrNull()
                val elementType = getElementType(firstElement)
                when (elementType) {
                    "String" -> {
                        StringMessageBodyFilterPolicy(key, policyValue.map { value -> value.asString })
                    }

                    "Boolean" -> {
                        val booleanValue = firstElement?.asBoolean
                        BooleanMessageBodyFilterPolicy(key, listOf(booleanValue!!))
                    }

                    "Object" -> {
                        val numericMatcher = firstElement?.asJsonObject
                        NumberMessageBodyFilterPolicy(key, numericMatcher!!)
                    }

                    "null" -> {
                        NullMessageBodyFilterPolicy(key)
                    }

                    else -> {
                        throw IllegalArgumentException("Unsupported filter policy value type")
                    }
                }
            }

            else -> {
                throw IllegalArgumentException("Unsupported filter policy value type")
            }
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