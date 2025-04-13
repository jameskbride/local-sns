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
        val filterPolicyJsonObject = JsonObject()
        val attribute1Policy = JsonArray()
        attribute1Policy.add("value1")
        filterPolicyJsonObject.add("matched", attribute1Policy)
        val filterPolicyJson = gson.toJson(filterPolicyJsonObject)

        val messageAttributeFilterPolicy = MessageAttributeFilterPolicy(filterPolicyJson)

        val messageAttribute = MessageAttribute(name="attribute1", value="value1", dataType="String")
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
        val filterPolicyJsonObject = JsonObject()
        val attribute1Policy = JsonArray()
        attribute1Policy.add("value1")
        filterPolicyJsonObject.add("matched", attribute1Policy)
        val filterPolicyJson = gson.toJson(filterPolicyJsonObject)

        val messageAttributeFilterPolicy = MessageAttributeFilterPolicy(filterPolicyJson)

        val secondMessageAttribute = MessageAttribute(name="ignored", value="value2")
        val messageAttributes = mapOf(
            "ignored" to secondMessageAttribute
        )

        val result = messageAttributeFilterPolicy.matches(messageAttributes)
        assert(!result) { "Expected basic String filter policy to not match message attributes" }
    }

    @Test
    fun `matches when all attributes are present`() {
        val filterPolicyJsonObject = JsonObject()

        val attribute1Policy = JsonArray()
        attribute1Policy.add("value1")
        filterPolicyJsonObject.add("matched", attribute1Policy)

        val attribute2Policy = JsonArray()
        attribute2Policy.add("value2")
        filterPolicyJsonObject.add("matched2", attribute2Policy)

        val filterPolicyJson = gson.toJson(filterPolicyJsonObject)

        val messageAttributeFilterPolicy = MessageAttributeFilterPolicy(filterPolicyJson)

        val messageAttribute = MessageAttribute(name="matched", value="value1", dataType="String")
        val secondMessageAttribute = MessageAttribute(name="matched2", value="value2", dataType="String")
        val ignoredAttribute = MessageAttribute(name="ignored", value="value2")
        val messageAttributes = mapOf(
            "matched" to messageAttribute,
            "matched2" to secondMessageAttribute,
            "ignored" to ignoredAttribute
        )

        val result = messageAttributeFilterPolicy.matches(messageAttributes)
        assert(result) { "Expected basic String filter policy to match message attributes" }
    }
 }