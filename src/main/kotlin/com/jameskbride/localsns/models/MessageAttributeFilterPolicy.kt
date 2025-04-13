package com.jameskbride.localsns.models

import com.google.gson.Gson
import com.google.gson.JsonObject

class MessageAttributeFilterPolicy(val filterPolicy: String) {
    private val gson = Gson()
    private val filterPolicyJsonObject = gson.fromJson(filterPolicy, JsonObject::class.java)

    fun matches(messageAttributes: Map<String, MessageAttribute>): Boolean {
        return filterPolicyJsonObject.asMap().all {
            messageAttributes.containsKey(it.key)
        }
    }
}