package com.jameskbride.localsns

import com.jameskbride.localsns.models.MessageAttribute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageAttributeTest {

    @Test
    fun `it can parse StringValue attributes`() {
        val rawAttributes = mapOf(
            "MessageAttributes.entry.1.Name" to "status",
            "MessageAttributes.entry.1.Value.DataType" to "String",
            "MessageAttributes.entry.1.Value.StringValue" to "not_sent"
        ).entries.toList()

        val messageAttributes = MessageAttribute.parse(rawAttributes)
        assertTrue(messageAttributes.keys.contains("status"))

        val messageAttribute = messageAttributes["status"]
        assertEquals("status", messageAttribute!!.name)
        assertEquals("String", messageAttribute.dataType)
        assertEquals("not_sent", messageAttribute.value)
    }

    @Test
    fun `it can parse Number attributes`() {
        val rawAttributes = mapOf(
            "MessageAttributes.entry.1.Name" to "amount",
            "MessageAttributes.entry.1.Value.DataType" to "Number",
            "MessageAttributes.entry.1.Value.StringValue" to "10.56"
        ).entries.toList()

        val messageAttributes = MessageAttribute.parse(rawAttributes)
        assertTrue(messageAttributes.keys.contains("amount"))

        val messageAttribute = messageAttributes["amount"]
        assertEquals("amount", messageAttribute!!.name)
        assertEquals("Number", messageAttribute.dataType)
        assertEquals("10.56", messageAttribute.value)
    }
}