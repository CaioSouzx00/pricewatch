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
                )
                call.respond(HttpStatusCode.Created, products.create(call.userId(), sanitized))
            }

            get {
                call.respond(products.listByUser(call.userId()))
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
