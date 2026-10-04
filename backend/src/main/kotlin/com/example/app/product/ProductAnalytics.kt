@file:UseSerializers(com.example.app.dto.BigDecimalSerializer::class)
package com.example.app.product

import com.example.app.dto.PriceHistoryResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.sqrt

@Serializable
data class ProductAnalyticsResponse(
    val minPrice: BigDecimal?,
    val maxPrice: BigDecimal?,
    val averagePrice: BigDecimal?,
    val percentageChange: BigDecimal?,
    val volatility: BigDecimal?,
    val daysSinceLowest: Int?
)

object ProductAnalytics {
    fun calculate(history: List<PriceHistoryResponse>, currentPrice: BigDecimal?): ProductAnalyticsResponse {
        if (history.isEmpty() || currentPrice == null) {
            return ProductAnalyticsResponse(null, null, null, null, null, null)
        }

        val prices = history.map { it.price }
        val minPrice = prices.minOrNull()
        val maxPrice = prices.maxOrNull()
        
        val sum = prices.fold(BigDecimal.ZERO) { acc, p -> acc.add(p) }
        val avgPrice = if (prices.isNotEmpty()) sum.divide(BigDecimal(prices.size), 2, RoundingMode.HALF_UP) else null

        val percentageChange = if (avgPrice != null && avgPrice.compareTo(BigDecimal.ZERO) != 0) {
            val change = currentPrice.subtract(avgPrice)
            change.divide(avgPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100)).setScale(2, RoundingMode.HALF_UP)
        } else null

        // Calculate volatility (standard deviation)
        val volatility = if (avgPrice != null && prices.size > 1) {
            val variance = prices.map { p -> 
                val diff = p.subtract(avgPrice).toDouble()
                diff * diff
            }.average()
            BigDecimal(sqrt(variance)).setScale(2, RoundingMode.HALF_UP)
        } else null

        val lowestRecord = history.minByOrNull { it.price }
        val daysSinceLowest = lowestRecord?.let {
            val diffMs = java.time.Instant.now().toEpochMilli() - it.checkedAt.toEpochMilli()
            (diffMs / (1000 * 60 * 60 * 24)).toInt()
        }

        return ProductAnalyticsResponse(
            minPrice = minPrice,
            maxPrice = maxPrice,
            averagePrice = avgPrice,
            percentageChange = percentageChange,
            volatility = volatility,
            daysSinceLowest = daysSinceLowest
        )
    }
}
