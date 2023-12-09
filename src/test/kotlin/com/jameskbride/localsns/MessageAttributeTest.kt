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
}