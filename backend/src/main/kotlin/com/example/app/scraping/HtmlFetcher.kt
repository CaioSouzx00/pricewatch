package com.example.app.scraping

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

interface HtmlFetcher {
    /** Retorna o corpo HTML ou lança [ScrapeException]. */
    suspend fun fetch(url: String): String
}

/** Intervalo mínimo entre requisições ao mesmo host. */
class RateLimiter(private val minIntervalMs: Long = 2_000) {
    private val mutex = Mutex()
    private val lastRequest = HashMap<String, Long>()

    suspend fun acquire(host: String) = mutex.withLock {
        val wait = (lastRequest[host] ?: 0L) + minIntervalMs - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        lastRequest[host] = System.currentTimeMillis()
    }
}

class HttpHtmlFetcher(
    private val rateLimiter: RateLimiter = RateLimiter(),
    private val userAgent: String = System.getenv("SCRAPER_USER_AGENT")?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT,
    private val timeoutMs: Int = 15_000,
    private val maxAttempts: Int = 3,
) : HtmlFetcher {

    override suspend fun fetch(url: String): String {
        val host = runCatching { URI(url).host }.getOrNull() ?: throw ScrapeException.Parse("URL inválida")
        var backoff = 1_000L
        var last: ScrapeException? = null
        repeat(maxAttempts) { attempt ->
            rateLimiter.acquire(host)
            try {
                return request(url)
            } catch (e: ScrapeException.Blocked) {
                throw e
            } catch (e: ScrapeException.Network) {
                last = e
                if (attempt < maxAttempts - 1) {
                    delay(backoff)
                    backoff *= 2
                }
            }
        }
        throw last ?: ScrapeException.Network("Falha ao buscar página")
    }

    private suspend fun request(url: String): String = withContext(Dispatchers.IO) {
        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofMillis(timeoutMs.toLong()))
            .build()
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", userAgent)
            .header("Accept-Language", "pt-BR,pt;q=0.9")
            .timeout(Duration.ofMillis(timeoutMs.toLong()))
            .GET()
            .build()

        val response = try {
            client.send(req, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            throw ScrapeException.Network("Erro de rede: ${e.message}", e)
        }
        val code = response.statusCode()
        when {
            code == 429 || code == 403 -> throw ScrapeException.Blocked("Bloqueado pelo site (HTTP $code)")
            code >= 500 -> throw ScrapeException.Network("Erro do servidor (HTTP $code)")
            code !in 200..299 -> throw ScrapeException.Parse("Resposta inesperada (HTTP $code)")
            else -> response.body()
        }
    }

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0 Safari/537.36"
    }
}
