package com.example.app.repository

import com.example.app.auth.ApiException
import com.example.app.db.*
import com.example.app.dto.*
import io.ktor.http.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant

fun UserEntity.toDto() = UserResponse(id.value, email, name, createdAt)

fun ProductEntity.toDto() = ProductResponse(
    id = id.value,
    userId = user.id.value,
    name = name,
    url = url,
    store = store,
    imageUrl = imageUrl,
    currency = currency,
    currentPrice = currentPrice,
    active = active,
    isFavorite = isFavorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
    inStock = inStock,
    checkIntervalMinutes = checkIntervalMinutes,
    lastCheckedAt = lastCheckedAt,
)

fun PriceHistoryEntity.toDto() = PriceHistoryResponse(id.value, product.id.value, price, checkedAt)

fun AlertEntity.toDto(productName: String? = null, productImageUrl: String? = null) = AlertResponse(
    id = id.value,
    userId = user.id.value,
    productId = product.id.value,
    productName = productName ?: runCatching { product.name }.getOrDefault(""),
    productImageUrl = productImageUrl ?: runCatching { product.imageUrl }.getOrNull(),
    type = AlertType.valueOf(type),
    targetPrice = targetPrice,
    percentageDrop = percentageDrop,
    active = active,
    triggeredAt = triggeredAt,
    createdAt = createdAt,
)

class UserRepository {
    fun create(request: UserCreateRequest, passwordHash: String): UserResponse = transaction {
        UserEntity.new {
            email = request.email.trim().lowercase()
            name = request.name
            this.passwordHash = passwordHash
        }.toDto()
    }

    fun findById(id: Long): UserResponse? = transaction { UserEntity.findById(id)?.toDto() }

    fun findByEmail(email: String): UserResponse? = transaction {
        UserEntity.find { UsersTable.email eq email.trim().lowercase() }.firstOrNull()?.toDto()
    }

    fun findPasswordHash(email: String): String? = transaction {
        UserEntity.find { UsersTable.email eq email.trim().lowercase() }.firstOrNull()?.passwordHash
    }

    fun delete(id: Long): Boolean = transaction { UserEntity.findById(id)?.delete() != null }
}

class ProductRepository {
    fun create(userId: Long, request: ProductCreateRequest): ProductResponse = transaction {
        val interval = request.checkIntervalMinutes ?: 60
        if (interval < 15 || interval > 1440) {
            throw ApiException(HttpStatusCode.BadRequest, "Intervalo de coleta deve estar entre 15 e 1440 minutos")
        }
        ProductEntity.new {
            user = UserEntity[userId]
            name = request.name
            url = request.url
            store = request.store
            imageUrl = request.imageUrl
            currency = request.currency.uppercase()
            currentPrice = request.currentPrice
            checkIntervalMinutes = interval
        }.toDto()
    }

    fun findById(id: Long): ProductResponse? = transaction { ProductEntity.findById(id)?.toDto() }

    fun listByUser(
        userId: Long,
        store: String? = null,
        inStock: Boolean? = null,
        tracking: String? = null,
        favorite: Boolean? = null,
    ): List<ProductResponse> = transaction {
        var op: Op<Boolean> = ProductsTable.userId eq EntityID(userId, UsersTable)
        if (!store.isNullOrBlank()) {
            op = op and (ProductsTable.store eq store)
        }
        if (inStock != null) {
            op = op and (ProductsTable.inStock eq inStock)
        }
        if (!tracking.isNullOrBlank()) {
            when (tracking.lowercase().trim()) {
                "active" -> op = op and (ProductsTable.active eq true)
                "paused" -> op = op and (ProductsTable.active eq false)
            }
        }
        if (favorite != null) {
            op = op and (ProductsTable.isFavorite eq favorite)
        }
        ProductEntity.find { op }
            .orderBy(ProductsTable.createdAt to SortOrder.DESC)
            .map { it.toDto() }
    }

    fun listActive(): List<ProductResponse> = transaction {
        ProductEntity.find { ProductsTable.active eq true }.map { it.toDto() }
    }

    fun listDueForCheck(now: Instant = Instant.now()): List<ProductResponse> = transaction {
        ProductEntity.find { ProductsTable.active eq true }
            .filter { p ->
                val last = p.lastCheckedAt
                if (last == null) true
                else {
                    val nextCheck = last.plus(java.time.Duration.ofMinutes(p.checkIntervalMinutes.toLong()))
                    !now.isBefore(nextCheck)
                }
            }
            .map { it.toDto() }
    }

    fun updatePrice(id: Long, price: BigDecimal, inStock: Boolean? = null): ProductResponse? = transaction {
        ProductEntity.findById(id)?.apply {
            currentPrice = price
            inStock?.let { this.inStock = it }
            lastCheckedAt = Instant.now()
            updatedAt = Instant.now()
        }?.toDto()
    }

