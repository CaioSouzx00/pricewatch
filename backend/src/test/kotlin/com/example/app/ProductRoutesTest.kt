package com.example.app

import com.example.app.auth.JwtConfig
import com.example.app.db.AlertsTable
import com.example.app.db.PriceHistoryTable
import com.example.app.db.ProductsTable
import com.example.app.db.UsersTable
import com.example.app.dto.ProductResponse
import com.example.app.dto.UserCreateRequest
import com.example.app.product.PlatformDetector
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductRoutesTest {
    private val jwtConfig = JwtConfig(
        secret = "test-secret-test-secret-test-secret-123",
        issuer = "test",
        audience = "test",
    )

    @BeforeTest
    fun setUp() {
        Database.connect("jdbc:h2:mem:product_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(UsersTable, ProductsTable, PriceHistoryTable, AlertsTable) }
    }

    @AfterTest
    fun tearDown() {
        transaction {
            AlertsTable.deleteAll()
            PriceHistoryTable.deleteAll()
            ProductsTable.deleteAll()
            UsersTable.deleteAll()
        }
    }

    private fun tokenFor(email: String): String {
        val user = UserRepository().create(UserCreateRequest(email, "Teste", "ignorada"), "hash")
        return jwtConfig.generateToken(user.id)
    }

    private fun withApp(block: suspend (HttpClient) -> Unit) = testApplication {
        application { module(jwtConfig) }
        val client = createClient { install(ContentNegotiation) { json() } }
        block(client)
    }

    private suspend fun HttpClient.createProduct(
        token: String,
        url: String = "https://www.mercadolivre.com.br/produto-123",
        name: String = "Fone",
    ) = post("/products") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody("""{"name":"$name","url":"$url","currentPrice":"199.90"}""")
    }

    @Test
    fun `detecta plataformas conhecidas e host desconhecido`() {
        assertEquals("Mercado Livre", PlatformDetector.detect("https://produto.mercadolivre.com.br/MLB-1"))
        assertEquals("Shopee", PlatformDetector.detect("https://shopee.com.br/item-i.1.2"))
        assertEquals("Amazon", PlatformDetector.detect("https://www.amazon.com.br/dp/B0"))
        assertEquals("loja.exemplo.com", PlatformDetector.detect("https://loja.exemplo.com/p/1"))
    }

    @Test
    fun `rotas exigem autenticacao`() = withApp { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/products").status)
    }

    @Test
    fun `post cria produto associado ao usuario com plataforma detectada`() = withApp { client ->
        val response = client.createProduct(tokenFor("a@example.com"))
        assertEquals(HttpStatusCode.Created, response.status)
        val product = response.body<ProductResponse>()
        assertEquals("Mercado Livre", product.store)
        assertEquals("199.90", product.currentPrice?.toPlainString())
        assertEquals("BRL", product.currency)
    }

    @Test
    fun `post com url invalida retorna 400`() = withApp { client ->
        val token = tokenFor("a@example.com")
        listOf("nao-e-url", "ftp://loja.com/x", "javascript:alert(1)", "https://localhost/x").forEach {
            assertEquals(HttpStatusCode.BadRequest, client.createProduct(token, url = it).status, it)
        }
    }

    @Test
    fun `post com nome em branco retorna 400`() = withApp { client ->
        val response = client.createProduct(tokenFor("a@example.com"), name = "  ")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `get lista apenas produtos do proprio usuario`() = withApp { client ->
        val a = tokenFor("a@example.com")
        val b = tokenFor("b@example.com")
        client.createProduct(a)
        client.createProduct(a)
        client.createProduct(b)
        assertEquals(2, client.get("/products") { bearerAuth(a) }.body<List<ProductResponse>>().size)
        assertEquals(1, client.get("/products") { bearerAuth(b) }.body<List<ProductResponse>>().size)
    }

    @Test
    fun `get por id de outro usuario retorna 404`() = withApp { client ->
        val a = tokenFor("a@example.com")
        val b = tokenFor("b@example.com")
        val id = client.createProduct(a).body<ProductResponse>().id
        assertEquals(HttpStatusCode.OK, client.get("/products/$id") { bearerAuth(a) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/products/$id") { bearerAuth(b) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/products/abc") { bearerAuth(a) }.status)
    }

    @Test
    fun `put atualiza campos e redetecta plataforma`() = withApp { client ->
        val token = tokenFor("a@example.com")
        val id = client.createProduct(token).body<ProductResponse>().id
        val response = client.put("/products/$id") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Novo nome","url":"https://shopee.com.br/x-i.1.2","active":false}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val updated = response.body<ProductResponse>()
        assertEquals("Novo nome", updated.name)
        assertEquals("Shopee", updated.store)
        assertEquals(false, updated.active)
        assertEquals("199.90", updated.currentPrice?.toPlainString())
    }

    @Test
    fun `put de outro usuario retorna 404 e url invalida retorna 400`() = withApp { client ->
        val a = tokenFor("a@example.com")
        val b = tokenFor("b@example.com")
        val id = client.createProduct(a).body<ProductResponse>().id
        val foreign = client.put("/products/$id") {
            bearerAuth(b)
            contentType(ContentType.Application.Json)
            setBody("""{"name":"hack"}""")
        }
        assertEquals(HttpStatusCode.NotFound, foreign.status)
        val invalid = client.put("/products/$id") {
            bearerAuth(a)
            contentType(ContentType.Application.Json)
            setBody("""{"url":"lixo"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
    }

    @Test
    fun `delete remove produto e respeita dono`() = withApp { client ->
        val a = tokenFor("a@example.com")
        val b = tokenFor("b@example.com")
        val id = client.createProduct(a).body<ProductResponse>().id
        assertEquals(HttpStatusCode.NotFound, client.delete("/products/$id") { bearerAuth(b) }.status)
        assertEquals(HttpStatusCode.NoContent, client.delete("/products/$id") { bearerAuth(a) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/products/$id") { bearerAuth(a) }.status)
        assertTrue(client.get("/products") { bearerAuth(a) }.body<List<ProductResponse>>().isEmpty())
    }
}
