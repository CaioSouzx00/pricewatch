package com.example.app.db

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class UserEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<UserEntity>(UsersTable)

    var email by UsersTable.email
    var name by UsersTable.name
    var passwordHash by UsersTable.passwordHash
    var createdAt by UsersTable.createdAt
}

class ProductEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ProductEntity>(ProductsTable)

    var user by UserEntity referencedOn ProductsTable.userId
    var name by ProductsTable.name
    var url by ProductsTable.url
    var store by ProductsTable.store
    var imageUrl by ProductsTable.imageUrl
    var currency by ProductsTable.currency
    var currentPrice by ProductsTable.currentPrice
    var inStock by ProductsTable.inStock
    var active by ProductsTable.active
    var isFavorite by ProductsTable.isFavorite
    var createdAt by ProductsTable.createdAt
    var updatedAt by ProductsTable.updatedAt
}

class PriceHistoryEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PriceHistoryEntity>(PriceHistoryTable)

    var product by ProductEntity referencedOn PriceHistoryTable.productId
    var price by PriceHistoryTable.price
    var checkedAt by PriceHistoryTable.checkedAt
}

class AlertEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AlertEntity>(AlertsTable)

    var user by UserEntity referencedOn AlertsTable.userId
    var product by ProductEntity referencedOn AlertsTable.productId
    var type by AlertsTable.type
    var targetPrice by AlertsTable.targetPrice
    var percentageDrop by AlertsTable.percentageDrop
    var active by AlertsTable.active
    var triggeredAt by AlertsTable.triggeredAt
    var createdAt by AlertsTable.createdAt
}

class ScrapeLogEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ScrapeLogEntity>(ScrapeLogsTable)

    var product by ProductEntity referencedOn ScrapeLogsTable.productId
    var success by ScrapeLogsTable.success
    var errorMessage by ScrapeLogsTable.errorMessage
    var latencyMs by ScrapeLogsTable.latencyMs
    var createdAt by ScrapeLogsTable.createdAt
}
