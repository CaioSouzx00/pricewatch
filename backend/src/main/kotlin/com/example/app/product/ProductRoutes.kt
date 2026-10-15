package com.example.app.product

import com.example.app.auth.ApiException
import com.example.app.auth.JWT_AUTH
import com.example.app.auth.userId
import com.example.app.dto.ProductCreateRequest
import com.example.app.dto.ProductResponse
import com.example.app.dto.ProductUpdateRequest
import com.example.app.repository.ProductRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.math.BigDecimal

private fun badRequest(message: String) = ApiException(HttpStatusCode.BadRequest, message)

private fun validateName(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || trimmed.length > 255) throw badRequest("Nome inválido")
    return trimmed
}

private fun validateCurrency(currency: String): String {
    if (!Regex("^[A-Za-z]{3}$").matches(currency)) throw badRequest("Moeda inválida")
    return currency.uppercase()
}

private fun validatePrice(price: BigDecimal): BigDecimal {
    if (price.signum() < 0 || price.precision() - price.scale() > 10) throw badRequest("Preço inválido")
    return price
}

private fun validateCheckInterval(minutes: Int): Int {
    if (minutes < 15 || minutes > 1440) throw badRequest("Intervalo de checagem deve estar entre 15 e 1440 minutos")
    return minutes
}

/** Produto pertencente ao usuário autenticado; 404 também para produtos de terceiros. */
private fun ProductRepository.findOwned(call: ApplicationCall): ProductResponse {
    val id = call.parameters["id"]?.toLongOrNull() ?: throw badRequest("ID inválido")
    return findById(id)?.takeIf { it.userId == call.userId() }
        ?: throw ApiException(HttpStatusCode.NotFound, "Produto não encontrado")
}

fun Route.productRoutes(products: ProductRepository) {
    authenticate(JWT_AUTH) {
        route("/products") {
            post {
                val request = call.receive<ProductCreateRequest>()
                val url = UrlValidator.validate(request.url)
                val sanitized = request.copy(
                    name = validateName(request.name),
                    url = url,
                    store = PlatformDetector.detect(url),
                    currency = validateCurrency(request.currency),
                    currentPrice = request.currentPrice?.let(::validatePrice),
                    checkIntervalMinutes = request.checkIntervalMinutes?.let(::validateCheckInterval),
                )
                call.respond(HttpStatusCode.Created, products.create(call.userId(), sanitized))
            }

            get {
                val store = call.request.queryParameters["store"]?.trim()?.takeIf { it.isNotEmpty() }
                val inStock = call.request.queryParameters["inStock"]?.toBooleanStrictOrNull()
                val tracking = call.request.queryParameters["tracking"]?.trim()?.takeIf { it.isNotEmpty() }
                val favorite = call.request.queryParameters["favorite"]?.toBooleanStrictOrNull()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100)
                val page = call.request.queryParameters["page"]?.toLongOrNull()?.coerceAtLeast(0)
                val offset = if (limit != null && page != null) page * limit else null

                call.respond(products.listByUser(
                    userId = call.userId(),
                    store = store,
                    inStock = inStock,
                    tracking = tracking,
                    favorite = favorite,
                    limit = limit,
                    offset = offset,
                ))
            }

            get("/{id}") {
                call.respond(products.findOwned(call))
            }

            put("/{id}") {
                val existing = products.findOwned(call)
                val request = call.receive<ProductUpdateRequest>()
                val url = request.url?.let(UrlValidator::validate)
                val sanitized = request.copy(
                    name = request.name?.let(::validateName),
                    url = url,
                    currency = request.currency?.let(::validateCurrency),
                    currentPrice = request.currentPrice?.let(::validatePrice),
                    checkIntervalMinutes = request.checkIntervalMinutes?.let(::validateCheckInterval),
                )
                val updated = products.update(existing.id, sanitized, store = url?.let(PlatformDetector::detect))
                    ?: throw ApiException(HttpStatusCode.NotFound, "Produto não encontrado")
                call.respond(updated)
            }

            delete("/{id}") {
                val existing = products.findOwned(call)
                products.delete(existing.id)
                call.respond(HttpStatusCode.NoContent)
            }

            put("/{id}/favorite") {
                val existing = products.findOwned(call)
                val updated = products.toggleFavorite(existing.id)
                    ?: throw ApiException(HttpStatusCode.NotFound, "Produto não encontrado")
                call.respond(updated)
            }
        }
    }
}
