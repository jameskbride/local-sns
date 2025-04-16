package com.jameskbride.localsns.serialization

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.jameskbride.localsns.models.Configuration
import com.jameskbride.localsns.models.Subscription
import com.jameskbride.localsns.models.Topic
import java.lang.reflect.Type

class ConfigurationTypeAdapter : JsonDeserializer<Configuration>, JsonSerializer<Configuration> {

    private val subscriptionListTokenType = object : TypeToken<List<Subscription>>() {}.type
    private val topicListTokenType = object : TypeToken<List<Topic>>() {}.type

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Configuration {
        val jsonObject = json.asJsonObject
        val version = jsonObject.get("version").asInt
        val timestamp = jsonObject.get("timestamp").asLong

        val subscriptions = if (jsonObject.has("subscriptions") && !jsonObject.get("subscriptions").isJsonNull) {
            context.deserialize<List<Subscription>>(jsonObject.get("subscriptions"), subscriptionListTokenType)
        } else {
            listOf()
        }

        val topics = if (jsonObject.has("topics") && !jsonObject.get("topics").isJsonNull) {
            context.deserialize<List<Topic>>(jsonObject.get("topics"), topicListTokenType)
        } else {
            listOf()
        }

        return Configuration(version, timestamp, subscriptions, topics)
    }

    override fun serialize(src: Configuration, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val jsonObject = JsonObject()
        jsonObject.addProperty("version", src.version)
        jsonObject.addProperty("timestamp", src.timestamp)
        jsonObject.add("subscriptions", context.serialize(src.subscriptions, subscriptionListTokenType))
        jsonObject.add("topics", context.serialize(src.topics, topicListTokenType))
        return jsonObject
    }
}

class TopicTypeAdapter : JsonSerializer<Topic>, JsonDeserializer<Topic> {
    override fun serialize(src: Topic, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val jsonObject = JsonObject()
        jsonObject.addProperty("arn", src.arn)
        jsonObject.addProperty("name", src.name)
        return jsonObject
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Topic {
        val jsonObject = json.asJsonObject
        return Topic(
            arn = jsonObject.get("arn").asString,
            name = jsonObject.get("name").asString
        )
    }
}

class SubscriptionTypeAdapter : JsonDeserializer<Subscription>, JsonSerializer<Subscription> {

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Subscription {
        val jsonObject = json.asJsonObject
        val subscriptionAttributes = if (jsonObject.has("subscriptionAttributes") && !jsonObject.get("subscriptionAttributes").isJsonNull) {
            context.deserialize<Map<String, String>>(jsonObject.get("subscriptionAttributes"), Map::class.java)
        } else {
            emptyMap()
        }

        return Subscription(
            arn = jsonObject.get("arn").asString,
            owner = jsonObject.get("owner").asString,
            topicArn = jsonObject.get("topicArn").asString,
            endpoint = jsonObject.get("endpoint").asString,
            protocol = jsonObject.get("protocol").asString,
            subscriptionAttributes = subscriptionAttributes
        )
    }

    override fun serialize(src: Subscription, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val jsonObject = JsonObject()
        jsonObject.addProperty("arn", src.arn)
        jsonObject.addProperty("owner", src.owner)
        jsonObject.addProperty("topicArn", src.topicArn)
        jsonObject.addProperty("endpoint", src.endpoint)
        jsonObject.addProperty("protocol", src.protocol)
        jsonObject.add("subscriptionAttributes", context.serialize(src.subscriptionAttributes))
        return jsonObject
    }
}