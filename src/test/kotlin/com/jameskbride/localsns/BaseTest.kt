package com.jameskbride.localsns

import com.jameskbride.localsns.models.Topic
import com.typesafe.config.ConfigFactory
import khttp.post
import khttp.get
import khttp.responses.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.jupiter.api.Assertions

open class BaseTest {
    protected fun postFormData(payload: Map<String, String?>): Response {
        val baseUrl = getBaseUrl()
        return post(baseUrl, data = payload)
    }

    companion object {
        fun getBaseUrl(): String {
            val config = ConfigFactory.load()
            val httpInterface = config.getString("http.interface")
            val httpPortEnv = config.getString("http.port")
            return "http://$httpInterface:$httpPortEnv/"
        }
    }

    fun getCurrentConfig(): Response {
        val baseUrl = getBaseUrl()
        return get("$baseUrl/config")
    }

    protected fun createValidArn(resourceName: String) =
        "arn:aws:sns:us-east-1:123456789012:${resourceName}"

    fun createCamelSqsEndpoint(name: String, port: Int? = null): String {
        val portString = if (port != null) {
            "$port"
        } else {
            "9324"
        }
        return "aws2-sqs://$name?accessKey=xxx&secretKey=xxx&region=us-east-1&trustAllCertificates=true&overrideEndpoint=true&uriEndpointOverride=http://localhost:$portString/000000000000/$name"
    }

    fun createHttpSqsEndpoint(name: String, protocolAndHost: String, port: String? = null): String {
        val portString = if (port != null) {
            ":$port"
        } else {
            ""
        }
        return "$protocolAndHost$portString/000000000000/$name"
    }

    fun createCamelHttpEndpoint(uri: String, method: String = "POST"): String {
        return "$uri?httpMethod=$method"
    }

    protected fun createTopic(name: String): Response {
        val payload = mapOf(
            "Action" to "CreateTopic",
            "Name" to name
        )

        return postFormData(payload)
    }

    fun deleteTopic(arn: String): Response {
        val payload = mapOf(
            "Action" to "DeleteTopic",
            "TopicArn" to arn
        )

        return postFormData(payload)
    }

    fun listTopics(): Response {
        val payload = mapOf(
            "Action" to "ListTopics",
        )

        return postFormData(payload)
    }

    fun getTopicArnFromResponse(topicResponse: Response): String {
        val doc = Jsoup.parse(topicResponse.text)
        return doc.select("TopicArn").text()
    }

    fun createTopicModel(topicName: String): Topic {
        val topicResponse = createTopic(topicName)
        val topicArn = getTopicArnFromResponse(topicResponse)
        return Topic(arn = topicArn, name = topicName)
    }

    fun getSubscriptionArnFromResponse(subscriptionResponse: Response): String {
        val doc = Jsoup.parse(subscriptionResponse.text)
        return doc.select("SubscriptionArn").text()
    }

    fun listSubscriptions(): Response {
        val data = mapOf(
            "Action" to "ListSubscriptions"
        )
        return postFormData(data)
    }

    fun listSubscriptionsByTopic(topicArn: String? = null): Response {
        val data = mutableMapOf(
            "Action" to "ListSubscriptionsByTopic"
        )

        if (topicArn != null) {
            data["TopicArn"] = topicArn
        }

        return postFormData(data)
    }

    fun subscribe(topicArn: String?, endpoint: String?, protocol: String?, subscriptionAttributes: Map<String, String> = mapOf()): Response {
        val data = mutableMapOf(
            "Action" to "Subscribe",
        )
        if (topicArn != null) {
            data["TopicArn"] = topicArn
        }
        if (endpoint != null) {
            data["Endpoint"] = endpoint
        }
        if (protocol != null) {
            data["Protocol"] = protocol
        }

        subscriptionAttributes.onEachIndexed { index, entry ->
            val oneBasedIndex = index + 1
            data["Attributes.entry.$oneBasedIndex.key"]= entry.key
            data["Attributes.entry.$oneBasedIndex.value"]= entry.value
        }

        return postFormData(data)
    }

    fun unsubscribe(subscriptionArn: String? = null): Response {
        val data = mutableMapOf(
            "Action" to "Unsubscribe"
        )

        if (subscriptionArn != null) {
            data["SubscriptionArn"] = subscriptionArn
        }

        return postFormData(data)
    }

    fun setSubscriptionAttributes(subscriptionArn: String?, attributeName: String?, attributeValue: String?): Response {
        val data = mutableMapOf(
            "Action" to "SetSubscriptionAttributes"
        )

        if (subscriptionArn != null) { data["SubscriptionArn"] = subscriptionArn }
        if (attributeName != null) { data["AttributeName"] = attributeName }
        if (attributeValue != null) { data["AttributeValue"] = attributeValue }

        return postFormData(data)
    }

    fun getSubscriptionAttributes(subscriptionArn: String?): Response {
        val data = mutableMapOf(
            "Action" to "GetSubscriptionAttributes"
        )

        if (subscriptionArn != null) { data["SubscriptionArn"] = subscriptionArn }

        return postFormData(data)
    }

    fun getEntries(response: Response): List<Element> {
        val doc = Jsoup.parse(response.text)
        return doc.select("entry").toList()
    }

    fun assertAttributeFound(
        entries: List<Element>,
        attributeName: String,
        attributeValue: String
    ) {
        Assertions.assertTrue(entries.any { it.text().contains(attributeName) && it.text().contains(attributeValue) })
    }
}