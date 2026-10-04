package com.example.app.db

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object UsersTable : LongIdTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val name = varchar("name", 120)
    val passwordHash = varchar("password_hash", 255)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

object ProductsTable : LongIdTable("products") {
    val userId = reference("user_id", UsersTable)
    val name = varchar("name", 255)
    val url = text("url")
    val store = varchar("store", 120).nullable()
    val imageUrl = text("image_url").nullable()
    val currency = char("currency", 3).default("BRL")
    val currentPrice = decimal("current_price", 12, 2).nullable()
    val inStock = bool("in_stock").nullable()
    val active = bool("active").default(true)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

object PriceHistoryTable : LongIdTable("price_history") {
    val productId = reference("product_id", ProductsTable)
    val price = decimal("price", 12, 2)
    val checkedAt = timestamp("checked_at").clientDefault { Instant.now() }
}

object AlertsTable : LongIdTable("alerts") {
    val userId = reference("user_id", UsersTable)
    val productId = reference("product_id", ProductsTable)
    val type = varchar("type", 20).default("PRICE_BELOW")
    val targetPrice = decimal("target_price", 12, 2).nullable()
    val active = bool("active").default(true)
    val triggeredAt = timestamp("triggered_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}
