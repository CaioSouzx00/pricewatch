package com.example.app.scraping

class PriceScraperRegistry(private val scrapers: List<PriceScraper>) {
    fun findScraper(url: String): PriceScraper? = scrapers.firstOrNull { it.supports(url) }

    fun findByStore(store: String): PriceScraper? = scrapers.firstOrNull { it.store.equals(store, ignoreCase = true) }

    fun all(): List<PriceScraper> = scrapers
}
