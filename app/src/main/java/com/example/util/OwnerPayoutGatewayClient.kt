package com.example.util

import com.example.data.model.OwnerBankConfig
import java.net.URI
import kotlin.math.roundToInt

data class SplitCalculationResult(
    val grossSom: Int,
    val commissionPercent: Int,
    val ownerCommissionSom: Int,
    val driverNetSom: Int
)

data class GatewaySettlementResult(
    val deliveredViaLiveGateway: Boolean,
    val referenceCode: String,
    val statusMessage: String
)

/**
 * Handles automated order commission splitting, bank detail validation,
 * and owner payout requests. Sending money is a server job (see hasLiveGatewayConfigured).
 */
object OwnerPayoutGatewayClient {

    fun calculateOrderSplit(
        grossSom: Int,
        commissionPercent: Int,
        commissionFreeShiftActive: Boolean = false
    ): SplitCalculationResult {
        val safeGross = grossSom.coerceAtLeast(0)
        if (commissionFreeShiftActive) {
            return SplitCalculationResult(
                grossSom = safeGross,
                commissionPercent = 0,
                ownerCommissionSom = 0,
                driverNetSom = safeGross
            )
        }
        val pct = commissionPercent.coerceIn(1, 35)
        val ownerCut = (safeGross * (pct / 100.0)).roundToInt().coerceIn(0, safeGross)
        val driverCut = (safeGross - ownerCut).coerceAtLeast(0)
        return SplitCalculationResult(
            grossSom = safeGross,
            commissionPercent = pct,
            ownerCommissionSom = ownerCut,
            driverNetSom = driverCut
        )
    }

    fun maskAccount(raw: String): String {
        val clean = raw.trim()
        if (clean.length <= 4) return if (clean.isEmpty()) "Не указаны" else "•••• $clean"
        return "•••• ${clean.takeLast(4)}"
    }

    fun validateBankConfig(config: OwnerBankConfig): String? {
        val cleanAcc = config.accountOrCard.trim()
        if (cleanAcc.length < 8) {
            return "Укажите корректный номер счёта, карты, IBAN или телефона MBank (минимум 8 символов)."
        }
        if (config.beneficiaryName.trim().length < 2) {
            return "Укажите ФИО владельца или название ИП / ОсОО."
        }
        if (config.inn.isNotBlank() && config.inn.filter(Char::isDigit).length != 14) {
            return "ИНН в Кыргызстане должен содержать 14 цифр (или оставьте пустым для физ. лица)."
        }
        if (config.bik.isNotBlank() && config.bik.filter(Char::isDigit).length != 6) {
            return "БИК банка КР должен содержать 6 цифр."
        }
        if (config.commissionPercent !in 5..25) {
            return "Ставка комиссии должна быть в пределах от 5% до 25%."
        }
        return null
    }

    private fun isRealSecretValue(value: String): Boolean {
        val clean = value.trim()
        return clean.isNotBlank() && !clean.startsWith("YOUR_", ignoreCase = true)
    }

    /**
     * Always false in the app. The payment gateway key must never be inside the APK:
     * anyone can unpack an APK and read it. Real payouts will be sent by a server
     * (Firebase Cloud Functions) that keeps the key in its own secret storage.
     */
    fun hasLiveGatewayConfigured(): Boolean = false

    /**
     * Strict URL validation: HTTPS only, non-blank host, blocks localhost, loopback,
     * link-local metadata, and RFC1918 private network ranges.
     */
    fun isSafePublicHttpsUrl(rawUrl: String): Boolean {
        if (rawUrl.isBlank()) return false
        return try {
            val uri = URI(rawUrl.trim())
            if (!uri.scheme.equals("https", ignoreCase = true)) return false
            val host = uri.host?.lowercase() ?: return false
            if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) return false
            if (host == "127.0.0.1" || host == "::1" || host == "0.0.0.0" || host == "169.254.169.254") return false
            if (host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("169.254.")) return false
            if (host.startsWith("172.")) {
                val secondOctet = host.removePrefix("172.").substringBefore(".").toIntOrNull()
                if (secondOctet != null && secondOctet in 16..31) return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Records an owner payout request. No money is sent from the phone: the request only
     * goes into the owner's ledger, and the payout has to be made by the server or by hand.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun executeOwnerSettlement(
        config: OwnerBankConfig,
        amountSom: Int,
        referenceCode: String,
        orderId: String?,
        offlineMode: Boolean
    ): GatewaySettlementResult = GatewaySettlementResult(
        deliveredViaLiveGateway = false,
        referenceCode = referenceCode,
        statusMessage = "Заявка на выплату $amountSom сом на ${config.bankName} ${maskAccount(config.accountOrCard)} записана. " +
            "Деньги автоматически НЕ отправлены — перевод делается вручную, пока не подключён сервер."
    )
}
