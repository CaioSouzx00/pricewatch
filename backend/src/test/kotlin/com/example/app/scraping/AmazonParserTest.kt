package com.example.app.scraping

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmazonParserTest {

    @Test
    fun `extrai dados da Amazon via offscreen price e og image`() {
        val html = """
            <html><head>
              <title>Kindle Paperwhite 16 GB</title>
              <meta property="og:title" content="Kindle Paperwhite"/>
              <meta property="og:image" content="https://m.media-amazon.com/images/I/kindle.jpg"/>
            </head><body>
              <span id="productTitle">Kindle Paperwhite 16 GB - Cor Preta</span>
              <div id="corePriceDisplay_desktop_feature_div">
                <span class="a-price aok-align-center">
                  <span class="a-offscreen">R$ 799,00</span>
                </span>
              </div>
              <div id="availability">
                <span>Em estoque.</span>
              </div>
            </body></html>
        """.trimIndent()
        val r = AmazonParser.parse(html)
        assertEquals("Kindle Paperwhite 16 GB - Cor Preta", r.title)
        assertEquals(BigDecimal("799.00"), r.price)
        assertEquals("BRL", r.currency)
        assertEquals("https://m.media-amazon.com/images/I/kindle.jpg", r.imageUrl)
        assertTrue(r.inStock)
    }

    @Test
    fun `extrai preco estruturado por whole e fraction e detecta indisponivel`() {
        val html = """
            <html><head>
              <meta property="og:title" content="PlayStation 5 Edicao Digital"/>
            </head><body>
              <span class="a-price-whole">3.499</span>
              <span class="a-price-fraction">90</span>
              <div id="availability">
                <span>Não disponível no momento.</span>
              </div>
              <img id="landingImage" data-old-hires="https://m.media-amazon.com/images/I/ps5-hires.jpg"/>
            </body></html>
        """.trimIndent()
        val r = AmazonParser.parse(html)
        assertEquals("PlayStation 5 Edicao Digital", r.title)
        assertEquals(BigDecimal("3499.90"), r.price)
        assertFalse(r.inStock)
        assertEquals("https://m.media-amazon.com/images/I/ps5-hires.jpg", r.imageUrl)
    }

    @Test
    fun `lanca Blocked quando a Amazon exibe CAPTCHA ou Bot Check`() {
        val html = """
            <html><head>
              <title>Amazon CAPTCHA</title>
            </head><body>
              <h4>Digite os caracteres que você vê abaixo</h4>
            </body></html>
        """.trimIndent()
        val ex = assertFailsWith<ScrapeException.Blocked> {
            AmazonParser.parse(html)
        }
        assertTrue(ex.message!!.contains("CAPTCHA"))
    }

    @Test
    fun `lanca Parse quando nao encontra preco na pagina`() {
        val html = """
            <html><head><title>Livro</title></head><body><span id="productTitle">Livro Raro</span></body></html>
        """.trimIndent()
        assertFailsWith<ScrapeException.Parse> {
            AmazonParser.parse(html)
        }
    }

    @Test
    fun `scraper reconhece dominios amazon e amzn`() {
        val scraper = AmazonScraper(object : HtmlFetcher {
            override suspend fun fetch(url: String) = ""
        })
        assertTrue(scraper.supports("https://www.amazon.com.br/dp/B08N5WRWNW"))
        assertTrue(scraper.supports("https://amzn.to/3xyz"))
        assertFalse(scraper.supports("https://mercadolivre.com.br/MLB-123"))
    }
}
