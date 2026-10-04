package com.example.app.scraping

import java.net.URI

class MercadoLivreScraper(private val fetcher: HtmlFetcher = SkrapeHtmlFetcher()) : PriceScraper {
    override val store = "Mercado Livre"

    override fun supports(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return host.contains("mercadolivre") || host.contains("mercadolibre")
    }

    override suspend fun scrape(url: String): ScrapeResult = MercadoLivreParser.parse(fetcher.fetch(url))
}
