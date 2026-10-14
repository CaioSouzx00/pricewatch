package com.example.app.scraping

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KabumParserTest {

    @Test
    fun `extrai dados do KaBuM via dados do DOM e meta tags`() {
        val html = """
            <html><head>
              <meta property="og:title" content="Placa de Video RTX 4070"/>
              <meta property="og:image" content="https://images.kabum.com.br/produtos/fotos/rtx4070.jpg"/>
            </head><body>
              <h1 class="sc-58326705-0">Placa de Video RTX 4070 12GB</h1>
              <h4 class="finalPrice">R$ 4.199,99</h4>
            </body></html>
        """.trimIndent()
        val r = KabumParser.parse(html)
        assertEquals("Placa de Video RTX 4070 12GB", r.title)
        assertEquals(BigDecimal("4199.99"), r.price)
        assertEquals("BRL", r.currency)
        assertEquals("https://images.kabum.com.br/produtos/fotos/rtx4070.jpg", r.imageUrl)
        assertTrue(r.inStock)
    }

    @Test
    fun `extrai dados via NEXT_DATA do Nextjs e detecta produto esgotado`() {
        val html = """
            <html><head></head><body>
              <h1>Processador Ryzen 7 7800X3D</h1>
              <p>Produto esgotado</p>
              <script id="__NEXT_DATA__" type="application/json">
                {
                  "props": {
                    "pageProps": {
                      "productData": {
                        "name": "Processador AMD Ryzen 7 7800X3D",
                        "price": 2899.90,
                        "available": false,
                        "photos": ["https://images.kabum.com.br/produtos/fotos/ryzen7.jpg"]
                      }
                    }
                  }
                }
              </script>
            </body></html>
        """.trimIndent()
        val r = KabumParser.parse(html)
        assertEquals("Processador AMD Ryzen 7 7800X3D", r.title)
        assertEquals(BigDecimal("2899.90"), r.price)
        assertEquals("https://images.kabum.com.br/produtos/fotos/ryzen7.jpg", r.imageUrl)
        assertFalse(r.inStock)
    }

    @Test
    fun `lanca Parse quando nao ha preco na pagina do KaBuM`() {
        val html = """
            <html><body><h1>Mouse Gamer</h1><p>Sem valor</p></body></html>
        """.trimIndent()
        assertFailsWith<ScrapeException.Parse> {
            KabumParser.parse(html)
        }
    }

    @Test
    fun `scraper reconhece dominio kabum`() {
        val scraper = KabumScraper(object : HtmlFetcher {
            override suspend fun fetch(url: String) = ""
        })
        assertTrue(scraper.supports("https://www.kabum.com.br/produto/12345/teclado"))
        assertFalse(scraper.supports("https://www.amazon.com.br/dp/1234"))
    }
}
