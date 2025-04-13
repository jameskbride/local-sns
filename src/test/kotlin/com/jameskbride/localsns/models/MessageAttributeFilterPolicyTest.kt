package com.jameskbride.localsns.models

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MessageAttributeFilterPolicyTest {

    private lateinit var gson: Gson

    @BeforeEach
    fun setup() {
        gson = Gson()
    }

    @Test
    fun `matches a single string attribute`() {
        // {"matched": ["value1"]}
        val filterPolicyJsonObject = JsonObject()
        val attribute1Policy = JsonArray()
        attribute1Policy.add("value1")
        filterPolicyJsonObject.add("matched", attribute1Policy)

        val filterPolicyJson = gson.toJson(filterPolicyJsonObject)
        val messageAttributeFilterPolicy = MessageAttributeFilterPolicy(filterPolicyJson)

        // {"matched": {"name": "matched", "value": "value1", "dataType": "String"}}
        val messageAttribute = MessageAttribute(name="matched", value="value1", dataType="String")
        // {"ignored": {"name": "ignored", "value": "value2", "dataType": "String"}}
        val secondMessageAttribute = MessageAttribute(name="ignored", value="value2")

        val messageAttributes = mapOf(
            "matched" to messageAttribute,
            "ignored" to secondMessageAttribute
        )

        val result = messageAttributeFilterPolicy.matches(messageAttributes)
        assert(result) { "Expected basic String filter policy to match message attributes" }
    }

    @Test
    fun `it matches one of multiple possible string values`() {
        // {"matched": ["value1", "value2"]}
        val filterPolicyJsonObject = JsonObject()
        val attribute1Policy = JsonArray()
        attribute1Policy.add("value1")
        attribute1Policy.add("value2")
        filterPolicyJsonObject.add("matched", attribute1Policy)

        val filterPolicyJson = gson.toJson(filterPolicyJsonObject)
        val messageAttributeFilterPolicy = MessageAttributeFilterPolicy(filterPolicyJson)

        // {"matched": {"name": "matched", "value": "value1", "dataType": "String"}}
        val messageAttribute = MessageAttribute(name="matched", value="value1", dataType="String")
        // {"ignored": {"name": "ignored", "value": "value2", "dataType": "String"}}
        val secondMessageAttribute = MessageAttribute(name="ignored", value="value2")

        val messageAttributes = mapOf(
            "matched" to messageAttribute,
            "ignored" to secondMessageAttribute
        )

        val result = messageAttributeFilterPolicy.matches(messageAttributes)
        assert(result) { "Expected basic String filter policy to match message attributes" }
    }

    @Test
    fun `does not match when filter policy attribute is not in message attributes`() {
        // {"matched": ["value1"]}
        val filterPolicyJsonObject = JsonObject()
        val attribute1Policy = JsonArray()
        attribute1Policy.add("value1")
        filterPolicyJsonObject.add("matched", attribute1Policy)

        val filterPolicyJson = gson.toJson(filterPolicyJsonObject)
        val messageAttributeFilterPolicy = MessageAttributeFilterPolicy(filterPolicyJson)

        // {"ignored": {"name": "ignored", "value": "value2", "dataType": "String"}}
        val secondMessageAttribute = MessageAttribute(name="ignored", value="value2")
        val messageAttributes = mapOf(
            "ignored" to secondMessageAttribute
        )

        val result = messageAttributeFilterPolicy.matches(messageAttributes)
        assert(!result) { "Expected basic String filter policy to not match message attributes" }
    }

    @Test
    fun `does not match when only some filter policy attributes are present`() {
        val filterPolicyJsonObject = JsonObject()
        val attribute1Policy = JsonArray()
        attribute1Policy.add("value1")
        filterPolicyJsonObject.add("matched", attribute1Policy)
        val attribute2Policy = JsonArray()
        attribute2Policy.add("value2")

        // {"matched": ["value1"], "matched2": ["value2"]}
        filterPolicyJsonObject.add("matched2", attribute2Policy)

        val filterPolicyJson = gson.toJson(filterPolicyJsonObject)
        val messageAttributeFilterPolicy = MessageAttributeFilterPolicy(filterPolicyJson)

        // {"matched": {"name": "matched", "value": "value1", "dataType": "String"}}
        val messageAttribute = MessageAttribute(name="matched", value="value1", dataType="String")
        // {"matched2": {"name": "matched2", "value": "value2", "dataType": "String"}}
        val ignoredAttribute = MessageAttribute(name="ignored", value="value2")
        val messageAttributes = mapOf(
            "matched" to messageAttribute,
            "ignored" to ignoredAttribute
        )

        val result = messageAttributeFilterPolicy.matches(messageAttributes)
        assert(!result) { "Expected basic String filter policy to not match message attributes" }
    }

    @Test
    fun `matches when all string attributes are present and match possible string values`() {
        val filterPolicyJsonObject = JsonObject()

        val attribute1Policy = JsonArray()
        attribute1Policy.add("value1")
        filterPolicyJsonObject.add("matched", attribute1Policy)

        val attribute2Policy = JsonArray()
        attribute2Policy.add("value2")
        filterPolicyJsonObject.add("matched2", attribute2Policy)
        // {"matched": ["value1"], "matched2": ["value2"]}
        val filterPolicyJson = gson.toJson(filterPolicyJsonObject)

        val messageAttributeFilterPolicy = MessageAttributeFilterPolicy(filterPolicyJson)

        // {"matched": {"name": "matched", "value": "value1", "dataType": "String"}}
        val messageAttribute = MessageAttribute(name="matched", value="value1", dataType="String")
        // {"matched2": {"name": "matched2", "value": "value2", "dataType": "String"}}
        val secondMessageAttribute = MessageAttribute(name="matched2", value="value2", dataType="String")
        // {"ignored": {"name": "ignored", "value": "value2", "dataType": "String"}}
        val ignoredAttribute = MessageAttribute(name="ignored", value="value2")
        val messageAttributes = mapOf(
            "matched" to messageAttribute,
            "matched2" to secondMessageAttribute,
            "ignored" to ignoredAttribute
        )

        val result = messageAttributeFilterPolicy.matches(messageAttributes)
        assert(result) { "Expected basic String filter policy to match message attributes" }
    }

    @Test
    fun `does not match when all string attributes are present but all values do not match`() {
        val filterPolicyJsonObject = JsonObject()

        val attribute1Policy = JsonArray()
        attribute1Policy.add("shouldMatch")
        filterPolicyJsonObject.add("matchOn", attribute1Policy)

        val attribute2Policy = JsonArray()
        attribute2Policy.add("shouldMatch2")
        filterPolicyJsonObject.add("matchOn2", attribute2Policy)

        // {"matchOn": ["shouldMatch"], "matchOn2": ["shouldMatch2"]}
        val filterPolicyJson = gson.toJson(filterPolicyJsonObject)

        val messageAttributeFilterPolicy = MessageAttributeFilterPolicy(filterPolicyJson)

        // {"matchOn": {"name": "matchOn", "value": "doesnotmatch", "dataType": "String"}}
        val mismatchedAttribute = MessageAttribute(name="matchOn", value="doesnotmatch", dataType="String")
        // {"matchOn2": {"name": "matchOn2", "value": "shouldMatch2", "dataType": "String"}}
        val matchingAttribute = MessageAttribute(name="matchOn2", value="shouldMatch2", dataType="String")
        // {"ignored": {"name": "ignored", "value": "value2", "dataType": "String"}}
        val ignoredAttribute = MessageAttribute(name="ignored", value="value2")
        val messageAttributes = mapOf(
            "matchOn" to mismatchedAttribute,
            "matchOn2" to matchingAttribute,
            "ignored" to ignoredAttribute
        )

        val result = messageAttributeFilterPolicy.matches(messageAttributes)
        assert(!result) { "Expected basic String filter policy to not match message attributes" }
    }

    @Test
    fun `it matches exact numeric filter policies`() {
        val attribute1Policy = JsonArray()
        val numericEqualityArray = JsonArray()
        numericEqualityArray.add("=")
        numericEqualityArray.add("1.125")
        val numericPolicyJsonObject = JsonObject()
        numericPolicyJsonObject.add("numeric", numericEqualityArray)
        attribute1Policy.add(numericPolicyJsonObject)

        // {"matched": [{"numeric": ["=", "1.125"]}]}
        val filterPolicyJsonObject = JsonObject()
        filterPolicyJsonObject.add("matched", attribute1Policy)

        val filterPolicyJson = gson.toJson(filterPolicyJsonObject)
        val messageAttributeFilterPolicy = MessageAttributeFilterPolicy(filterPolicyJson)

        // {"matched": {"name": "matched", "value": "1.125", "dataType": "Number"}}
        val messageAttribute = MessageAttribute(name="matched", value="1.125", dataType="Number")
        // {"ignored": {"name": "ignored", "value": "value2", "dataType": "String"}}
        val secondMessageAttribute = MessageAttribute(name="ignored", value="value2")

        val messageAttributes = mapOf(
            "matched" to messageAttribute,
            "ignored" to secondMessageAttribute
        )

        val result = messageAttributeFilterPolicy.matches(messageAttributes)
        assert(result) { "Expected basic String filter policy to match message attributes" }
    }
 }