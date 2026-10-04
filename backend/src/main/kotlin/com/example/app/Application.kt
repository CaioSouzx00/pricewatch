package com.example.app

import com.example.app.alert.AlertEvaluator
import com.example.app.alert.Notifier
import com.example.app.alert.alertRoutes
import com.example.app.alert.notifierFromEnv
import com.example.app.auth.JwtConfig
import com.example.app.auth.authRoutes
import com.example.app.auth.configureErrorHandling
import com.example.app.auth.configureSecurity
import com.example.app.db.DatabaseFactory
import com.example.app.product.productRoutes
import com.example.app.product.dashboardRoutes
import com.example.app.repository.AlertRepository
import com.example.app.repository.ProductRepository
import com.example.app.repository.PriceHistoryRepository
import com.example.app.repository.UserRepository
import com.example.app.scraping.MercadoLivreScraper
import com.example.app.scraping.PriceScraper
import com.example.app.scraping.PriceTrackingService
import com.example.app.scraping.ScrapingJob
import com.example.app.scraping.scrapingRoutes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import org.slf4j.event.Level

@Serializable
data class HealthResponse(val status: String)

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") { module() }.start(wait = true)
}

fun Application.module(
    jwtConfig: JwtConfig = JwtConfig.fromEnv(),
    scrapers: List<PriceScraper> = listOf(MercadoLivreScraper()),
    notifier: Notifier = notifierFromEnv(),
) {
    configureLogging()
    configureCors()
    configureSerialization()
    configureErrorHandling()
    configureSecurity(jwtConfig)
    configureDatabase()

    val userRepository = UserRepository()
    val productRepository = ProductRepository()
    val historyRepository = PriceHistoryRepository()
    val alertRepository = AlertRepository()
    val scrapeLogRepository = com.example.app.repository.ScrapeLogRepository()
    val alertEvaluator = AlertEvaluator(alertRepository, userRepository, historyRepository, notifier)
    val trackingService = PriceTrackingService(scrapers, productRepository, historyRepository, scrapeLogRepository, alertEvaluator)

    routing {
        get("/health") { call.respond(HealthResponse("ok")) }
        authRoutes(userRepository, jwtConfig)
        productRoutes(productRepository)
        dashboardRoutes(productRepository, alertRepository)
        scrapingRoutes(productRepository, historyRepository, trackingService, scrapeLogRepository)
        alertRoutes(alertRepository, productRepository)
    }

    // Job periódico: opt-in (SCRAPER_JOB_ENABLED=true) e só com banco configurado.
    if (System.getenv("DB_URL") != null) {
        ScrapingJob.fromEnv(trackingService)?.let { job ->
            job.start()
            environment.monitor.subscribe(ApplicationStopping) { job.stop() }
        }
    }
}

fun Application.configureLogging() {
    install(CallLogging) { level = Level.INFO }
}

fun Application.configureCors() {
    val origins = (System.getenv("CORS_ALLOWED_ORIGINS") ?: "http://localhost:5173")
        .split(",").map { it.trim() }.filter { it.isNotEmpty() }
    install(CORS) {
        origins.forEach { origin ->
            val uri = java.net.URI(origin)
            allowHost(
                uri.authority,
                schemes = listOf(uri.scheme)
            )
        }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowCredentials = true
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) { json() }
}

fun Application.configureDatabase() {
    val url = System.getenv("DB_URL") ?: return
    DatabaseFactory.init(
        url = url,
        user = System.getenv("DB_USER") ?: "postgres",
        password = System.getenv("DB_PASSWORD") ?: "postgres",
    )
}
