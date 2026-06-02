package com.jameskbride.localsns.models

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.jameskbride.localsns.subscriptions.getElementType
import com.jameskbride.localsns.subscriptions.validateNumericMatcher

class MessageAttributeFilterPolicy(filterPolicy: String) {
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

    fun matches(messageAttributes: Map<String, MessageAttribute>): Boolean {
        return matchesPolicy(filterPolicyJsonObject, messageAttributes)
    }

    private fun matchesPolicy(policyJsonObject: JsonObject, messageAttributes: Map<String, MessageAttribute>): Boolean {
        val andPolicyEntries = policyJsonObject.asMap().filterKeys { it != "\$or" }
        val andEntriesMatch = andPolicyEntries.all {
            matchesSingleAttributePolicy(it.key, it.value, messageAttributes)
        }
        if (!andEntriesMatch) {
            return false
        }

        val orPolicyValue = policyJsonObject.get("\$or") ?: return true
        if (!isRecognizedOrOperator(orPolicyValue)) {
            return matchesSingleAttributePolicy("\$or", orPolicyValue, messageAttributes)
        }

        return orPolicyValue.asJsonArray.any {
            matchesPolicy(it.asJsonObject, messageAttributes)
        }
    }

    private fun isRecognizedOrOperator(orPolicyValue: Any?): Boolean {
        if (orPolicyValue !is JsonArray || orPolicyValue.size() < 2) {
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

    private fun matchesSingleAttributePolicy(
        key: String,
        policyValue: Any?,
        messageAttributes: Map<String, MessageAttribute>
    ): Boolean {
        val messageAttribute = messageAttributes[key] ?: return false
        val attributePolicy = buildAttributePolicy(key, policyValue)
        return attributePolicy.matches(messageAttribute)
    }

    private fun buildAttributePolicy(key: String, policyValue: Any?): AttributeFilterPolicy {
        return when (policyValue) {
            is JsonArray -> {
                val firstElement = policyValue.firstOrNull()
                val elementType = getElementType(firstElement)
                when (elementType) {
                    "String" -> StringAttributeFilterPolicy(key, policyValue.map { value -> value.asString })
                    "Object" -> NumberAttributeFilterPolicy(key, firstElement?.asJsonObject)
                    else -> throw IllegalArgumentException("Unsupported filter policy value type")
                }
            }

            else -> throw IllegalArgumentException("Unsupported filter policy value type")
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