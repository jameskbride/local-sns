package com.jameskbride.localsns.models

import java.io.Serializable

private const val ATTRIBUTE_PATTERN = ".*Attributes\\.entry\\.(\\d+)\\.(.*?)"

data class MessageAttribute(val name:String, val value:String, val dataType:String = "String"): Serializable {
    companion object {
        fun parse(attributes: List<Map.Entry<String, String>>): Map<String, MessageAttribute> {
            val pattern = ATTRIBUTE_PATTERN.toRegex()
            val entryNumbers = attributes.map { attribute ->
                val match = pattern.matchEntire(attribute.key)
                match!!.groupValues[1].toInt()
            }.distinct()

            val messageAttributes = entryNumbers.map {entryNumber ->
                val name = attributes.find {
                    val namePattern = ".*Attributes\\.entry\\.$entryNumber.Name"
                    it.key.matches(namePattern.toRegex())
                }!!.value

                val value = attributes.find {
                    val namePattern = ".*Attributes\\.entry\\.$entryNumber.Value.StringValue"
                    it.key.matches(namePattern.toRegex())
                }!!.value

                val dataType = attributes.find {
                    val namePattern = ".*Attributes\\.entry\\.$entryNumber.Value.DataType"
                    it.key.matches(namePattern.toRegex())
                }!!.value

                mapOf(name to MessageAttribute(name, value, dataType))
            }.fold(mapOf<String, MessageAttribute>()) { acc, map -> acc + map }

            return messageAttributes
        }
    }
}