    fun updateScrapedData(id: Long, price: BigDecimal, inStock: Boolean?, imageUrl: String?): ProductResponse? = transaction {
        ProductEntity.findById(id)?.apply {
            currentPrice = price
            inStock?.let { this.inStock = it }
            if (!imageUrl.isNullOrBlank() && (imageUrl.startsWith("http://", ignoreCase = true) || imageUrl.startsWith("https://", ignoreCase = true))) {
                this.imageUrl = imageUrl
            }
            lastCheckedAt = Instant.now()
            updatedAt = Instant.now()
        }?.toDto()
    }

    fun markChecked(id: Long): Unit = transaction {
        ProductEntity.findById(id)?.apply {
            lastCheckedAt = Instant.now()
        }
    }

    fun update(id: Long, request: ProductUpdateRequest, store: String?): ProductResponse? = transaction {
        ProductEntity.findById(id)?.apply {
            request.name?.let { name = it }
            request.url?.let { url = it }
            store?.let { this.store = it }
            request.imageUrl?.let { imageUrl = it }
            request.currency?.let { currency = it.uppercase() }
            request.currentPrice?.let { currentPrice = it }
            request.active?.let { active = it }
            request.checkIntervalMinutes?.let {
                if (it < 15 || it > 1440) {
                    throw ApiException(HttpStatusCode.BadRequest, "Intervalo de coleta deve estar entre 15 e 1440 minutos")
                }
                checkIntervalMinutes = it
            }
            updatedAt = Instant.now()
        }?.toDto()
    }

    fun delete(id: Long): Boolean = transaction { ProductEntity.findById(id)?.delete() != null }

    fun toggleFavorite(id: Long): ProductResponse? = transaction {
        ProductEntity.findById(id)?.apply {
            isFavorite = !isFavorite
            updatedAt = Instant.now()
        }?.toDto()
    }
}

class PriceHistoryRepository {
    fun add(productId: Long, price: BigDecimal): PriceHistoryResponse = transaction {
        PriceHistoryEntity.new {
            product = ProductEntity[productId]
            this.price = price
        }.toDto()
    }

    fun listByProduct(productId: Long, limit: Int = 100): List<PriceHistoryResponse> = transaction {
        PriceHistoryEntity.find { PriceHistoryTable.productId eq EntityID(productId, ProductsTable) }
            .orderBy(PriceHistoryTable.checkedAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toDto() }
    }
}

class AlertRepository {
    private fun mapAlertRow(row: ResultRow): AlertResponse = AlertResponse(
        id = row[AlertsTable.id].value,
        userId = row[AlertsTable.userId].value,
        productId = row[AlertsTable.productId].value,
        productName = row[ProductsTable.name],
        productImageUrl = row[ProductsTable.imageUrl],
        type = AlertType.valueOf(row[AlertsTable.type]),
        targetPrice = row[AlertsTable.targetPrice],
        percentageDrop = row[AlertsTable.percentageDrop],
        active = row[AlertsTable.active],
        triggeredAt = row[AlertsTable.triggeredAt],
        createdAt = row[AlertsTable.createdAt],
    )

    fun create(userId: Long, request: AlertCreateRequest): AlertResponse = transaction {
        val created = AlertEntity.new {
            user = UserEntity[userId]
            product = ProductEntity[request.productId]
            type = request.type.name
            targetPrice = request.targetPrice
            percentageDrop = request.percentageDrop
        }
        findById(created.id.value) ?: created.toDto()
    }

    fun findById(id: Long): AlertResponse? = transaction {
        (AlertsTable innerJoin ProductsTable)
            .selectAll()
            .where { AlertsTable.id eq id }
            .singleOrNull()
            ?.let(::mapAlertRow)
    }

    fun listByUser(userId: Long): List<AlertResponse> = transaction {
        (AlertsTable innerJoin ProductsTable)
            .selectAll()
            .where { AlertsTable.userId eq EntityID(userId, UsersTable) }
            .orderBy(AlertsTable.createdAt to SortOrder.DESC)
            .map(::mapAlertRow)
    }

    fun listActiveByProduct(productId: Long): List<AlertResponse> = transaction {
        (AlertsTable innerJoin ProductsTable)
            .selectAll()
            .where { (AlertsTable.productId eq EntityID(productId, ProductsTable)) and (AlertsTable.active eq true) }
            .map(::mapAlertRow)
    }

    /** Alertas de disparo único (preço abaixo de X) são desativados; os demais só registram o horário. */
    fun markTriggered(id: Long, deactivate: Boolean = true): AlertResponse? = transaction {
        AlertEntity.findById(id)?.apply {
            if (deactivate) active = false
            triggeredAt = Instant.now()
        }
        findById(id)
    }

    fun update(id: Long, request: AlertUpdateRequest): AlertResponse? = transaction {
        AlertEntity.findById(id)?.apply {
            request.type?.let { this.type = it.name }
            request.targetPrice?.let { this.targetPrice = it }
            request.percentageDrop?.let { this.percentageDrop = it }
        }
        findById(id)
    }

