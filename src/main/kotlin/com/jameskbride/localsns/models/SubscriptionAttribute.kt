package com.jameskbride.localsns.models

private const val SUBSCRIPTION_ATTRIBUTE_PATTERN = ".*Attributes\\.entry\\.(\\d+)\\.(.*?)"

data class SubscriptionAttribute(val name:String, val value:String) {
    companion object {
        fun parse(attributes: List<MutableMap.MutableEntry<String, String>>): Map<String, String> {
            val pattern = SUBSCRIPTION_ATTRIBUTE_PATTERN.toRegex()
            val entryNumbers = attributes.map { attribute ->
                val match = pattern.matchEntire(attribute.key)
                match!!.groupValues[1].toInt()
            }.distinct()

            val subscriptionAttributes = entryNumbers.map { entryNumber ->
                val name = attributes.find {
                    val namePattern = ".*Attributes\\.entry\\.$entryNumber.key"
                    it.key.matches(namePattern.toRegex())
                }!!.value

                val value = attributes.find {
                    val namePattern = ".*Attributes\\.entry\\.$entryNumber.value"
                    it.key.matches(namePattern.toRegex())
                }!!.value

                mapOf(name to value)
            }.fold(mapOf<String, String>()) { acc, map -> acc + map }

            return subscriptionAttributes
        }
    }
}
