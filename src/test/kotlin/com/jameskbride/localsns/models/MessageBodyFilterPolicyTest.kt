package com.jameskbride.localsns.models

import org.junit.jupiter.api.Test

class MessageBodyFilterPolicyTest {

    @Test
    fun `it matches a message with a filter policy`() {
        val policyJson = """
            {"store": ["example_corp_1"]}
        """.trimIndent()

        val filterPolicy = MessageBodyFilterPolicy(policyJson)

        val message = """
            {"store": "example_corp_1"}
        """.trimIndent()
        assert(filterPolicy.matches(message))
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
        assert(!filterPolicy.matches(message))
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
        assert(!filterPolicy.matches(message))
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
        assert(filterPolicy.matches(message))
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
        assert(filterPolicy.matches(message))
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
        assert(!filterPolicy.matches(message))
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
        assert(!filterPolicy.matches(message))
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
        assert(filterPolicy.matches(message))
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
        assert(!filterPolicy.matches(message))
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
        assert(!filterPolicy.matches(message))
    }
}