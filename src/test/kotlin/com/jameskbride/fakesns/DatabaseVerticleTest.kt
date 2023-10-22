package com.jameskbride.fakesns

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jameskbride.fakesns.models.Configuration
import com.jameskbride.fakesns.models.Subscription
import com.jameskbride.fakesns.models.Topic
import com.jameskbride.fakesns.verticles.DatabaseVerticle
import com.typesafe.config.ConfigFactory
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
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

    @Test
    fun `it persists the db when a configChange is detected`(vertx: Vertx, testContext: VertxTestContext) {
        val topic = Topic(arn=createValidArn("topic1"), name="topic1")
        val subscription = Subscription(
            topicArn = topic.arn,
            arn = createValidArn("subscription1"),
            owner="owner",
            protocol="sqs",
            endpoint=createEndpoint("queue1")
        )

        val topics = getTopicsMap(vertx)!!
        topics[topic.arn] = topic

        val subscriptions = getSubscriptionsMap(vertx)!!
        subscriptions[topic.arn] = subscriptions.getOrDefault(topic.arn, listOf()) + subscription

        val config = ConfigFactory.load()

        vertx.eventBus().publish("configChange", "conf")
        vertx.eventBus().consumer<String>("configChangeComplete") {
            vertx.fileSystem()
                .readFile(getDbOutputPath(config))
                .onComplete {result ->
                    val configFile = result.result()
                    val jsonConfig = JsonObject(configFile)

                    val configuration = jsonConfig.mapTo(Configuration::class.java)
                    assertEquals(configuration.version, 1)
                    assertTrue(configuration.topics.contains(topic))
                    assertTrue(configuration.subscriptions.contains(subscription))
                    testContext.completeNow()
                }
        }
    }
}