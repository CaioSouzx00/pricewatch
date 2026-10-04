package com.example.app.repository

import com.example.app.db.*
import com.example.app.dto.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant

fun UserEntity.toDto() = UserResponse(id.value, email, name, createdAt)

fun ProductEntity.toDto() = ProductResponse(
    id.value, user.id.value, name, url, store, imageUrl, currency,
    currentPrice, active, createdAt, updatedAt, inStock,
)

fun PriceHistoryEntity.toDto() = PriceHistoryResponse(id.value, product.id.value, price, checkedAt)

fun AlertEntity.toDto() = AlertResponse(
    id.value, user.id.value, product.id.value, AlertType.valueOf(type), targetPrice, active, triggeredAt, createdAt,
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
        ProductEntity.new {
            user = UserEntity[userId]
            name = request.name
            url = request.url
            store = request.store
            imageUrl = request.imageUrl
            currency = request.currency.uppercase()
            currentPrice = request.currentPrice
        }.toDto()
    }

    fun findById(id: Long): ProductResponse? = transaction { ProductEntity.findById(id)?.toDto() }

    fun listByUser(userId: Long): List<ProductResponse> = transaction {
        ProductEntity.find { ProductsTable.userId eq EntityID(userId, UsersTable) }
            .orderBy(ProductsTable.createdAt to SortOrder.DESC)
            .map { it.toDto() }
    }

    fun listActive(): List<ProductResponse> = transaction {
        ProductEntity.find { ProductsTable.active eq true }.map { it.toDto() }
    }

    fun updatePrice(id: Long, price: BigDecimal, inStock: Boolean? = null): ProductResponse? = transaction {
        ProductEntity.findById(id)?.apply {
            currentPrice = price
            inStock?.let { this.inStock = it }
            updatedAt = Instant.now()
        }?.toDto()
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
            updatedAt = Instant.now()
        }?.toDto()
    }

    fun delete(id: Long): Boolean = transaction { ProductEntity.findById(id)?.delete() != null }
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
    fun create(userId: Long, request: AlertCreateRequest): AlertResponse = transaction {
        AlertEntity.new {
            user = UserEntity[userId]
            product = ProductEntity[request.productId]
            type = request.type.name
            targetPrice = request.targetPrice
        }.toDto()
    }

    fun findById(id: Long): AlertResponse? = transaction { AlertEntity.findById(id)?.toDto() }

    fun listByUser(userId: Long): List<AlertResponse> = transaction {
        AlertEntity.find { AlertsTable.userId eq EntityID(userId, UsersTable) }
            .orderBy(AlertsTable.createdAt to SortOrder.DESC)
            .map { it.toDto() }
    }

    fun listActiveByProduct(productId: Long): List<AlertResponse> = transaction {
        AlertEntity.find {
            (AlertsTable.productId eq EntityID(productId, ProductsTable)) and (AlertsTable.active eq true)
        }.map { it.toDto() }
    }

    /** Alertas de disparo único (preço abaixo de X) são desativados; os demais só registram o horário. */
    fun markTriggered(id: Long, deactivate: Boolean = true): AlertResponse? = transaction {
        AlertEntity.findById(id)?.apply {
            if (deactivate) active = false
            triggeredAt = Instant.now()
        }?.toDto()
    }

    fun delete(id: Long): Boolean = transaction { AlertEntity.findById(id)?.delete() != null }
}
