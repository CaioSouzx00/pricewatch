package com.example.app.scraping

import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.math.BigDecimal
import java.net.URI

object AmazonParser {
    fun parse(html: String): ScrapeResult {
        val doc = Jsoup.parse(html)

        // Verificação de bloqueio anti-bot / CAPTCHA da Amazon
        val titleText = doc.title().lowercase()
        val bodyText = doc.text().lowercase()
        if (titleText.contains("robot check") ||
            titleText.contains("bot check") ||
            titleText.contains("amazon captcha") ||
            bodyText.contains("digite os caracteres que você vê abaixo") ||
            bodyText.contains("to discuss automated access to amazon data")
        ) {
            throw ScrapeException.Blocked("Amazon detectou tráfego automatizado (CAPTCHA/Bot Check)")
        }

        val offer = jsonLdOffer(doc)

        val title = doc.selectFirst("#productTitle")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[name=title]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
            ?: offer?.get("name")?.primitiveContent()
            ?: throw ScrapeException.Parse("Título não encontrado na página da Amazon")

        val price = extractPrice(doc, offer)
            ?: throw ScrapeException.Parse("Preço não encontrado na página da Amazon")

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
        // 1. .a-price .a-offscreen (preço principal na Amazon desktop)
        for (el in doc.select(".a-price.aok-align-center .a-offscreen, #corePriceDisplay_desktop_feature_div .a-price .a-offscreen, .a-price .a-offscreen")) {
            val parsed = PriceStringParser.parse(el.text())
            if (parsed != null) return parsed
        }

        // 2. Elementos legados de preço
        val legacySelectors = listOf("#priceblock_ourprice", "#priceblock_dealprice", "#priceblock_saleprice", "#price_inside_buybox")
        for (sel in legacySelectors) {
            val text = doc.selectFirst(sel)?.text()
            val parsed = PriceStringParser.parse(text)
            if (parsed != null) return parsed
        }

        // 3. Fração e centavos estruturados: .a-price-whole + .a-price-fraction
        val whole = doc.selectFirst(".a-price-whole")?.text()?.replace(".", "")?.replace(",", "")?.trim()
        if (!whole.isNullOrEmpty()) {
            val fraction = doc.selectFirst(".a-price-fraction")?.text()?.trim() ?: "00"
            val combined = "$whole.$fraction".toBigDecimalOrNull()
            if (combined != null && combined > BigDecimal.ZERO) return combined
        }

        // 4. JSON-LD offer price
        offer?.get("price")?.primitiveContent()?.let {
            val parsed = PriceStringParser.parse(it)
            if (parsed != null) return parsed
        }

        // 5. Meta tags
        val metaPrice = doc.selectFirst("meta[name=twitter:data1], meta[property=product:price:amount], meta[itemprop=price]")?.attr("content")
        return PriceStringParser.parse(metaPrice)
    }

    private fun determineInStock(doc: Document, offer: JsonObject?): Boolean {
        val availabilityText = doc.selectFirst("#availability")?.text()?.lowercase()
        if (availabilityText != null) {
            val unavailableIndicators = listOf("não disponível", "indisponível", "currently unavailable", "sem previsão", "fora de estoque")
            if (unavailableIndicators.any { availabilityText.contains(it) }) return false
            val inStockIndicators = listOf("em estoque", "disponível", "apenas")
            if (inStockIndicators.any { availabilityText.contains(it) }) return true
        }

        val jsonLdAvail = offer?.get("availability")?.primitiveContent()
        if (jsonLdAvail != null) {
            return jsonLdAvail.contains("InStock", ignoreCase = true)
        }

        return true
    }

    private fun extractImage(doc: Document): String? {
        val ogImage = doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
        if (isValidImageUrl(ogImage)) return ogImage

        val landingImg = doc.selectFirst("#landingImage, #imgBlkFront")?.attr("data-old-hires")?.trim()?.takeIf { isValidImageUrl(it) }
            ?: doc.selectFirst("#landingImage, #imgBlkFront")?.attr("src")?.trim()?.takeIf { isValidImageUrl(it) }
        if (landingImg != null) return landingImg

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

class AmazonScraper(private val fetcher: HtmlFetcher = HttpHtmlFetcher()) : PriceScraper {
    override val store: String = "Amazon"

    override fun supports(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return host.contains("amazon.") || host.contains("amzn.")
    }

    override suspend fun scrape(url: String): ScrapeResult = AmazonParser.parse(fetcher.fetch(url))
}
