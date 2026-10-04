package com.example.app.alert

import com.example.app.dto.AlertResponse
import com.example.app.dto.AlertType
import com.example.app.dto.ProductResponse
import com.example.app.repository.AlertRepository
import com.example.app.repository.UserRepository
import com.example.app.scraping.ScrapeResult
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.math.BigDecimal

object AlertRules {
    /**
     * [oldPrice]/[oldInStock] são nulos na primeira coleta; nesse caso só `PRICE_BELOW`
     * pode disparar (não há o que comparar nos demais tipos).
     */
    fun matches(
        alert: AlertResponse,
        oldPrice: BigDecimal?,
        newPrice: BigDecimal,
        oldInStock: Boolean?,
        newInStock: Boolean,
    ): Boolean = when (alert.type) {
        AlertType.PRICE_BELOW -> alert.targetPrice?.let { newPrice <= it } ?: false
        AlertType.PRICE_UP -> oldPrice != null && newPrice > oldPrice
        AlertType.STOCK_CHANGE -> oldInStock != null && oldInStock != newInStock
    }

    fun message(alert: AlertResponse, product: ProductResponse, oldPrice: BigDecimal?, result: ScrapeResult): String =
        when (alert.type) {
            AlertType.PRICE_BELOW ->
                "O preço de \"${product.name}\" caiu para ${result.currency} ${result.price.toPlainString()} " +
                    "(alvo: ${alert.targetPrice?.toPlainString()})."
            AlertType.PRICE_UP ->
                "O preço de \"${product.name}\" subiu de ${oldPrice?.toPlainString()} " +
                    "para ${result.currency} ${result.price.toPlainString()}."
            AlertType.STOCK_CHANGE ->
                if (result.inStock) "\"${product.name}\" voltou a ter estoque."
                else "\"${product.name}\" ficou sem estoque."
        }
}

class AlertEvaluator(
    private val alerts: AlertRepository,
    private val users: UserRepository,
    private val notifier: Notifier,
) {
    private val log = LoggerFactory.getLogger(AlertEvaluator::class.java)

    /**
     * Avalia os alertas ativos do produto. [previous] é o estado ANTES da coleta.
     * Falhas de envio não propagam e não marcam o alerta como disparado (nova tentativa na próxima coleta).
     */
    suspend fun evaluate(previous: ProductResponse, result: ScrapeResult) {
        for (alert in alerts.listActiveByProduct(previous.id)) {
            if (!AlertRules.matches(alert, previous.currentPrice, result.price, previous.inStock, result.inStock)) continue
            try {
                val user = users.findById(alert.userId) ?: continue
                notifier.send(
                    AlertNotification(
                        userId = user.id,
                        email = user.email,
                        userName = user.name,
                        alertId = alert.id,
                        alertType = alert.type,
                        productName = previous.name,
                        productUrl = previous.url,
                        currency = result.currency,
                        oldPrice = previous.currentPrice,
                        newPrice = result.price,
                        inStock = result.inStock,
                        message = AlertRules.message(alert, previous, previous.currentPrice, result),
                    ),
                )
                alerts.markTriggered(alert.id, deactivate = alert.type == AlertType.PRICE_BELOW)
                log.info("Alerta {} ({}) disparado para o produto {}", alert.id, alert.type, previous.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Falha ao enviar alerta {}: {}", alert.id, e.message)
            }
        }
    }
}
