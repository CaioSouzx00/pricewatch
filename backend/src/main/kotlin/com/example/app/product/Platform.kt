package com.example.app.product

import com.example.app.auth.ApiException
import io.ktor.http.*
import java.net.URI

object PlatformDetector {
    private val platforms: List<Pair<Set<String>, String>> = listOf(
        setOf("mercadolivre", "mercadolibre") to "Mercado Livre",
        setOf("shopee") to "Shopee",
        setOf("amazon", "amzn") to "Amazon",
        setOf("magazineluiza", "magalu") to "Magazine Luiza",
        setOf("americanas") to "Americanas",
        setOf("aliexpress") to "AliExpress",
        setOf("kabum") to "KaBuM",
        setOf("casasbahia") to "Casas Bahia",
    )

    /** Retorna o nome da plataforma conhecida ou o próprio host (sem "www.") se desconhecida. */
    fun detect(url: String): String {
        val host = URI(url).host.lowercase().removePrefix("www.")
        val labels = host.split('.')
        return platforms.firstOrNull { (keys, _) -> labels.any { it in keys } }?.second ?: host
    }
}

object UrlValidator {
    private const val MAX_LENGTH = 2048

    /** Valida e retorna a URL normalizada (trim). Lança [ApiException] 400 se inválida. */
    fun validate(raw: String): String {
        val url = raw.trim()
        val invalid = ApiException(HttpStatusCode.BadRequest, "URL inválida")
        if (url.isEmpty() || url.length > MAX_LENGTH) throw invalid
        val uri = runCatching { URI(url) }.getOrNull() ?: throw invalid
        if (uri.scheme?.lowercase() !in setOf("http", "https")) throw invalid
        if (uri.userInfo != null) throw invalid
        val host = uri.host ?: throw invalid
        if (!host.contains('.') || host.startsWith('.') || host.endsWith('.')) throw invalid
        return url
    }
}