    fun toggle(id: Long): AlertResponse? = transaction {
        AlertEntity.findById(id)?.apply {
            val nextActive = !active
            active = nextActive
            if (nextActive) {
                // Reativar alerta disparado zera estado disparado para voltar a ser avaliado
                triggeredAt = null
            }
        }
        findById(id)
    }

    fun delete(id: Long): Boolean = transaction { AlertEntity.findById(id)?.delete() != null }
}

class ScrapeLogRepository {
    fun add(
        productId: Long,
        success: Boolean,
        errorMessage: String?,
        latencyMs: Int? = null,
        store: String? = null,
        blocked: Boolean = false,
    ) = transaction {
        ScrapeLogEntity.new {
            this.product = ProductEntity[productId]
            this.store = store
            this.success = success
            this.blocked = blocked
            this.errorMessage = errorMessage
            this.latencyMs = latencyMs
        }
    }

    fun getStats(store: String? = null, window: String = "24h"): ScraperStatsResponse = transaction {
        val windowDuration = when (window.lowercase().trim()) {
            "7d" -> java.time.Duration.ofDays(7)
            else -> java.time.Duration.ofHours(24)
        }
        val since = Instant.now().minus(windowDuration)

        var op: Op<Boolean> = ScrapeLogsTable.createdAt greaterEq since
        if (!store.isNullOrBlank()) {
            op = op and ((ScrapeLogsTable.store eq store) or (ProductsTable.store eq store))
        }

        val rows = (ScrapeLogsTable innerJoin ProductsTable)
            .selectAll()
            .where { op }
            .orderBy(ScrapeLogsTable.createdAt to SortOrder.DESC)
            .toList()

        val total = rows.size.toLong()
        val successCount = rows.count { it[ScrapeLogsTable.success] }.toLong()
        val failedCount = total - successCount
        val blockedCount = rows.count { it[ScrapeLogsTable.blocked] }.toLong()
        val successRate = if (total > 0) (successCount.toDouble() / total) * 100.0 else 0.0

        val successfulLatencies = rows.filter { it[ScrapeLogsTable.success] }
            .mapNotNull { it[ScrapeLogsTable.latencyMs] }
            .sorted()

        val averageLatencyMs = if (successfulLatencies.isNotEmpty()) successfulLatencies.average() else 0.0
        val p95LatencyMs = if (successfulLatencies.isNotEmpty()) {
            val idx = ((successfulLatencies.size - 1) * 0.95).toInt()
            successfulLatencies[idx].toDouble()
        } else 0.0

        val recentFailures = rows.filter { !it[ScrapeLogsTable.success] }
            .take(15)
            .map { r ->
                ScrapeFailureItem(
                    productId = r[ScrapeLogsTable.productId].value,
                    store = r[ScrapeLogsTable.store] ?: r[ProductsTable.store],
                    errorMessage = r[ScrapeLogsTable.errorMessage],
                    latencyMs = r[ScrapeLogsTable.latencyMs],
                    createdAt = r[ScrapeLogsTable.createdAt],
                    blocked = r[ScrapeLogsTable.blocked],
                )
            }

        val byStore = rows.groupBy { it[ScrapeLogsTable.store] ?: it[ProductsTable.store] ?: "Desconhecida" }
            .mapValues { (_, storeRows) ->
                val sTotal = storeRows.size.toLong()
                val sSuccess = storeRows.count { it[ScrapeLogsTable.success] }.toLong()
                val sFailed = sTotal - sSuccess
                val sBlocked = storeRows.count { it[ScrapeLogsTable.blocked] }.toLong()
                val sRate = if (sTotal > 0) (sSuccess.toDouble() / sTotal) * 100.0 else 0.0
                val sLatencies = storeRows.filter { it[ScrapeLogsTable.success] }
                    .mapNotNull { it[ScrapeLogsTable.latencyMs] }
                    .sorted()
                val sAvg = if (sLatencies.isNotEmpty()) sLatencies.average() else 0.0
                val sP95 = if (sLatencies.isNotEmpty()) {
                    val idx = ((sLatencies.size - 1) * 0.95).toInt()
                    sLatencies[idx].toDouble()
                } else 0.0

                StoreScrapeStats(
                    total = sTotal,
                    successCount = sSuccess,
                    failedCount = sFailed,
                    blockedCount = sBlocked,
                    successRate = sRate,
                    averageLatencyMs = sAvg,
                    p95LatencyMs = sP95,
                )
            }

        ScraperStatsResponse(
            total = total,
            successCount = successCount,
            failedCount = failedCount,
            blockedCount = blockedCount,
            successRate = successRate,
            averageLatencyMs = averageLatencyMs,
            p95LatencyMs = p95LatencyMs,
            recentFailures = recentFailures,
            byStore = byStore,
        )
    }
}
