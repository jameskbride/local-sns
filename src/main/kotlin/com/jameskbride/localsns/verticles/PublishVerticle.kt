package com.jameskbride.localsns.verticles

import com.google.gson.Gson
import com.jameskbride.localsns.topics.PublishRequest
import com.jameskbride.localsns.topics.publishBasicMessageToTopic
import com.jameskbride.localsns.topics.publishJsonStructure
import io.vertx.core.AbstractVerticle
import org.apache.camel.impl.DefaultCamelContext

class PublishVerticle: AbstractVerticle() {

    private val logger = org.apache.logging.log4j.LogManager.getLogger(PublishVerticle::class.java)

    override fun start() {
        logger.info("Starting publish service...")
        val vertx = this.vertx
        val gson = Gson()
        vertx.eventBus().consumer<String>("publishJsonStructure") { message ->
            val publishRequest = gson.fromJson(message.body(), PublishRequest::class.java)
            val camelContext = DefaultCamelContext()
            val producerTemplate = camelContext.createProducerTemplate()
            camelContext.start()
            publishJsonStructure(
                publishRequest,
                producerTemplate,
                vertx
            )
        }

        vertx.eventBus().consumer<String>("publishBasicMessage") { message ->
            val publishRequest = gson.fromJson(message.body(), PublishRequest::class.java)
            val camelContext = DefaultCamelContext()
            val producerTemplate = camelContext.createProducerTemplate()
            camelContext.start()
            publishBasicMessageToTopic(
                publishRequest,
                producerTemplate,
                vertx
            )
        }
    }
}