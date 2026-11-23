package com.example.util

import com.example.BuildConfig
import com.example.data.model.OwnerBankConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
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
 * and optional HTTPS split-payout webhook dispatch to a real acquiring/bank server
 * configured via AI Studio Secrets (PAYMENT_GATEWAY_WEBHOOK_URL + PAYMENT_GATEWAY_API_KEY).
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

    fun hasLiveGatewayConfigured(): Boolean {
        val url = BuildConfig.PAYMENT_GATEWAY_WEBHOOK_URL.trim()
        val apiKey = BuildConfig.PAYMENT_GATEWAY_API_KEY.trim()
        return isRealSecretValue(apiKey) && isRealSecretValue(url) && isSafePublicHttpsUrl(url)
    }

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
     * Dispatches an automated owner settlement payload to the configured HTTPS acquiring gateway
     * (if configured in Secrets), or returns a clean internal ledger settlement reference.
     */
    suspend fun executeOwnerSettlement(
        config: OwnerBankConfig,
        amountSom: Int,
        referenceCode: String,
        orderId: String?,
        offlineMode: Boolean
    ): GatewaySettlementResult = withContext(Dispatchers.IO) {
        val webhookUrl = BuildConfig.PAYMENT_GATEWAY_WEBHOOK_URL.trim()
        val apiKey = BuildConfig.PAYMENT_GATEWAY_API_KEY.trim()

        if (offlineMode || !isRealSecretValue(apiKey) || !isRealSecretValue(webhookUrl) || !isSafePublicHttpsUrl(webhookUrl)) {
            return@withContext GatewaySettlementResult(
                deliveredViaLiveGateway = false,
                referenceCode = referenceCode,
                statusMessage = "Зачислено в авто-реестр на ${config.bankName} ${maskAccount(config.accountOrCard)}"
            )
        }

        try {
            val uri = URI(webhookUrl)
            val resolved = InetAddress.getByName(uri.host)
            if (resolved.isLoopbackAddress || resolved.isSiteLocalAddress || resolved.isLinkLocalAddress || resolved.isAnyLocalAddress) {
                return@withContext GatewaySettlementResult(
                    deliveredViaLiveGateway = false,
                    referenceCode = referenceCode,
                    statusMessage = "Шлюз отклонён политикой безопасности; проведено во внутреннем реестре"
                )
            }

            val conn = (URL(webhookUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("X-Idempotency-Key", referenceCode)
            }

            val payload = JSONObject().apply {
                put("event", "owner_commission_auto_payout")
                put("referenceCode", referenceCode)
                put("orderId", orderId ?: "BATCH")
                put("currency", "KGS")
                put("amountSom", amountSom)
                put("bankName", config.bankName)
                put("accountOrCard", config.accountOrCard.trim())
                put("beneficiaryName", config.beneficiaryName.trim())
                put("inn", config.inn.trim())
                put("bik", config.bik.trim())
                put("commissionPercent", config.commissionPercent)
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            conn.disconnect()

            if (code in 200..299) {
                GatewaySettlementResult(
                    deliveredViaLiveGateway = true,
                    referenceCode = referenceCode,
                    statusMessage = "Отправлено через банковский шлюз (HTTP $code) на ${config.bankName} ${maskAccount(config.accountOrCard)}"
                )
            } else {
                GatewaySettlementResult(
                    deliveredViaLiveGateway = false,
                    referenceCode = referenceCode,
                    statusMessage = "Шлюз вернул HTTP $code — выплата зафиксирована в реестре владельца"
                )
            }
        } catch (_: Exception) {
            GatewaySettlementResult(
                deliveredViaLiveGateway = false,
                referenceCode = referenceCode,
                statusMessage = "Нет ответа от шлюза — зафиксировано в реестре на ${config.bankName} ${maskAccount(config.accountOrCard)}"
            )
        }
    }
}
