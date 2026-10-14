package com.example.app.scraping

import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.math.BigDecimal
import java.net.URI

object ShopeeParser {
    fun parse(html: String): ScrapeResult {
        val doc = Jsoup.parse(html)

        val offer = jsonLdOffer(doc)

        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: offer?.get("name")?.primitiveContent()
            ?: doc.title().trim().takeIf { it.isNotEmpty() && !it.contains("Shopee Brasil", ignoreCase = true) }

        val price = extractPrice(doc, offer)

        // Se a página for um SPA vazio sem dados estáticos no HTML
        if (title.isNullOrBlank() || price == null) {
            throw ScrapeException.Parse(
                "A Shopee renderiza seus produtos no cliente via JavaScript (Single Page Application). " +
                    "O HTML estático não contém dados de preço/título disponíveis para extração via Jsoup."
            )
        }

        val currency = doc.selectFirst("meta[itemprop=priceCurrency]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
            ?: offer?.get("priceCurrency")?.primitiveContent()
            ?: "BRL"

        val inStock = determineInStock(doc, offer)
        val imageUrl = extractImage(doc)

        return ScrapeResult(
            title = title,
            price = price,
            currency = currency.uppercase(),
            inStock = inStock,
            imageUrl = imageUrl,
        )
    }

    private fun extractPrice(doc: Document, offer: JsonObject?): BigDecimal? {
        // 1. JSON-LD offer price
        offer?.get("price")?.primitiveContent()?.let {
            val parsed = PriceStringParser.parse(it)
            if (parsed != null) return parsed
        }

        // 2. Meta tags específicas (og:price:amount, product:price:amount)
        val metaPrice = doc.selectFirst("meta[property=product:price:amount], meta[property=og:price:amount], meta[itemprop=price]")?.attr("content")
        val parsedMeta = PriceStringParser.parse(metaPrice)
        if (parsedMeta != null) return parsedMeta

        // 3. Fallback no og:description (ex: "Compre [Produto] por R$ 49,90 na Shopee")
        val ogDescription = doc.selectFirst("meta[property=og:description]")?.attr("content")
        if (!ogDescription.isNullOrBlank()) {
            val priceMatch = Regex("""(?:R\$|BRL)\s*([\d.,]+)""").find(ogDescription)
            if (priceMatch != null) {
                val parsed = PriceStringParser.parse(priceMatch.groupValues[1])
                if (parsed != null) return parsed
            }
        }

        // 4. Elementos HTML se houver SSR parcial
        val domPrice = doc.selectFirst(".pmmxKx, .IZhrqS, div[class*='price']")?.text()
        return PriceStringParser.parse(domPrice)
    }

    private fun determineInStock(doc: Document, offer: JsonObject?): Boolean {
        val jsonLdAvail = offer?.get("availability")?.primitiveContent()
        if (jsonLdAvail != null) {
            return jsonLdAvail.contains("InStock", ignoreCase = true)
        }

        val text = doc.text().lowercase()
        val outOfStockIndicators = listOf("esgotado", "produto indisponível", "sem estoque")
        if (outOfStockIndicators.any { text.contains(it) }) return false

        return true
    }

    private fun extractImage(doc: Document): String? {
        val ogImage = doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
        if (isValidImageUrl(ogImage)) return ogImage

        return jsonLdImage(doc)
    }

    private fun jsonLdImage(doc: Document): String? {
        for (script in doc.select("script[type=application/ld+json]")) {
            val root = runCatching { Json.parseToJsonElement(script.data()) }.getOrNull() ?: continue
            val nodes = if (root is JsonArray) root else listOf(root)
            for (node in nodes) {
                val obj = node as? JsonObject ?: continue
                val img = obj["image"] ?: continue
                when (img) {
                    is JsonPrimitive -> img.contentOrNull?.takeIf(::isValidImageUrl)?.let { return it }
                    is JsonArray -> img.firstOrNull()?.primitiveContent()?.takeIf(::isValidImageUrl)?.let { return it }
                    is JsonObject -> img["url"]?.primitiveContent()?.takeIf(::isValidImageUrl)?.let { return it }
                    else -> {}
                }
            }
        }
        return null
    }

    private fun jsonLdOffer(doc: Document): JsonObject? {
        for (script in doc.select("script[type=application/ld+json]")) {
            val root = runCatching { Json.parseToJsonElement(script.data()) }.getOrNull() ?: continue
            val nodes = if (root is JsonArray) root else listOf(root)
            for (node in nodes) {
                val offers = (node as? JsonObject)?.get("offers") ?: continue
                val offer = if (offers is JsonArray) offers.firstOrNull() else offers
                if (offer is JsonObject) return offer
            }
        }
        return null
    }

    private fun isValidImageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        return (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) &&
            !trimmed.startsWith("data:", ignoreCase = true)
    }

    private fun JsonElement.primitiveContent(): String? = (this as? JsonPrimitive)?.contentOrNull
}

class ShopeeScraper(private val fetcher: HtmlFetcher = HttpHtmlFetcher()) : PriceScraper {
    override val store: String = "Shopee"

    override fun supports(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return host.contains("shopee.com.br") || host.contains("shopee.")
    }

    override suspend fun scrape(url: String): ScrapeResult = ShopeeParser.parse(fetcher.fetch(url))
}
