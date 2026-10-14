package com.example.app.scraping

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.math.BigDecimal

/** Parser puro (sem rede) do HTML de páginas de produto do Mercado Livre. */
object MercadoLivreParser {

    fun parse(html: String): ScrapeResult {
        val doc = Jsoup.parse(html)
        val offer = jsonLdOffer(doc)

        val title = doc.selectFirst("h1.ui-pdp-title")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ScrapeException.Parse("Título não encontrado")

        val price = metaPrice(doc)
            ?: offer?.get("price")?.primitiveContent()?.toBigDecimalOrNull()
            ?: visiblePrice(doc)
            ?: throw ScrapeException.Parse("Preço não encontrado")

        val currency = doc.selectFirst("meta[itemprop=priceCurrency]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: offer?.get("priceCurrency")?.primitiveContent()
            ?: "BRL"

        val imageUrl = extractImage(doc)

        return ScrapeResult(title, price, currency.uppercase(), inStock(doc, offer), imageUrl)
    }

    private fun extractImage(doc: Document): String? {
        val ogImage = doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
        if (isValidImageUrl(ogImage)) return ogImage

        val jsonLdImg = jsonLdImage(doc)
        if (isValidImageUrl(jsonLdImg)) return jsonLdImg

        val domImage = doc.selectFirst(".ui-pdp-gallery img, img.ui-pdp-image, img[data-zoom]")?.attr("src")?.trim()
            ?: doc.selectFirst("img[src^=http]")?.attr("src")?.trim()
        if (isValidImageUrl(domImage)) return domImage

        return null
    }

    private fun jsonLdImage(doc: Document): String? {
        for (script in doc.select("script[type=application/ld+json]")) {
            val root = runCatching { Json.parseToJsonElement(script.data()) }.getOrNull() ?: continue
            val nodes = if (root is JsonArray) root else listOf(root)
            for (node in nodes) {
                val obj = node as? JsonObject ?: continue
                val img = obj["image"] ?: continue
                when (img) {
                    is JsonPrimitive -> img.contentOrNull?.let { return it }
                    is JsonArray -> img.firstOrNull()?.primitiveContent()?.let { return it }
                    is JsonObject -> img["url"]?.primitiveContent()?.let { return it }
                    else -> {}
                }
            }
        }
        return null
    }

    fun isValidImageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        return (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) &&
            !trimmed.startsWith("data:", ignoreCase = true)
    }

    private fun metaPrice(doc: Document): BigDecimal? =
        doc.selectFirst("meta[itemprop=price]")?.attr("content")?.trim()?.toBigDecimalOrNull()

    /** Ex.: fração "1.299" + centavos "90" => 1299.90 */
    private fun visiblePrice(doc: Document): BigDecimal? {
        val container = doc.selectFirst(".ui-pdp-price__second-line .andes-money-amount")
            ?: doc.selectFirst(".andes-money-amount") ?: return null
        val fraction = container.selectFirst(".andes-money-amount__fraction")?.text()
            ?.replace(".", "")?.trim().orEmpty()
        if (fraction.isEmpty()) return null
        val cents = container.selectFirst(".andes-money-amount__cents")?.text()?.trim()?.takeIf { it.isNotEmpty() } ?: "00"
        return "$fraction.$cents".toBigDecimalOrNull()
    }

    private fun inStock(doc: Document, offer: JsonObject?): Boolean {
        val availability = doc.selectFirst("meta[itemprop=availability]")?.attr("content")
            ?: doc.selectFirst("link[itemprop=availability]")?.attr("href")
            ?: offer?.get("availability")?.primitiveContent()
        if (availability != null) return availability.contains("InStock", ignoreCase = true)

        val text = doc.text().lowercase()
        val unavailable = listOf("publicação pausada", "produto indisponível", "estoque esgotado", "sem estoque")
        return unavailable.none { text.contains(it) }
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

    private fun JsonElement.primitiveContent(): String? = (this as? JsonPrimitive)?.contentOrNull
}
