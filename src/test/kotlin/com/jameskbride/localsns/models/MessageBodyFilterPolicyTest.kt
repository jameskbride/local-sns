package com.jameskbride.localsns.models

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MessageBodyFilterPolicyTest {

    private lateinit var gson: Gson

    @BeforeEach
    fun setup() {
        gson = Gson()
    }

    @Test
    fun `it matches a message with a filter policy`() {
        val policyJson = """
            {"store": ["example_corp_1"]}
        """.trimIndent()

        val filterPolicy = MessageBodyFilterPolicy(policyJson)

        val message = """
            {"store": "example_corp_1"}
        """.trimIndent()
        assert(filterPolicy.matches(gson.fromJson(message, JsonObject::class.java)))
    }

    @Test
    fun `it does not match when a message attribute does not match the filter policy`() {
        val policyJson = """
            {"store": ["matches"]}
        """.trimIndent()

        val filterPolicy = MessageBodyFilterPolicy(policyJson)

        val message = """
            {"store": "does not match"}
        """.trimIndent()
        assert(!filterPolicy.matches(gson.fromJson(message, JsonObject::class.java)))
    }

    @Test
    fun `it does not match when all filter policy keys are not present in the message`() {
        val policyJson = """
            {"store": ["example_corp_1"], "other_key": ["other_value"]}
        """.trimIndent()

        val filterPolicy = MessageBodyFilterPolicy(policyJson)

        val message = """
            {"store": "example_corp_1"}
        """.trimIndent()
        assert(!filterPolicy.matches(gson.fromJson(message, JsonObject::class.java)))
    }

    @Test
    fun `it matches on exact numeric filter policies`() {
        val policyJson = """
            {"store": [{"numeric": ["=", 1]}]}
        """.trimIndent()

        val filterPolicy = MessageBodyFilterPolicy(policyJson)

        val message = """
            {"store": 1}
        """.trimIndent()
        assert(filterPolicy.matches(gson.fromJson(message, JsonObject::class.java)))
    }

    @Test
    fun `it matches on boolean filter policies`() {
        val policyJson = """
            {"store": [true]}
        """.trimIndent()

        val filterPolicy = MessageBodyFilterPolicy(policyJson)

        val message = """
            {"store": true}
        """.trimIndent()
        assert(filterPolicy.matches(gson.fromJson(message, JsonObject::class.java)))
    }

    @Test
    fun `it does not match on boolean filter policies with different values`() {
        val policyJson = """
            {"store": [true]}
        """.trimIndent()

        val filterPolicy = MessageBodyFilterPolicy(policyJson)

        val message = """
            {"store": false}
        """.trimIndent()
        assert(!filterPolicy.matches(gson.fromJson(message, JsonObject::class.java)))
    }

    @Test
    fun `it does not match on object message attributes`() {
        val policyJson = """
            {"store": ["example_corp_1"]}
        """.trimIndent()

        val filterPolicy = MessageBodyFilterPolicy(policyJson)

        val message = """
            {"store": {"key": "value"}}
        """.trimIndent()
        assert(!filterPolicy.matches(gson.fromJson(message, JsonObject::class.java)))
    }

    @Test
    fun `it matches on null message attributes`() {
        val policyJson = """
            {"store": [null]}
        """.trimIndent()

        val filterPolicy = MessageBodyFilterPolicy(policyJson)

        val message = """
            {"store": null}
        """.trimIndent()
        assert(filterPolicy.matches(gson.fromJson(message, JsonObject::class.java)))
    }

    @Test
    fun `it does not match on null message attributes with different values`() {
        val policyJson = """
            {"store": [null]}
        """.trimIndent()

        val filterPolicy = MessageBodyFilterPolicy(policyJson)

        val message = """
            {"store": "not null"}
        """.trimIndent()
        assert(!filterPolicy.matches(gson.fromJson(message, JsonObject::class.java)))
    }

    @Test
    fun `it does not match on array message attributes`() {
        val policyJson = """
            {"store": ["example_corp_1"]}
        """.trimIndent()

        val filterPolicy = MessageBodyFilterPolicy(policyJson)

        val message = """
            {"store": ["example_corp_1", "other_value"]}
        """.trimIndent()
        assert(!filterPolicy.matches(gson.fromJson(message, JsonObject::class.java)))
    }

    @Test
    fun `it matches when one $or branch matches`() {
        val policyJson = """
            {
              "source": ["aws.cloudwatch"],
              "${'$'}or": [
                {"metricName": ["CPUUtilization"]},
                {"namespace": ["AWS/EC2"]}
              ]
            }
        """.trimIndent()

        val filterPolicy = MessageBodyFilterPolicy(policyJson)

        val message = """
            {"source": "aws.cloudwatch", "namespace": "AWS/EC2"}
        """.trimIndent()
        assert(filterPolicy.matches(gson.fromJson(message, JsonObject::class.java)))
    }

    @Test
    fun `it does not match when no $or branch matches`() {
        val policyJson = """
            {
              "source": ["aws.cloudwatch"],
              "${'$'}or": [
                {"metricName": ["CPUUtilization"]},
                {"namespace": ["AWS/EC2"]}
              ]
            }
        """.trimIndent()

        val filterPolicy = MessageBodyFilterPolicy(policyJson)

        val message = """
            {"source": "aws.cloudwatch", "metricName": "ReadLatency"}
        """.trimIndent()
        assert(!filterPolicy.matches(gson.fromJson(message, JsonObject::class.java)))
    }

    @Test
    fun `it does not match when top-level key matches but $or branch does not`() {
        val policyJson = """
            {
              "status": ["not_sent"],
              "${'$'}or": [
                {"amount": [{"numeric": ["=", 10.5]}]},
                {"region": ["us-east-1"]}
              ]
            }
        """.trimIndent()

        val filterPolicy = MessageBodyFilterPolicy(policyJson)

        val message = """
            {"status": "not_sent", "amount": 11.0}
        """.trimIndent()
        assert(!filterPolicy.matches(gson.fromJson(message, JsonObject::class.java)))
    }

    @Test
    fun `it treats invalid $or with one branch as a normal message body key`() {
        val policyJson = """
            {
              "${'$'}or": ["branch-value"]
            }
        """.trimIndent()

        val filterPolicy = MessageBodyFilterPolicy(policyJson)

        val matchingMessage = """
            {"${'$'}or": "branch-value"}
        """.trimIndent()
        val nonMatchingMessage = """
            {"${'$'}or": "other"}
        """.trimIndent()

        assert(filterPolicy.matches(gson.fromJson(matchingMessage, JsonObject::class.java)))
        assert(!filterPolicy.matches(gson.fromJson(nonMatchingMessage, JsonObject::class.java)))
    }

    @Test
    fun `it treats $or with reserved keyword branch fields as a normal message body key`() {
        val policyJson = """
            {
              "${'$'}or": [
                {"numeric": ["=", 1]},
                {"metricName": ["CPUUtilization"]}
              ]
            }
        """.trimIndent()

        val filterPolicy = MessageBodyFilterPolicy(policyJson)

        val matchingMessage = """
            {"${'$'}or": 1}
        """.trimIndent()
        val nonMatchingMessage = """
            {"${'$'}or": 2}
        """.trimIndent()

        assert(filterPolicy.matches(gson.fromJson(matchingMessage, JsonObject::class.java)))
        assert(!filterPolicy.matches(gson.fromJson(nonMatchingMessage, JsonObject::class.java)))
    }
}