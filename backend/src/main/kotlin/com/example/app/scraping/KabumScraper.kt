package com.example.app.scraping

import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.math.BigDecimal
import java.net.URI

object KabumParser {
    fun parse(html: String): ScrapeResult {
        val doc = Jsoup.parse(html)

        val nextData = parseNextData(doc)
        val offer = jsonLdOffer(doc)

        val title = extractNextDataTitle(nextData)
            ?: doc.selectFirst("h1[class*='sc-']")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
            ?: offer?.get("name")?.primitiveContent()
            ?: throw ScrapeException.Parse("Título não encontrado na página do KaBuM!")

        val price = extractPrice(doc, nextData, offer)
            ?: throw ScrapeException.Parse("Preço não encontrado na página do KaBuM!")

        val currency = doc.selectFirst("meta[itemprop=priceCurrency]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
            ?: offer?.get("priceCurrency")?.primitiveContent()
            ?: "BRL"

        val inStock = determineInStock(doc, nextData, offer)
        val imageUrl = extractImage(doc, nextData)

        return ScrapeResult(
            title = title,
            price = price,
            currency = currency.uppercase(),
            inStock = inStock,
            imageUrl = imageUrl,
        )
    }

    private fun extractPrice(doc: Document, nextData: JsonObject?, offer: JsonObject?): BigDecimal? {
        // 1. Next.js __NEXT_DATA__
        if (nextData != null) {
            val priceFromNext = extractNextDataPrice(nextData)
            if (priceFromNext != null) return priceFromNext
        }

        // 2. JSON-LD offer price
        offer?.get("price")?.primitiveContent()?.let {
            val parsed = PriceStringParser.parse(it)
            if (parsed != null) return parsed
        }

        // 3. Meta tags
        val metaPrice = doc.selectFirst("meta[property=product:price:amount], meta[itemprop=price]")?.attr("content")
        val parsedMeta = PriceStringParser.parse(metaPrice)
        if (parsedMeta != null) return parsedMeta

        // 4. Seletores de preço do KaBuM! no DOM
        val domSelectors = listOf(
            "h4.finalPrice",
            "h4[class*='finalPrice']",
            "div[class*='priceCard'] h4",
            ".preco_desconto",
            "b.regularPrice",
        )
        for (sel in domSelectors) {
            val el = doc.selectFirst(sel)
            val parsed = PriceStringParser.parse(el?.text())
            if (parsed != null) return parsed
        }

        return null
    }

    private fun determineInStock(doc: Document, nextData: JsonObject?, offer: JsonObject?): Boolean {
        // 1. Next.js props
        if (nextData != null) {
            val available = extractNextDataAvailability(nextData)
            if (available != null) return available
        }

        // 2. DOM text
        val text = doc.text().lowercase()
        val outOfStockIndicators = listOf(
            "produto esgotado",
            "avise-me quando chegar",
            "produto indisponível",
            "sem estoque",
        )
        if (outOfStockIndicators.any { text.contains(it) }) return false

        // 3. JSON-LD availability
        val jsonLdAvail = offer?.get("availability")?.primitiveContent()
        if (jsonLdAvail != null) {
            return jsonLdAvail.contains("InStock", ignoreCase = true)
        }

        return true
    }

    private fun extractImage(doc: Document, nextData: JsonObject?): String? {
        val ogImage = doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
        if (isValidImageUrl(ogImage)) return ogImage

        if (nextData != null) {
            val nextImg = extractNextDataImage(nextData)
            if (isValidImageUrl(nextImg)) return nextImg
        }

        val domImg = doc.selectFirst("img[class*='imageSlide'], img[src*='kabum.com.br/produtos/fotos']")?.attr("src")?.trim()
        if (isValidImageUrl(domImg)) return domImg

        return jsonLdImage(doc)
    }

    private fun parseNextData(doc: Document): JsonObject? {
        val script = doc.selectFirst("script#__NEXT_DATA__") ?: return null
        return runCatching { Json.parseToJsonElement(script.data()) as? JsonObject }.getOrNull()
    }

    private fun extractNextDataTitle(nextData: JsonObject?): String? {
        if (nextData == null) return null
        val product = findProductData(nextData) ?: return null
        return (product["name"] as? JsonPrimitive)?.contentOrNull
            ?: (product["title"] as? JsonPrimitive)?.contentOrNull
    }

    private fun extractNextDataPrice(nextData: JsonObject): BigDecimal? {
        val product = findProductData(nextData) ?: return null
        val priceDetails = product["priceDetails"] as? JsonObject
        val pricePrimitive = (priceDetails?.get("discountPrice") as? JsonPrimitive)
            ?: (priceDetails?.get("price") as? JsonPrimitive)
            ?: (product["price"] as? JsonPrimitive)
            ?: (product["priceWithDiscount"] as? JsonPrimitive)
        return PriceStringParser.parse(pricePrimitive?.contentOrNull)
    }

    private fun extractNextDataAvailability(nextData: JsonObject): Boolean? {
        val product = findProductData(nextData) ?: return null
        val availablePrim = (product["available"] as? JsonPrimitive)
            ?: (product["inStock"] as? JsonPrimitive)
        return availablePrim?.booleanOrNull
    }

    private fun extractNextDataImage(nextData: JsonObject): String? {
        val product = findProductData(nextData) ?: return null
        val photos = product["photos"] as? JsonArray
        val firstPhoto = photos?.firstOrNull()
        if (firstPhoto is JsonPrimitive) return firstPhoto.contentOrNull
        if (firstPhoto is JsonObject) return (firstPhoto["url"] as? JsonPrimitive)?.contentOrNull
        return (product["image"] as? JsonPrimitive)?.contentOrNull
    }

    private fun findProductData(root: JsonObject): JsonObject? {
        val pageProps = (root["props"] as? JsonObject)?.get("pageProps") as? JsonObject ?: return null
        return (pageProps["productData"] as? JsonObject)
            ?: (pageProps["productDetails"] as? JsonObject)
            ?: (pageProps["product"] as? JsonObject)
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

class KabumScraper(private val fetcher: HtmlFetcher = HttpHtmlFetcher()) : PriceScraper {
    override val store: String = "KaBuM"

    override fun supports(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return host.contains("kabum.com.br")
    }

    override suspend fun scrape(url: String): ScrapeResult = KabumParser.parse(fetcher.fetch(url))
}
