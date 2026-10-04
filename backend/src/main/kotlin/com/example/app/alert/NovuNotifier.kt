package com.example.app.alert

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Cliente da API REST do Novu (`POST /v1/events/trigger`).
 * O workflow no Novu deve ter um passo de e-mail usando as variáveis do `payload`.
 */
class NovuNotifier(
    private val apiKey: String,
    private val workflowId: String = "price-alert",
    private val baseUrl: String = "https://api.novu.co",
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
) : Notifier {

    override suspend fun send(notification: AlertNotification) {
        val request = HttpRequest.newBuilder(URI.create("${baseUrl.trimEnd('/')}/v1/events/trigger"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "ApiKey $apiKey")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(buildBody(notification).toString()))
            .build()
        val response = withContext(Dispatchers.IO) { client.send(request, HttpResponse.BodyHandlers.ofString()) }
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Novu respondeu HTTP ${response.statusCode()}: ${response.body().take(200)}")
        }
    }

    internal fun buildBody(n: AlertNotification): JsonObject = buildJsonObject {
        put("name", workflowId)
        put("to", buildJsonObject {
            put("subscriberId", n.userId.toString())
            put("email", n.email)
            put("firstName", n.userName)
        })
        put("payload", buildJsonObject {
            put("alertType", n.alertType.name)
            put("message", n.message)
            put("productName", n.productName)
            put("productUrl", n.productUrl)
            put("currency", n.currency)
            put("oldPrice", n.oldPrice?.toPlainString())
            put("newPrice", n.newPrice.toPlainString())
            put("inStock", n.inStock)
        })
    }
}

/** Usado quando o Novu não está configurado: apenas registra no log. */
class LoggingNotifier : Notifier {
    private val log = LoggerFactory.getLogger(LoggingNotifier::class.java)

    override suspend fun send(notification: AlertNotification) {
        log.info("[Novu não configurado] E-mail para {}: {}", notification.email, notification.message)
    }
}

fun notifierFromEnv(): Notifier {
    val key = System.getenv("NOVU_API_KEY")?.takeIf { it.isNotBlank() } ?: return LoggingNotifier()
    return NovuNotifier(
        apiKey = key,
        workflowId = System.getenv("NOVU_WORKFLOW_ID")?.takeIf { it.isNotBlank() } ?: "price-alert",
        baseUrl = System.getenv("NOVU_API_URL")?.takeIf { it.isNotBlank() } ?: "https://api.novu.co",
    )
}
