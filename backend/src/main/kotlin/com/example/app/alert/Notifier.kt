package com.example.app.alert

import com.example.app.dto.AlertType
import java.math.BigDecimal

data class AlertNotification(
    val userId: Long,
    val email: String,
    val userName: String,
    val alertId: Long,
    val alertType: AlertType,
    val productName: String,
    val productUrl: String,
    val currency: String,
    val oldPrice: BigDecimal?,
    val newPrice: BigDecimal,
    val inStock: Boolean,
    val message: String,
)

interface Notifier {
    /** Lança exceção se o envio falhar. */
    suspend fun send(notification: AlertNotification)
}
