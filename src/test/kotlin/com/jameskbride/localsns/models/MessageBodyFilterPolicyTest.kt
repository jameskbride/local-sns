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
}