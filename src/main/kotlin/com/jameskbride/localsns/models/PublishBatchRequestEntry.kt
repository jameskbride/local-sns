package com.jameskbride.localsns.models

private const val ENTRY_PATTERN = "PublishBatchRequestEntries\\.member\\.(\\d+)\\.(.*?)"

data class PublishBatchRequestEntry(val id: String, val message: String) {
    companion object {
        fun parse(attributes: List<Map.Entry<String, String>>): Map<Int, PublishBatchRequestEntry> {
            val pattern = ENTRY_PATTERN.toRegex()
            val entryNumbers = attributes.map { attribute ->
                val match = pattern.matchEntire(attribute.key)
                match!!.groupValues[1].toInt()
            }.distinct()

            val publishBatchRequestEntries = entryNumbers.map { entryNumber ->
                val id = attributes.find {
                    val idPattern = "PublishBatchRequestEntries\\.member\\.$entryNumber.Id"
                    it.key.matches(idPattern.toRegex())
                }!!.value

                val message = attributes.find {
                    val messagePattern = "PublishBatchRequestEntries\\.member\\.$entryNumber.Message"
                    it.key.matches(messagePattern.toRegex())
                }!!.value

                mapOf(entryNumber to PublishBatchRequestEntry(id, message))
            }.fold(mapOf<Int, PublishBatchRequestEntry>()) { acc, map -> acc + map }

            return publishBatchRequestEntries
        }
    }
}