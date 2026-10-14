package com.example.app.scraping

import com.example.app.auth.ApiException
import com.example.app.auth.JWT_AUTH
import com.example.app.auth.userId
import com.example.app.dto.PriceHistoryResponse
import com.example.app.dto.ProductResponse
import com.example.app.product.ProductAnalytics
import com.example.app.repository.PriceHistoryRepository
import com.example.app.repository.ProductRepository
import com.example.app.repository.ScrapeLogRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private fun escapeCsv(value: String): String {
    return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }
}

fun Route.scrapingRoutes(
    products: ProductRepository,
    history: PriceHistoryRepository,
    service: PriceTrackingService,
    scrapeLogs: ScrapeLogRepository,
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

        get("/products/{id}/history/export") {
            val product = call.ownedProduct()
            val format = call.request.queryParameters["format"]?.lowercase()?.trim()
                ?: throw ApiException(HttpStatusCode.BadRequest, "Parâmetro 'format' é obrigatório (use 'csv' ou 'json')")

            val historyList = history.listByProduct(product.id, limit = 1000)

            when (format) {
                "csv" -> {
                    val csv = buildString {
                        appendLine("id,productId,price,checkedAt")
                        for (item in historyList) {
                            append(item.id).append(",")
                            append(item.productId).append(",")
                            append(escapeCsv(item.price.toPlainString())).append(",")
                            appendLine(escapeCsv(item.checkedAt.toString()))
                        }
                    }
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "price-history-${product.id}.csv").toString()
                    )
                    call.respondText(csv, ContentType.Text.CSV.withCharset(Charsets.UTF_8))
                }
                "json" -> {
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "price-history-${product.id}.json").toString()
                    )
                    call.respond(historyList)
                }
                else -> throw ApiException(HttpStatusCode.BadRequest, "Formato inválido: '$format'. Use 'csv' ou 'json'")
            }
        }

        get("/products/{id}/analytics") {
            val product = call.ownedProduct()
            val historyList = history.listByProduct(product.id)
            call.respond(ProductAnalytics.calculate(historyList, product.currentPrice))
        }

        get("/scraper/stats") {
            val store = call.request.queryParameters["store"]?.trim()?.takeIf { it.isNotEmpty() }
            val window = call.request.queryParameters["window"]?.trim() ?: "24h"
            call.respond(scrapeLogs.getStats(store = store, window = window))
        }
    }
}
