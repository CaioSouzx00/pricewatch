package com.example.app.scraping

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MercadoLivreParserTest {

    @Test
    fun `extrai dados via meta tags`() {
        val html = """
            <html><head>
              <meta itemprop="price" content="1299.90"/>
              <meta itemprop="priceCurrency" content="BRL"/>
              <meta itemprop="availability" content="https://schema.org/InStock"/>
            </head><body><h1 class="ui-pdp-title">Notebook Gamer</h1></body></html>
        """.trimIndent()
        val r = MercadoLivreParser.parse(html)
        assertEquals("Notebook Gamer", r.title)
        assertEquals(BigDecimal("1299.90"), r.price)
        assertEquals("BRL", r.currency)
        assertTrue(r.inStock)
    }

    @Test
    fun `extrai preco visivel e detecta indisponivel`() {
        val html = """
            <html><body><h1 class="ui-pdp-title">Fone</h1>
            <div class="ui-pdp-price__second-line"><span class="andes-money-amount">
              <span class="andes-money-amount__fraction">1.299</span>
              <span class="andes-money-amount__cents">90</span></span></div>
            <p>Publicação pausada</p></body></html>
        """.trimIndent()
        val r = MercadoLivreParser.parse(html)
        assertEquals(BigDecimal("1299.90"), r.price)
        assertFalse(r.inStock)
    }

    @Test
    fun `usa JSON-LD como fallback`() {
        val html = """
            <html><head><meta property="og:title" content="Mouse"/>
            <script type="application/ld+json">
              {"@type":"Product","offers":{"price":59.9,"priceCurrency":"BRL","availability":"https://schema.org/OutOfStock"}}
            </script></head><body></body></html>
        """.trimIndent()
        val r = MercadoLivreParser.parse(html)
        assertEquals("Mouse", r.title)
        assertEquals(BigDecimal("59.9"), r.price)
        assertFalse(r.inStock)
    }

    @Test
    fun `html sem dados lanca erro de parse`() {
        assertFailsWith<ScrapeException.Parse> { MercadoLivreParser.parse("<html><body>oi</body></html>") }
    }

    @Test
    fun `scraper reconhece apenas urls do mercado livre`() {
        val scraper = MercadoLivreScraper(object : HtmlFetcher {
            override suspend fun fetch(url: String) = ""
        })
        assertTrue(scraper.supports("https://produto.mercadolivre.com.br/MLB-123"))
        assertFalse(scraper.supports("https://example.com/x"))
    }
}
