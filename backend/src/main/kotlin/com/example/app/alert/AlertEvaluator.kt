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
        AlertType.PERCENTAGE_DROP -> {
            if (oldPrice != null && alert.percentageDrop != null && oldPrice > BigDecimal.ZERO) {
                val drop = (oldPrice - newPrice) / oldPrice * BigDecimal(100)
                drop >= alert.percentageDrop
            } else false
        }
        AlertType.HISTORICAL_MIN -> {
            // Evaluated outside this function if we need history, or we can pass minPrice here
            false // We'll handle this directly in evaluate()
        }
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
            AlertType.PERCENTAGE_DROP ->
                "O preço de \"${product.name}\" caiu ${alert.percentageDrop?.toPlainString()}% (de ${oldPrice?.toPlainString()} para ${result.currency} ${result.price.toPlainString()})."
            AlertType.HISTORICAL_MIN ->
                "O preço de \"${product.name}\" atingiu o mínimo histórico de ${result.currency} ${result.price.toPlainString()}!"
        }
}

class AlertEvaluator(
    private val alerts: AlertRepository,
    private val users: UserRepository,
    private val history: com.example.app.repository.PriceHistoryRepository,
    private val notifier: Notifier,
) {
    private val log = LoggerFactory.getLogger(AlertEvaluator::class.java)

    /**
     * Avalia os alertas ativos do produto. [previous] é o estado ANTES da coleta.
     * Falhas de envio não propagam e não marcam o alerta como disparado (nova tentativa na próxima coleta).
     */
    suspend fun evaluate(previous: ProductResponse, result: ScrapeResult) {
        val historyList = history.listByProduct(previous.id)
        val minPrice = historyList.minByOrNull { it.price }?.price

        for (alert in alerts.listActiveByProduct(previous.id)) {
            val matches = if (alert.type == AlertType.HISTORICAL_MIN) {
                minPrice != null && result.price <= minPrice && (previous.currentPrice == null || result.price < previous.currentPrice)
            } else {
                AlertRules.matches(alert, previous.currentPrice, result.price, previous.inStock, result.inStock)
            }
            if (!matches) continue
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
                alerts.markTriggered(alert.id, deactivate = alert.type == AlertType.PRICE_BELOW || alert.type == AlertType.PERCENTAGE_DROP || alert.type == AlertType.HISTORICAL_MIN)
                log.info("Alerta {} ({}) disparado para o produto {}", alert.id, alert.type, previous.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("Falha ao enviar alerta {}: {}", alert.id, e.message)
            }
        }
    }
}
