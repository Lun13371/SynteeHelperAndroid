package ru.syntee.helper.core.network.dto

import kotlinx.serialization.Serializable

// ── Auth ──────────────────────────────────────────────────────────────────

@Serializable
data class TokenResponse(
    val access_token: String,
    val refresh_token: String,
    val token_type: String = "bearer",
)

@Serializable
data class ExchangeRequest(val code: String)

/**
 * ⚠️ Refresh-токен передаётся в JSON body (НЕ query-param!) и ОДНОРАЗОВЫЙ:
 * gateway атомарно блэклистит его (Redis SET NX); гонка → 401 "already used".
 */
@Serializable
data class RefreshRequest(val refresh: String)

@Serializable
data class GatewayUserDto(
    val id: String,
    val email: String,
    val username: String? = null,
    val username_is_auto: Boolean? = null,
    val role: String? = null,
    val display_name: String? = null,
    val avatar_url: String? = null,
)

// ── Каталог моделей ───────────────────────────────────────────────────────

@Serializable
data class ModelsResponseDto(
    val data: List<GatewayModelDto> = emptyList(),
)

@Serializable
data class GatewayModelDto(
    val id: String,
    val display_name: String? = null,
    val owned_by: String? = null,
    val supports_streaming: Boolean? = null,
    val supports_tools: Boolean? = null,
    val supports_vision: Boolean? = null,
    val max_tokens: Long? = null,
    val modality: String? = null,
    val description: String? = null,
    val reasoning: ReasoningDto? = null,
    val pricing: PricingDto? = null,
    val status: String? = null,
)

@Serializable
data class ReasoningDto(
    val supported: Boolean = false,
    val levels: List<String> = emptyList(),
)

/** Цены за 1k токенов — UI умножает ×1000 (показываем за 1M, как на десктопе). */
@Serializable
data class PricingDto(
    val input_price: Double? = null,
    val output_price: Double? = null,
    val unit: String? = null,
)

@Serializable
data class StatusBucketsDto(
    val model: String? = null,
    val buckets: List<BucketDto> = emptyList(),
)

@Serializable
data class BucketDto(
    val timestamp: Long? = null,
    val requests: Int = 0,
    val success: Int = 0,
    val errors: Int = 0,
    val uptime: Double? = null,
)
