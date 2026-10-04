package com.example.app.alert

import com.example.app.auth.ApiException
import com.example.app.auth.JWT_AUTH
import com.example.app.auth.userId
import com.example.app.dto.AlertCreateRequest
import com.example.app.dto.AlertResponse
import com.example.app.dto.AlertType
import com.example.app.repository.AlertRepository
import com.example.app.repository.ProductRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private fun badRequest(message: String) = ApiException(HttpStatusCode.BadRequest, message)

private fun notFound(message: String) = ApiException(HttpStatusCode.NotFound, message)

fun Route.alertRoutes(alerts: AlertRepository, products: ProductRepository) {
    authenticate(JWT_AUTH) {
        route("/alerts") {
            post {
                val request = call.receive<AlertCreateRequest>()
                products.findById(request.productId)?.takeIf { it.userId == call.userId() }
                    ?: throw notFound("Produto não encontrado")

                val sanitized = when (request.type) {
                    AlertType.PRICE_BELOW -> {
                        val target = request.targetPrice ?: throw badRequest("targetPrice é obrigatório para PRICE_BELOW")
                        if (target.signum() <= 0 || target.precision() - target.scale() > 10) {
                            throw badRequest("Preço alvo inválido")
                        }
                        request.copy(percentageDrop = null)
                    }
                    AlertType.PERCENTAGE_DROP -> {
                        val percentage = request.percentageDrop ?: throw badRequest("percentageDrop é obrigatório para PERCENTAGE_DROP")
                        if (percentage.signum() <= 0 || percentage > java.math.BigDecimal(100)) {
                            throw badRequest("Porcentagem de queda inválida")
                        }
                        request.copy(targetPrice = null)
                    }
                    else -> request.copy(targetPrice = null, percentageDrop = null)
                }
                call.respond(HttpStatusCode.Created, alerts.create(call.userId(), sanitized))
            }

            get {
                call.respond<List<AlertResponse>>(alerts.listByUser(call.userId()))
            }

            delete("/{id}") {
                val id = call.parameters["id"]?.toLongOrNull() ?: throw badRequest("ID inválido")
                val alert = alerts.findById(id)?.takeIf { it.userId == call.userId() }
                    ?: throw notFound("Alerta não encontrado")
                alerts.delete(alert.id)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
