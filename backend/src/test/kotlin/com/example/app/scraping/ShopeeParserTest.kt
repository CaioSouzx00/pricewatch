package com.example.app.scraping

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShopeeParserTest {

    @Test
    fun `extrai dados da Shopee quando fornecidos via JSON-LD`() {
        val html = """
            <html><head>
              <meta property="og:title" content="Cabo USB-C Trançado"/>
              <meta property="og:image" content="https://down-br.img.susercontent.com/file/cabo.jpg"/>
              <script type="application/ld+json">
                {
                  "@type": "Product",
                  "name": "Cabo USB-C 2 Metros",
                  "offers": {
                    "price": 29.90,
                    "priceCurrency": "BRL",
                    "availability": "https://schema.org/InStock"
                  }
                }
              </script>
            </head><body></body></html>
        """.trimIndent()
        val r = ShopeeParser.parse(html)
        assertEquals("Cabo USB-C Trançado", r.title)
        assertEquals(BigDecimal("29.90"), r.price)
        assertEquals("BRL", r.currency)
        assertEquals("https://down-br.img.susercontent.com/file/cabo.jpg", r.imageUrl)
        assertTrue(r.inStock)
    }

    @Test
    fun `extrai preco da Shopee via meta tag og description`() {
        val html = """
            <html><head>
              <meta property="og:title" content="Smartwatch Relogio Inteligente"/>
              <meta property="og:description" content="Compre Smartwatch Relogio Inteligente por R$ 89,90 na Shopee Brasil!"/>
              <meta property="og:image" content="https://down-br.img.susercontent.com/file/relogio.jpg"/>
            </head><body></body></html>
        """.trimIndent()
        val r = ShopeeParser.parse(html)
        assertEquals("Smartwatch Relogio Inteligente", r.title)
        assertEquals(BigDecimal("89.90"), r.price)
        assertEquals("https://down-br.img.susercontent.com/file/relogio.jpg", r.imageUrl)
        assertTrue(r.inStock)
    }

    @Test
    fun `lanca Parse tipado quando a pagina e uma SPA vazia renderizada via JS`() {
        val html = """
            <!DOCTYPE html><html><head><title>Shopee Brasil</title></head>
            <body><div id="main"></div><script src="bundle.js"></script></body></html>
        """.trimIndent()
        val ex = assertFailsWith<ScrapeException.Parse> {
            ShopeeParser.parse(html)
        }
        assertTrue(ex.message!!.contains("JavaScript"))
    }

    @Test
    fun `scraper reconhece dominio shopee`() {
        val scraper = ShopeeScraper(object : HtmlFetcher {
            override suspend fun fetch(url: String) = ""
        })
        assertTrue(scraper.supports("https://shopee.com.br/product/123/456"))
        assertFalse(scraper.supports("https://amazon.com.br/dp/123"))
    }
}
