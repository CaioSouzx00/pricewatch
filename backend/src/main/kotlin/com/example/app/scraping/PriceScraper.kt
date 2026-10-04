package com.example.app.scraping

import java.math.BigDecimal

/** Dados extraídos de uma página de produto. */
data class ScrapeResult(
    val title: String,
    val price: BigDecimal,
    val currency: String = "BRL",
    val inStock: Boolean,
)

sealed class ScrapeException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Falha de rede/timeout ou resposta 5xx (pode valer a pena tentar de novo). */
    class Network(message: String, cause: Throwable? = null) : ScrapeException(message, cause)

    /** 429/403: o site está limitando ou bloqueando. */
    class Blocked(message: String) : ScrapeException(message)

    /** HTML recebido não contém os dados esperados. */
    class Parse(message: String) : ScrapeException(message)
}

interface PriceScraper {
    /** Nome da loja, igual ao retornado por `PlatformDetector`. */
    val store: String

    fun supports(url: String): Boolean

    suspend fun scrape(url: String): ScrapeResult
}
