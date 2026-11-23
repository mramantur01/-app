package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.OwnerBankConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kgTaxiPrefsDataStore by preferencesDataStore(name = "kg_taxi_prefs")

data class AppPrefsSnapshot(
    val onboardingSeen: Boolean = false,
    val walletBalanceSom: Int = 1450,
    val referralClaimed: Boolean = false,
    val displayCurrencyCode: String = "KGS",
    val businessCompanyInn: String = "01408202110234",
    val favoriteDrivers: Set<String> = setOf("Бекзат А."),
    val blockedDrivers: Set<String> = emptySet(),
    val ownerBankConfig: OwnerBankConfig = OwnerBankConfig(),
    val ownerPendingBalanceSom: Int = 0,
    val ownerTotalSettledSom: Int = 6840
)

class AppPreferencesStore(private val context: Context) {
    private val keyOnboardingSeen = booleanPreferencesKey("onboarding_seen")
    private val keyWalletBalance = intPreferencesKey("wallet_balance_som")
    private val keyReferralClaimed = booleanPreferencesKey("referral_claimed")
    private val keyCurrencyCode = stringPreferencesKey("display_currency_code")
    private val keyBusinessInn = stringPreferencesKey("business_company_inn")
    private val keyFavoriteDrivers = stringSetPreferencesKey("favorite_drivers")
    private val keyBlockedDrivers = stringSetPreferencesKey("blocked_drivers")

    // Owner Automated Commission & Bank Details Keys
    private val keyOwnerBankName = stringPreferencesKey("owner_bank_name")
    private val keyOwnerAccount = stringPreferencesKey("owner_account_or_card")
    private val keyOwnerBeneficiary = stringPreferencesKey("owner_beneficiary_name")
    private val keyOwnerInn = stringPreferencesKey("owner_inn")
    private val keyOwnerBik = stringPreferencesKey("owner_bik")
    private val keyOwnerCommissionPct = intPreferencesKey("owner_commission_pct")
    private val keyOwnerAutoPayout = booleanPreferencesKey("owner_auto_payout")
    private val keyOwnerInstantSplit = booleanPreferencesKey("owner_instant_split")
    private val keyOwnerThreshold = intPreferencesKey("owner_payout_threshold_som")
    private val keyOwnerPendingSom = intPreferencesKey("owner_pending_balance_som")
    private val keyOwnerSettledSom = intPreferencesKey("owner_total_settled_som")

    val prefsFlow: Flow<AppPrefsSnapshot> = context.kgTaxiPrefsDataStore.data.map { prefs ->
        AppPrefsSnapshot(
            onboardingSeen = prefs[keyOnboardingSeen] ?: false,
            walletBalanceSom = prefs[keyWalletBalance] ?: 1450,
            referralClaimed = prefs[keyReferralClaimed] ?: false,
            displayCurrencyCode = prefs[keyCurrencyCode] ?: "KGS",
            businessCompanyInn = prefs[keyBusinessInn] ?: "01408202110234",
            favoriteDrivers = prefs[keyFavoriteDrivers] ?: setOf("Бекзат А."),
            blockedDrivers = prefs[keyBlockedDrivers] ?: emptySet(),
            ownerBankConfig = OwnerBankConfig(
                bankName = prefs[keyOwnerBankName] ?: "MBank",
                accountOrCard = prefs[keyOwnerAccount] ?: "",
                beneficiaryName = prefs[keyOwnerBeneficiary] ?: "ИП KG Taxi Владелец",
                inn = prefs[keyOwnerInn] ?: "",
                bik = prefs[keyOwnerBik] ?: "103001",
                commissionPercent = (prefs[keyOwnerCommissionPct] ?: 14).coerceIn(5, 25),
                autoPayoutEnabled = prefs[keyOwnerAutoPayout] ?: true,
                instantPerOrderSplit = prefs[keyOwnerInstantSplit] ?: true,
                autoPayoutThresholdSom = (prefs[keyOwnerThreshold] ?: 500).coerceIn(100, 50000)
            ),
            ownerPendingBalanceSom = prefs[keyOwnerPendingSom] ?: 0,
            ownerTotalSettledSom = prefs[keyOwnerSettledSom] ?: 6840
        )
    }

