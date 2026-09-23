package ru.syntee.helper.core.data.billing

import ru.syntee.helper.core.network.api.GatewayApi
import javax.inject.Inject
import javax.inject.Singleton

/** Баланс аккаунта на gateway. */
data class Balance(
    val balance: Double,
    val held: Double,
) {
    /** Доступно к списанию (баланс минус заморозка активных стримов). */
    val available: Double get() = balance - held
}

/**
 * Биллинг gateway: /api/user/billing/balance.
 * Топап — в вебе ({WEB}/billing через Custom Tabs), как на десктопе.
 */
@Singleton
class BillingRepository @Inject constructor(
    private val gatewayApi: GatewayApi,
) {

    suspend fun getBalance(): Result<Balance> = runCatching {
        val dto = gatewayApi.getBalance()
        Balance(balance = dto.balance, held = dto.held)
    }
}
