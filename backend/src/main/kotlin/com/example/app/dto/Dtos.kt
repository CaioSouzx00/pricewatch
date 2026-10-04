package com.example.app.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.time.Instant

object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
}

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

typealias Money = @Serializable(with = BigDecimalSerializer::class) BigDecimal
typealias Timestamp = @Serializable(with = InstantSerializer::class) Instant

// ---------- Users ----------
@Serializable
data class UserCreateRequest(val email: String, val name: String, val password: String)

@Serializable
data class UserResponse(val id: Long, val email: String, val name: String, val createdAt: Timestamp)

// ---------- Products ----------
@Serializable
data class ProductCreateRequest(
    val name: String,
    val url: String,
    val store: String? = null,
    val imageUrl: String? = null,
    val currency: String = "BRL",
    val currentPrice: Money? = null,
)

@Serializable
data class ProductUpdateRequest(
    val name: String? = null,
    val url: String? = null,
    val imageUrl: String? = null,
    val currency: String? = null,
    val currentPrice: Money? = null,
    val active: Boolean? = null,
)

@Serializable
data class ProductResponse(
    val id: Long,
    val userId: Long,
    val name: String,
    val url: String,
    val store: String?,
    val imageUrl: String?,
    val currency: String,
    val currentPrice: Money?,
    val active: Boolean,
    val isFavorite: Boolean,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val inStock: Boolean? = null,
)

// ---------- Price history ----------
@Serializable
data class PriceHistoryResponse(val id: Long, val productId: Long, val price: Money, val checkedAt: Timestamp)

// ---------- Alerts ----------
@Serializable
enum class AlertType { PRICE_BELOW, PRICE_UP, STOCK_CHANGE, PERCENTAGE_DROP, HISTORICAL_MIN }

@Serializable
data class AlertCreateRequest(
    val productId: Long,
    val type: AlertType = AlertType.PRICE_BELOW,
    val targetPrice: Money? = null,
    val percentageDrop: Money? = null,
)

@Serializable
data class AlertResponse(
    val id: Long,
    val userId: Long,
    val productId: Long,
    val type: AlertType,
    val targetPrice: Money?,
    val percentageDrop: Money?,
    val active: Boolean,
    val triggeredAt: Timestamp?,
    val createdAt: Timestamp,
)
