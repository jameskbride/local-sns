package com.jameskbride.localsns.models

private const val ENTRY_PATTERN = ".*PublishBatchRequestEntry\\.entry\\.(\\d+)\\.(.*?)"

data class PublishBatchRequestEntry(val id: String, val message: String) {
    companion object {
        fun parse(attributes: List<Map.Entry<String, String>>): Map<String, PublishBatchRequestEntry> {
            val pattern = ENTRY_PATTERN.toRegex()
            val entryNumbers = attributes.map { attribute ->
                val match = pattern.matchEntire(attribute.key)
                match!!.groupValues[1].toInt()
            }.distinct()

            val publishBatchRequestEntries = entryNumbers.map { entryNumber ->
                val id = attributes.find {
                    val idPattern = ".*PublishBatchRequestEntry\\.entry\\.$entryNumber.Id"
                    it.key.matches(idPattern.toRegex())
                }!!.value

                val message = attributes.find {
                    val messagePattern = ".*PublishBatchRequestEntry\\.entry\\.$entryNumber.Message"
                    it.key.matches(messagePattern.toRegex())
                }!!.value

                mapOf(id to PublishBatchRequestEntry(id, message))
            }.fold(mapOf<String, PublishBatchRequestEntry>()) { acc, map -> acc + map }

            return publishBatchRequestEntries
        }
    }
}