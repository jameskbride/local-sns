package com.jameskbride.localsns.models

private const val ENTRY_PATTERN = "PublishBatchRequestEntries\\.member\\.(\\d+)\\.(.*?)"

// ids can have alpha-numeric characters, hyphens, and underscores, up to 80 characters
private const val ID_PATTERN = "[a-zA-Z0-9_-]{1,80}"
data class PublishBatchRequestEntry(val id: String, val message: String, val messageStructure: String? = null, val messageAttributes: Map<String, MessageAttribute> = mapOf()) {
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

                val messageStructure = attributes.find {
                    val messageStructurePattern = "PublishBatchRequestEntries\\.member\\.$entryNumber.MessageStructure"
                    it.key.matches(messageStructurePattern.toRegex())
                }?.value

                val messageAttributes = MessageAttribute.parse(attributes.filter {
                    val messageAttributesPattern = "PublishBatchRequestEntries\\.member\\.$entryNumber.MessageAttributes\\.entry\\.(\\d+)\\.(.*?)"
                    it.key.matches(messageAttributesPattern.toRegex())
                })

                mapOf(entryNumber to PublishBatchRequestEntry(id, message, messageStructure, messageAttributes))
            }.fold(mapOf<Int, PublishBatchRequestEntry>()) { acc, map -> acc + map }

            return publishBatchRequestEntries
        }

        fun isValidId(id: String): Boolean {
            return id.matches(ID_PATTERN.toRegex())
        }
    }
}