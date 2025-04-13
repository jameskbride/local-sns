package com.jameskbride.localsns.models

import com.google.gson.Gson
import com.google.gson.JsonObject

class MessageBodyFilterPolicy(filterPolicy: String) {
    private val gson = Gson()
    private val filterPolicyJsonObject = gson.fromJson(filterPolicy, JsonObject::class.java)

    fun matches(message: String): Boolean {
        val messageJsonObject = gson.fromJson(message, JsonObject::class.java)

        val allKeysPresent = filterPolicyJsonObject.asMap().all { messageJsonObject.has(it.key) }
        if (!allKeysPresent) {
            return false
        }

        val matches = filterPolicyJsonObject.asMap().all {
            val filterValue = it.value.asJsonArray
            val messageValue = messageJsonObject.get(it.key)
            filterValue.asList().contains(messageValue)
        }

        return matches
    }
}