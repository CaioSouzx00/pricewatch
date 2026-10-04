package com.example.app.scraping

import com.example.app.auth.ApiException
import com.example.app.auth.JWT_AUTH
import com.example.app.auth.userId
import com.example.app.dto.PriceHistoryResponse
import com.example.app.dto.ProductResponse
import com.example.app.product.ProductAnalytics
import com.example.app.repository.PriceHistoryRepository
import com.example.app.repository.ProductRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.scrapingRoutes(
    products: ProductRepository,
    history: PriceHistoryRepository,
    service: PriceTrackingService,
    scrapeLogs: com.example.app.repository.ScrapeLogRepository,
) {
    authenticate(JWT_AUTH) {
        fun ApplicationCall.ownedProduct(): ProductResponse {
            val id = parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "ID inválido")
            return products.findById(id)?.takeIf { it.userId == userId() }
                ?: throw ApiException(HttpStatusCode.NotFound, "Produto não encontrado")
        }

        post("/products/{id}/refresh") {
            val product = call.ownedProduct()
            try {
                service.refresh(product)
            } catch (e: ScrapeException.Blocked) {
                throw ApiException(HttpStatusCode.TooManyRequests, "Loja limitou as requisições; tente mais tarde")
            } catch (e: ScrapeException.Network) {
                throw ApiException(HttpStatusCode.BadGateway, "Não foi possível acessar a loja")
            } catch (e: ScrapeException.Parse) {
                throw ApiException(HttpStatusCode.UnprocessableEntity, e.message ?: "Não foi possível ler a página")
            }
            call.respond(products.findById(product.id) ?: product)
        }

        get("/products/{id}/history") {
            val product = call.ownedProduct()
            call.respond<List<PriceHistoryResponse>>(history.listByProduct(product.id))
        }

        get("/products/{id}/analytics") {
            val product = call.ownedProduct()
            val historyList = history.listByProduct(product.id)
            call.respond(ProductAnalytics.calculate(historyList, product.currentPrice))
        }

        get("/health") {
            call.respond(scrapeLogs.getStats())
        }
    }
}
