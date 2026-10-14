package com.example.app.alert

import com.example.app.auth.JwtConfig
import com.example.app.db.*
import com.example.app.dto.*
import com.example.app.module
import com.example.app.repository.AlertRepository
import com.example.app.repository.ProductRepository
import com.example.app.repository.UserRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.*

class AlertsRoutesTest {
    private val jwtConfig = JwtConfig(
        secret = "test-secret-test-secret-test-secret-123",
        issuer = "test",
        audience = "test",
    )

    private val userRepo = UserRepository()
    private val productRepo = ProductRepository()
    private val alertRepo = AlertRepository()

    @BeforeTest
    fun setUp() {
        Database.connect("jdbc:h2:mem:alert_routes_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", driver = "org.h2.Driver")
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
    fun `GET alerts retorna nome e imagem do produto via JOIN`() = withApp { client ->
        val token = tokenFor("owner@example.com")
        val user = userRepo.findByEmail("owner@example.com")!!

        val product = productRepo.create(
            user.id,
            ProductCreateRequest(
                name = "Monitor UltraWide 29",
                url = "https://www.mercadolivre.com.br/monitor-123",
                imageUrl = "https://example.com/monitor.jpg",
                currentPrice = BigDecimal("1200.00"),
            )
        )

        val alert = alertRepo.create(
            user.id,
            AlertCreateRequest(
                productId = product.id,
                type = AlertType.PRICE_BELOW,
                targetPrice = BigDecimal("1000.00"),
            )
        )

        val response = client.get("/alerts") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status)

        val alerts = response.body<List<AlertResponse>>()
        assertEquals(1, alerts.size)
        val first = alerts.first()
        assertEquals(alert.id, first.id)
        assertEquals("Monitor UltraWide 29", first.productName)
        assertEquals("https://example.com/monitor.jpg", first.productImageUrl)
        assertEquals("1000.00", first.targetPrice?.toPlainString())
    }

    @Test
    fun `PUT alerts permite alterar preco alvo e valida dono`() = withApp { client ->
        val userToken = tokenFor("u1@example.com")
        val foreignToken = tokenFor("u2@example.com")
        val user = userRepo.findByEmail("u1@example.com")!!

        val product = productRepo.create(
            user.id,
            ProductCreateRequest(
                name = "Mouse Sem Fio",
                url = "https://www.amazon.com.br/dp/B0123",
            )
        )

        val created = client.post("/alerts") {
            bearerAuth(userToken)
            contentType(ContentType.Application.Json)
            setBody("""{"productId":${product.id},"type":"PRICE_BELOW","targetPrice":"150.00"}""")
        }.body<AlertResponse>()

        // Outro usuário tentando alterar: 404
        val foreignUpdate = client.put("/alerts/${created.id}") {
            bearerAuth(foreignToken)
            contentType(ContentType.Application.Json)
            setBody("""{"targetPrice":"120.00"}""")
        }
        assertEquals(HttpStatusCode.NotFound, foreignUpdate.status)

        // Alteração com valor inválido: 400
        val invalidUpdate = client.put("/alerts/${created.id}") {
            bearerAuth(userToken)
            contentType(ContentType.Application.Json)
            setBody("""{"targetPrice":"-10.00"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, invalidUpdate.status)

        // Alteração com sucesso pelo dono
        val successUpdate = client.put("/alerts/${created.id}") {
            bearerAuth(userToken)
            contentType(ContentType.Application.Json)
            setBody("""{"targetPrice":"120.00"}""")
        }
        assertEquals(HttpStatusCode.OK, successUpdate.status)
        val updated = successUpdate.body<AlertResponse>()
        assertEquals("120.00", updated.targetPrice?.toPlainString())
        assertEquals("Mouse Sem Fio", updated.productName)
    }

    @Test
    fun `PATCH alerts toggle altera ativo e redefinicao zera estado disparado`() = withApp { client ->
        val token = tokenFor("toggle@example.com")
        val user = userRepo.findByEmail("toggle@example.com")!!

        val product = productRepo.create(
            user.id,
            ProductCreateRequest(
                name = "Teclado RGB",
                url = "https://www.kabum.com.br/produto/123",
            )
        )

        val alert = alertRepo.create(
            user.id,
            AlertCreateRequest(
                productId = product.id,
                type = AlertType.PRICE_BELOW,
                targetPrice = BigDecimal("200.00"),
            )
        )

        // Simula disparo prévio
        alertRepo.markTriggered(alert.id, deactivate = true)
        val triggeredAlert = alertRepo.findById(alert.id)!!
        assertFalse(triggeredAlert.active)
        assertNotNull(triggeredAlert.triggeredAt)

        // Reativa via PATCH /alerts/{id}/toggle
        val toggleResponse = client.patch("/alerts/${alert.id}/toggle") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, toggleResponse.status)
        val reactivated = toggleResponse.body<AlertResponse>()
        assertTrue(reactivated.active)
        assertNull(reactivated.triggeredAt, "Reativar um alerta disparado deve zerar triggeredAt para voltar a ser avaliado")

        // Desativa novamente via toggle
        val deactivateResponse = client.patch("/alerts/${alert.id}/toggle") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, deactivateResponse.status)
        assertFalse(deactivateResponse.body<AlertResponse>().active)
    }
}
