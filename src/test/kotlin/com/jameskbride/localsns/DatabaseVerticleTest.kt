package com.jameskbride.localsns

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.jameskbride.localsns.models.Configuration
import com.jameskbride.localsns.models.Subscription
import com.jameskbride.localsns.models.Topic
import com.jameskbride.localsns.serialization.ConfigurationTypeAdapter
import com.jameskbride.localsns.serialization.SubscriptionTypeAdapter
import com.jameskbride.localsns.serialization.TopicTypeAdapter
import com.jameskbride.localsns.verticles.DatabaseVerticle
import com.typesafe.config.ConfigFactory
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class DatabaseVerticleTest: BaseTest() {

    @BeforeEach
    fun setup(vertx: Vertx, testContext: VertxTestContext) {
        val mapper = DatabindCodec.mapper()
        mapper.registerKotlinModule()

        val prettyMapper = DatabindCodec.prettyMapper()
        prettyMapper.registerKotlinModule()
        vertx.deployVerticle(DatabaseVerticle(), testContext.succeeding { _ -> testContext.completeNow() })
    }

    //Skipping on CI for now until I can figure out why it fails there
    @Tag("skipForCI")
    @Test
    fun `it persists the db when a configChange is detected`(vertx: Vertx, testContext: VertxTestContext) {
        val topic = Topic(arn=createValidArn("topic1"), name="topic1")
        val subscription = Subscription(
            topicArn = topic.arn,
            arn = createValidArn("subscription1"),
            owner="owner",
            protocol="sqs",
            endpoint=createCamelSqsEndpoint("queue1")
        )

        val topics = getTopicsMap(vertx)!!
        topics[topic.arn] = topic

        val subscriptions = getSubscriptionsMap(vertx)!!
        subscriptions[topic.arn] = subscriptions.getOrDefault(topic.arn, listOf()) + subscription

        val config = ConfigFactory.load()

        vertx.eventBus().consumer<String>("configChangeComplete") {
            vertx.fileSystem()
                .readFile(getDbOutputPath(config))
                .onComplete {result ->
                    val configFile = result.result().toString()
                    val gson = Gson()
                    val configuration = gson.fromJson(configFile, Configuration::class.java)
                    assertEquals(configuration.version, 1)
                    assertTrue(configuration.topics.contains(topic))
                    assertTrue(configuration.subscriptions.contains(subscription))
                    testContext.completeNow()
                }
        }

        vertx.eventBus().publish("configChange", "conf")
    }

    @Tag("skipForCI")
    @Test
    fun `it persists subscription attributes`(vertx: Vertx, testContext: VertxTestContext) {
        val topic = Topic(arn=createValidArn("topic1"), name="topic1")
        val subscription = Subscription(
            topicArn = topic.arn,
            arn = createValidArn("subscription1"),
            owner="owner",
            protocol="sqs",
            endpoint=createCamelSqsEndpoint("queue1"),
            subscriptionAttributes = mapOf("RawMessageDelivery" to "true")
        )

        val topics = getTopicsMap(vertx)!!
        topics[topic.arn] = topic

        val subscriptions = getSubscriptionsMap(vertx)!!
        subscriptions[topic.arn] = subscriptions.getOrDefault(topic.arn, listOf()) + subscription

        val config = ConfigFactory.load()

        vertx.eventBus().consumer<String>("configChangeComplete") {
            vertx.fileSystem()
                .readFile(getDbOutputPath(config))
                .onComplete {result ->
                    val configFile = result.result().toString()
                    val gson = Gson()
                    val configuration = gson.fromJson(configFile, Configuration::class.java)
                    assertEquals(configuration.version, 1)
                    assertTrue(configuration.topics.contains(topic))
                    assertTrue(configuration.subscriptions.contains(subscription))
                    val foundSubscription = configuration.subscriptions[0]
                    assertEquals(foundSubscription.subscriptionAttributes["RawMessageDelivery"], "true")
                    testContext.completeNow()
                }
        }

        vertx.eventBus().publish("configChange", "conf")
    }

    @Tag("skipForCI")
    @Test
    fun `it ensures empty collections for the Configuration are deserialized correctly`(vertx: Vertx, testContext: VertxTestContext) {
        val configJson = """
            {
                "version": 1,
                "timestamp": ${System.currentTimeMillis()},
                "topics": null,
                "subscriptions": null
            }
        """.trimIndent()

        val configPath = getDbOutputPath(ConfigFactory.load())
        vertx.fileSystem()
            .writeFile(configPath, Buffer.buffer(configJson))
            .onComplete {
                vertx.fileSystem()
                    .readFile(configPath)
                    .onComplete { result ->
                        val configFile = result.result().toString()
                        val gson = GsonBuilder()
                            .registerTypeAdapter(Configuration::class.java, ConfigurationTypeAdapter())
                            .registerTypeAdapter(Subscription::class.java, SubscriptionTypeAdapter())
                            .registerTypeAdapter(Topic::class.java, TopicTypeAdapter())
                            .create()
                        val configuration = gson.fromJson(configFile, Configuration::class.java)

                        assertEquals(1, configuration.version)
                        assertNotNull(configuration.topics)
                        assertTrue(configuration.topics.isEmpty())
                        assertNotNull(configuration.subscriptions)
                        assertTrue(configuration.subscriptions.isEmpty())
                        testContext.completeNow()
                    }
            }
    }

    @Tag("skipForCI")
    @Test
    fun `it insures empty collections for Subscriptions in the Configuration are deserialized correctly`(vertx: Vertx, testContext: VertxTestContext) {
        val configJson = """
            {
                "version": 1,
                "timestamp": ${System.currentTimeMillis()},
                "topics": null,
                "subscriptions": [
                    {
                        "topicArn": "arn:aws:sns:us-east-1:123456789012:topic1",
                        "arn": "arn:aws:sns:us-east-1:123456789012:subscription1",
                        "owner": "owner",
                        "protocol": "sqs",
                        "endpoint": "http://example.com",
                        "subscriptionAttributes": null
                    }
                ]
            }
        """.trimIndent()

        val configPath = getDbOutputPath(ConfigFactory.load())
        vertx.fileSystem()
            .writeFile(configPath, Buffer.buffer(configJson))
            .onComplete {
                vertx.fileSystem()
                    .readFile(configPath)
                    .onComplete { result ->
                        val configFile = result.result().toString()
                        val gson = GsonBuilder()
                            .registerTypeAdapter(Configuration::class.java, ConfigurationTypeAdapter())
                            .registerTypeAdapter(Subscription::class.java, SubscriptionTypeAdapter())
                            .registerTypeAdapter(Topic::class.java, TopicTypeAdapter())
                            .create()

                        // Use TypeToken to specify the type of the Configuration
                        val configurationType = object : TypeToken<Configuration>() {}.type
                        val configuration = gson.fromJson<Configuration>(configFile, configurationType)

                        assertEquals(1, configuration.version)
                        assertNotNull(configuration.topics)
                        assertTrue(configuration.topics.isEmpty())
                        assertNotNull(configuration.subscriptions)
                        assertEquals(1, configuration.subscriptions.size)

                        val subscription = configuration.subscriptions[0]
                        assertEquals("arn:aws:sns:us-east-1:123456789012:topic1", subscription.topicArn)
                        assertEquals("arn:aws:sns:us-east-1:123456789012:subscription1", subscription.arn)
                        assertEquals("owner", subscription.owner)
                        assertEquals("sqs", subscription.protocol)
                        assertEquals("http://example.com", subscription.endpoint)
                        assertNotNull(subscription.subscriptionAttributes)
                        assertTrue(subscription.subscriptionAttributes.isEmpty())

                        testContext.completeNow()
                    }
            }
    }
}