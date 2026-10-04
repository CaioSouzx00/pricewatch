package com.example.app

import com.example.app.auth.AuthResponse
import com.example.app.auth.JwtConfig
import com.example.app.db.AlertsTable
import com.example.app.db.PriceHistoryTable
import com.example.app.db.ProductsTable
import com.example.app.db.UsersTable
import com.example.app.dto.UserResponse
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthRoutesTest {
    private val jwtConfig = JwtConfig(
        secret = "test-secret-test-secret-test-secret-123",
        issuer = "test",
        audience = "test",
    )

    @BeforeTest
    fun setUp() {
        Database.connect("jdbc:h2:mem:auth_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(UsersTable, ProductsTable, PriceHistoryTable, AlertsTable)
        }
    }

    @AfterTest
    fun tearDown() {
        transaction { UsersTable.deleteAll() }
    }

    private fun withApp(block: suspend ApplicationTestBuilder.(io.ktor.client.HttpClient) -> Unit) =
        testApplication {
            application { module(jwtConfig) }
            val client = createClient { install(ContentNegotiation) { json() } }
            block(client)
        }

    private suspend fun io.ktor.client.HttpClient.register(email: String = "ana@example.com") =
        post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","name":"Ana","password":"senha12345"}""")
        }

    @Test
    fun `register cria usuario e retorna token`() = withApp { client ->
        val response = client.register()
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<AuthResponse>()
        assertTrue(body.token.isNotBlank())
        assertEquals("ana@example.com", body.user.email)
    }

    @Test
    fun `register com email duplicado retorna 409`() = withApp { client ->
        client.register()
        assertEquals(HttpStatusCode.Conflict, client.register().status)
    }

    @Test
    fun `register com senha curta retorna 400`() = withApp { client ->
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"a@b.com","name":"Ana","password":"123"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `login com credenciais validas retorna token`() = withApp { client ->
        client.register()
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"ana@example.com","password":"senha12345"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.body<AuthResponse>().token.isNotBlank())
    }

    @Test
    fun `login com senha errada retorna 401`() = withApp { client ->
        client.register()
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"ana@example.com","password":"errada12345"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `me com token valido retorna usuario`() = withApp { client ->
        val token = client.register().body<AuthResponse>().token
        val response = client.get("/auth/me") { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ana@example.com", assertNotNull(response.body<UserResponse>()).email)
    }

    @Test
    fun `me sem token ou com token invalido retorna 401`() = withApp { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/auth/me").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/auth/me") { bearerAuth("lixo") }.status)
    }
}