    suspend fun setOnboardingSeen(seen: Boolean) {
        context.kgTaxiPrefsDataStore.edit { it[keyOnboardingSeen] = seen }
    }

    suspend fun setWalletBalance(balanceSom: Int) {
        context.kgTaxiPrefsDataStore.edit { it[keyWalletBalance] = balanceSom.coerceAtLeast(0) }
    }

    suspend fun setReferralClaimed(claimed: Boolean) {
        context.kgTaxiPrefsDataStore.edit { it[keyReferralClaimed] = claimed }
    }

    suspend fun setDisplayCurrencyCode(code: String) {
        context.kgTaxiPrefsDataStore.edit { it[keyCurrencyCode] = code.uppercase() }
    }

    suspend fun setBusinessCompanyInn(inn: String) {
        context.kgTaxiPrefsDataStore.edit { it[keyBusinessInn] = inn.trim() }
    }

    suspend fun saveOwnerBankConfig(config: OwnerBankConfig) {
        context.kgTaxiPrefsDataStore.edit { prefs ->
            prefs[keyOwnerBankName] = config.bankName.trim()
            prefs[keyOwnerAccount] = config.accountOrCard.trim()
            prefs[keyOwnerBeneficiary] = config.beneficiaryName.trim()
            prefs[keyOwnerInn] = config.inn.filter(Char::isDigit).take(14)
            prefs[keyOwnerBik] = config.bik.filter(Char::isDigit).take(6)
            prefs[keyOwnerCommissionPct] = config.commissionPercent.coerceIn(5, 25)
            prefs[keyOwnerAutoPayout] = config.autoPayoutEnabled
            prefs[keyOwnerInstantSplit] = config.instantPerOrderSplit
            prefs[keyOwnerThreshold] = config.autoPayoutThresholdSom.coerceIn(100, 50000)
        }
    }

    suspend fun saveOwnerBalances(pendingSom: Int, totalSettledSom: Int) {
        context.kgTaxiPrefsDataStore.edit { prefs ->
            prefs[keyOwnerPendingSom] = pendingSom.coerceAtLeast(0)
            prefs[keyOwnerSettledSom] = totalSettledSom.coerceAtLeast(0)
        }
    }

    suspend fun toggleFavoriteDriver(driverName: String) {
        context.kgTaxiPrefsDataStore.edit { prefs ->
            val currentFav = (prefs[keyFavoriteDrivers] ?: setOf("Бекзат А.")).toMutableSet()
            val currentBlocked = (prefs[keyBlockedDrivers] ?: emptySet()).toMutableSet()
            if (driverName in currentFav) {
                currentFav.remove(driverName)
            } else {
                currentFav.add(driverName)
                currentBlocked.remove(driverName)
            }
            prefs[keyFavoriteDrivers] = currentFav
            prefs[keyBlockedDrivers] = currentBlocked
        }
    }

    suspend fun toggleBlockedDriver(driverName: String) {
        context.kgTaxiPrefsDataStore.edit { prefs ->
            val currentFav = (prefs[keyFavoriteDrivers] ?: setOf("Бекзат А.")).toMutableSet()
            val currentBlocked = (prefs[keyBlockedDrivers] ?: emptySet()).toMutableSet()
            if (driverName in currentBlocked) {
                currentBlocked.remove(driverName)
            } else {
                currentBlocked.add(driverName)
                currentFav.remove(driverName)
            }
            prefs[keyFavoriteDrivers] = currentFav
            prefs[keyBlockedDrivers] = currentBlocked
        }
    }
}

