package com.example.app.product

import com.example.app.auth.JWT_AUTH
import com.example.app.auth.userId
import com.example.app.repository.AlertRepository
import com.example.app.repository.ProductRepository
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class DashboardMetrics(
    val totalProducts: Int,
    val activeAlerts: Int,
    val recentPriceDrops: Int,
    val staleProducts: Int,
    val totalValueTracked: String
)

fun Route.dashboardRoutes(products: ProductRepository, alerts: AlertRepository) {
    authenticate(JWT_AUTH) {
        get("/dashboard/metrics") {
            val userId = call.userId()
            val userProducts = products.listByUser(userId)
            val userAlerts = alerts.listByUser(userId)
            
            val totalProducts = userProducts.size
            val activeAlerts = userAlerts.count { it.active }
            
            // Just some basic metrics derived from current data
            val recentPriceDrops = userAlerts.count { it.triggeredAt != null && java.time.Instant.now().toEpochMilli() - it.triggeredAt.toEpochMilli() < 7 * 24 * 60 * 60 * 1000 }
            
            // Stale: updated more than 48h ago
            val staleProducts = userProducts.count { java.time.Instant.now().toEpochMilli() - it.updatedAt.toEpochMilli() > 48 * 60 * 60 * 1000 }
            
            val totalValue = userProducts.mapNotNull { it.currentPrice }.fold(java.math.BigDecimal.ZERO) { acc, bd -> acc.add(bd) }

            call.respond(DashboardMetrics(
                totalProducts = totalProducts,
                activeAlerts = activeAlerts,
                recentPriceDrops = recentPriceDrops,
                staleProducts = staleProducts,
                totalValueTracked = totalValue.toPlainString()
            ))
        }
    }
}
