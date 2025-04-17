package com.jameskbride.localsns.subscriptions

import com.google.gson.*

fun getElementType(firstElement: JsonElement?): Any? {
    return when (firstElement) {
        is JsonArray -> "Array"
        is JsonObject -> "Object"
        is JsonNull -> "null"
        is JsonPrimitive -> {
            if (firstElement.isString) {
                "String"
            } else if (firstElement.isBoolean) {
                "Boolean"
            } else if (firstElement.isNumber) {
                "Number"
            } else {
                null
            }
        }
        else -> null
    }
}

fun validateNumericMatcher(numericMatcher: JsonObject?): Double {
    if (numericMatcher == null) {
        throw IllegalArgumentException("Numeric matcher cannot be null")
    }
    if (!numericMatcher.has("numeric")) {
        throw IllegalArgumentException("Only numeric matcher is supported")
    }
    val numericMatcherArray = numericMatcher.getAsJsonArray("numeric")
    if (numericMatcherArray.size() != 2) {
        throw IllegalArgumentException("Numeric matcher must have exactly 2 elements")
    }

    val operator = numericMatcherArray[0].asString
    if (operator != "=") {
        throw IllegalArgumentException("Only equality operator is supported")
    }
    return numericMatcherArray[1].asDouble
}