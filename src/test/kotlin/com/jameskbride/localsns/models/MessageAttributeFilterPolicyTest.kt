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

    @Test
    fun `it does not match when the exact numeric match does not match`() {
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

        // {"matched": {"name": "matched", "value": "1.126", "dataType": "Number"}}
        val messageAttribute = MessageAttribute(name="matched", value="1.126", dataType="Number")
        // {"ignored": {"name": "ignored", "value": "value2", "dataType": "String"}}
        val secondMessageAttribute = MessageAttribute(name="ignored", value="value2")

        val messageAttributes = mapOf(
            "matched" to messageAttribute,
            "ignored" to secondMessageAttribute
        )

        val result = messageAttributeFilterPolicy.matches(messageAttributes)
        assert(!result) { "Expected basic String filter policy to not match message attributes" }
    }

    @Test
    fun `it matches when one $or branch matches`() {
        val filterPolicyJson = """
            {
              "source": ["aws.cloudwatch"],
                            "${'$'}or": [
                {"metricName": ["CPUUtilization"]},
                {"namespace": ["AWS/EC2"]}
              ]
            }
        """.trimIndent()

        val messageAttributeFilterPolicy = MessageAttributeFilterPolicy(filterPolicyJson)
        val messageAttributes = mapOf(
            "source" to MessageAttribute(name="source", value="aws.cloudwatch"),
            "namespace" to MessageAttribute(name="namespace", value="AWS/EC2")
        )

        val result = messageAttributeFilterPolicy.matches(messageAttributes)
        assert(result) { "Expected filter policy with \$or to match when one branch matches" }
    }

    @Test
    fun `it does not match when no $or branch matches`() {
        val filterPolicyJson = """
            {
              "source": ["aws.cloudwatch"],
                            "${'$'}or": [
                {"metricName": ["CPUUtilization"]},
                {"namespace": ["AWS/EC2"]}
              ]
            }
        """.trimIndent()

        val messageAttributeFilterPolicy = MessageAttributeFilterPolicy(filterPolicyJson)
        val messageAttributes = mapOf(
            "source" to MessageAttribute(name="source", value="aws.cloudwatch"),
            "metricName" to MessageAttribute(name="metricName", value="ReadLatency")
        )

        val result = messageAttributeFilterPolicy.matches(messageAttributes)
        assert(!result) { "Expected filter policy with \$or to not match when no branches match" }
    }

    @Test
    fun `it does not match when top-level key matches but $or branch does not`() {
        val filterPolicyJson = """
            {
              "status": ["not_sent"],
                            "${'$'}or": [
                {"amount": [{"numeric": ["=", 10.5]}]},
                {"region": ["us-east-1"]}
              ]
            }
        """.trimIndent()

        val messageAttributeFilterPolicy = MessageAttributeFilterPolicy(filterPolicyJson)
        val messageAttributes = mapOf(
            "status" to MessageAttribute(name="status", value="not_sent"),
            "amount" to MessageAttribute(name="amount", value="11.0", dataType="Number")
        )

        val result = messageAttributeFilterPolicy.matches(messageAttributes)
        assert(!result) { "Expected top-level AND with \$or to fail when no branch matches" }
    }

    @Test
    fun `it treats invalid $or with one branch as a normal attribute key`() {
        val filterPolicyJson = """
            {
              "${'$'}or": ["branch-value"]
            }
        """.trimIndent()

        val messageAttributeFilterPolicy = MessageAttributeFilterPolicy(filterPolicyJson)

        val matches = messageAttributeFilterPolicy.matches(
            mapOf("\$or" to MessageAttribute(name="\$or", value="branch-value"))
        )
        val doesNotMatch = messageAttributeFilterPolicy.matches(
            mapOf("\$or" to MessageAttribute(name="\$or", value="other"))
        )

        assert(matches) { "Expected invalid \$or operator shape to be treated as normal key" }
        assert(!doesNotMatch) { "Expected normal key matching behavior for invalid \$or operator shape" }
    }

    @Test
    fun `it treats $or with reserved keyword branch fields as a normal attribute key`() {
        val filterPolicyJson = """
            {
                            "${'$'}or": [
                                {"numeric": ["=", 1]},
                {"metricName": ["CPUUtilization"]}
              ]
            }
        """.trimIndent()

        val messageAttributeFilterPolicy = MessageAttributeFilterPolicy(filterPolicyJson)

        val matches = messageAttributeFilterPolicy.matches(
            mapOf("\$or" to MessageAttribute(name="\$or", value="1", dataType="Number"))
        )
        val doesNotMatch = messageAttributeFilterPolicy.matches(
            mapOf("\$or" to MessageAttribute(name="\$or", value="2", dataType="Number"))
        )

        assert(matches) { "Expected reserved branch field names to disable \$or operator parsing" }
        assert(!doesNotMatch) { "Expected fallback matching to honor normal key semantics" }
    }
}