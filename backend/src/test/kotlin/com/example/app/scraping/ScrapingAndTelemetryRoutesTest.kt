package com.example.app.scraping

import com.example.app.HealthResponse
import com.example.app.auth.JwtConfig
import com.example.app.db.*
import com.example.app.dto.*
import com.example.app.module
import com.example.app.repository.PriceHistoryRepository
import com.example.app.repository.ProductRepository
import com.example.app.repository.ScrapeLogRepository
import com.example.app.repository.UserRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import kotlin.test.*

class ScrapingAndTelemetryRoutesTest {
    private val jwtConfig = JwtConfig(
        secret = "test-secret-test-secret-test-secret-123",
        issuer = "test",
        audience = "test",
    )

    private val userRepo = UserRepository()
    private val productRepo = ProductRepository()
    private val historyRepo = PriceHistoryRepository()
    private val scrapeLogRepo = ScrapeLogRepository()

    @BeforeTest
    fun setUp() {
        Database.connect("jdbc:h2:mem:telemetry_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(UsersTable, ProductsTable, PriceHistoryTable, AlertsTable, ScrapeLogsTable)
        }
    }

    @AfterTest
    fun tearDown() {
        transaction {
            ScrapeLogsTable.deleteAll()
            AlertsTable.deleteAll()
            PriceHistoryTable.deleteAll()
            ProductsTable.deleteAll()
            UsersTable.deleteAll()
        }
    }

    private fun tokenFor(email: String): String {
        val user = userRepo.create(UserCreateRequest(email, "Teste", "senha123"), "hash")
        return jwtConfig.generateToken(user.id)
    }

    private fun withApp(block: suspend (HttpClient) -> Unit) = testApplication {
        application { module(jwtConfig) }
        val client = createClient { install(ContentNegotiation) { json() } }
        block(client)
    }

    @Test
    fun `GET health e publico e sem autenticacao`() = withApp { client ->
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<HealthResponse>()
        assertEquals("ok", body.status)
    }

    @Test
    fun `GET scraper stats exige autenticacao e retorna metricas`() = withApp { client ->
        // Sem token: 401
        assertEquals(HttpStatusCode.Unauthorized, client.get("/scraper/stats").status)

        val token = tokenFor("admin@example.com")
        val user = userRepo.findByEmail("admin@example.com")!!

        val p1 = productRepo.create(user.id, ProductCreateRequest(name = "Item 1", url = "https://www.mercadolivre.com.br/1"))
        val p2 = productRepo.create(user.id, ProductCreateRequest(name = "Item 2", url = "https://www.amazon.com.br/dp/2"))

        // Adiciona logs de sucesso e bloqueio
        scrapeLogRepo.add(p1.id, success = true, errorMessage = null, latencyMs = 250, store = "Mercado Livre", blocked = false)
        scrapeLogRepo.add(p2.id, success = false, errorMessage = "Bloqueado pelo site (HTTP 429)", latencyMs = 120, store = "Amazon", blocked = true)

        val response = client.get("/scraper/stats?window=24h") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val stats = response.body<ScraperStatsResponse>()
        assertEquals(2, stats.total)
        assertEquals(1, stats.successCount)
        assertEquals(1, stats.failedCount)
        assertEquals(1, stats.blockedCount)
        assertEquals(50.0, stats.successRate)
        assertEquals(250.0, stats.averageLatencyMs)
        assertEquals(1, stats.recentFailures.size)
        assertEquals("Amazon", stats.recentFailures.first().store)
        assertTrue(stats.recentFailures.first().blocked)
    }

    @Test
    fun `GET export de historico exporta em CSV e JSON com cabecalhos corretos`() = withApp { client ->
        val token = tokenFor("exporter@example.com")
        val foreignToken = tokenFor("other@example.com")
        val user = userRepo.findByEmail("exporter@example.com")!!

        val product = productRepo.create(user.id, ProductCreateRequest(name = "Smartphone", url = "https://www.kabum.com.br/1"))
        historyRepo.add(product.id, BigDecimal("1999.00"))
        historyRepo.add(product.id, BigDecimal("1850.50"))

        // Outro usuário tentando exportar: 404
        val foreign = client.get("/products/${product.id}/history/export?format=csv") { bearerAuth(foreignToken) }
        assertEquals(HttpStatusCode.NotFound, foreign.status)

        // Export formato inválido: 400
        val invalid = client.get("/products/${product.id}/history/export?format=xml") { bearerAuth(token) }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)

        // Export CSV
        val csvResponse = client.get("/products/${product.id}/history/export?format=csv") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, csvResponse.status)
        assertTrue(csvResponse.headers[HttpHeaders.ContentType]!!.contains("text/csv"))
        val disposition = csvResponse.headers[HttpHeaders.ContentDisposition]!!
        assertTrue(disposition.contains("attachment") && disposition.contains("price-history-${product.id}.csv"))
        val csvBody = csvResponse.bodyAsText()
        assertTrue(csvBody.startsWith("id,productId,price,checkedAt"))
        assertTrue(csvBody.contains("1999.00"))
        assertTrue(csvBody.contains("1850.50"))

        // Export JSON
        val jsonResponse = client.get("/products/${product.id}/history/export?format=json") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, jsonResponse.status)
        assertTrue(jsonResponse.headers[HttpHeaders.ContentType]!!.contains("application/json"))
        val jsonDisposition = jsonResponse.headers[HttpHeaders.ContentDisposition]!!
        assertTrue(jsonDisposition.contains("attachment") && jsonDisposition.contains("price-history-${product.id}.json"))
        val jsonList = jsonResponse.body<List<PriceHistoryResponse>>()
        assertEquals(2, jsonList.size)
    }
}
