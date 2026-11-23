package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OwnerBankConfig
import com.example.data.model.OwnerBankSettlementRecord
import com.example.data.model.OwnerCommissionEntry
import com.example.ui.theme.*
import com.example.util.OwnerPayoutGatewayClient
import kotlin.math.roundToInt

/**
 * Executive Owner Payout & Automatic Commission Split Console.
 * Allows the platform owner to save Kyrgyzstan bank details (MBank, DemirBank, Optima, Bakai,
 * KICB, Elcart, O!Dengi), configure commission %, enable automatic per-order split settlement,
 * inspect the real-time split ledger, and trigger manual or test settlements.
 */
@Composable
fun OwnerCommissionAndBankPanel(
    config: OwnerBankConfig,
    pendingBalanceSom: Int,
    totalSettledSom: Int,
    ledger: List<OwnerCommissionEntry>,
    settlements: List<OwnerBankSettlementRecord>,
    isPayoutBusy: Boolean,
    onSaveConfig: (OwnerBankConfig) -> Unit,
    onTriggerManualPayout: () -> Unit,
    onRunTestOrderSplit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val kgBanks = listOf(
        "MBank",
        "DemirBank",
        "Optima Bank",
        "Бакай Банк",
        "KICB",
        "Элкарт (РСК/Айыл)",
        "O!Деньги"
    )

    var bankName by remember(config.bankName) { mutableStateOf(config.bankName) }
    var accountInput by remember(config.accountOrCard) { mutableStateOf(config.accountOrCard) }
    var beneficiaryInput by remember(config.beneficiaryName) { mutableStateOf(config.beneficiaryName) }
    var innInput by remember(config.inn) { mutableStateOf(config.inn) }
    var bikInput by remember(config.bik) { mutableStateOf(config.bik) }
    var commissionPct by remember(config.commissionPercent) { mutableFloatStateOf(config.commissionPercent.toFloat()) }
    var autoPayoutEnabled by remember(config.autoPayoutEnabled) { mutableStateOf(config.autoPayoutEnabled) }
    var instantSplit by remember(config.instantPerOrderSplit) { mutableStateOf(config.instantPerOrderSplit) }
    var thresholdSom by remember(config.autoPayoutThresholdSom) { mutableIntStateOf(config.autoPayoutThresholdSom) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val liveGatewayReady = remember { OwnerPayoutGatewayClient.hasLiveGatewayConfigured() }
    val maskedDest = remember(config.accountOrCard) { OwnerPayoutGatewayClient.maskAccount(config.accountOrCard) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Executive Owner Commission Balance Hero Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Доход владельца (Авто-комиссия)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                        )
                        Text(
                            text = "$totalSettledSom сом",
                            fontFamily = JetBrainsMono,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (config.isConfigured && config.autoPayoutEnabled) UberGreen else UberAmber
                    ) {
                        Text(
                            text = if (config.isConfigured && config.autoPayoutEnabled) "АВТО ВКЛ" else "НАСТРОЙТЕ БАНК",
                            color = if (config.isConfigured && config.autoPayoutEnabled) Color.White else Color.Black,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Sub-KPIs: Pending / Commission Rate / Destination Bank
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("В ожидании", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                            Text("$pendingBalanceSom с", fontFamily = JetBrainsMono, fontWeight = FontWeight.ExtraBold, color = UberAmber)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Комиссия", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                            Text("${config.commissionPercent}%", fontFamily = JetBrainsMono, fontWeight = FontWeight.ExtraBold, color = UberGreen)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Реквизиты", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                            Text(
                                text = if (config.isConfigured) "${config.bankName} $maskedDest" else "Не заполнены",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                // Gateway Mode Indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (liveGatewayReady) Icons.Default.VerifiedUser else Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = if (liveGatewayReady) UberGreen else UberBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (liveGatewayReady) {
                            "Банковский шлюз HTTPS активен: комиссия уходит на ваш сервер эквайринга автоматически"
                        } else {
                            "Авто-расщепление активно в приложении. Для реальных переводов укажите ключ эквайринга КР в Secrets"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }

                // Quick Actions: Payout Now + Test Instant Split
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onTriggerManualPayout,
                        enabled = !isPayoutBusy && pendingBalanceSom > 0 && config.isConfigured,
                        colors = ButtonDefaults.buttonColors(containerColor = UberGreen, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("owner_manual_payout_button")
                    ) {
                        if (isPayoutBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Вывести ${pendingBalanceSom}с", fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = onRunTestOrderSplit,
                        enabled = !isPayoutBusy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp).testTag("owner_test_split_button")
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = UberAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Тест заказа", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // 2. Owner Bank Details & Automation Form
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = UberBlue)
                    Text("Банковские реквизиты владельца (КР)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                }

                // Kyrgyzstan Bank Selector Carousel
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    kgBanks.forEach { bank ->
                        FilterChip(
                            selected = bankName == bank,
                            onClick = { bankName = bank },
                            label = { Text(bank, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("owner_bank_${bank.hashCode()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = accountInput,
                    onValueChange = {
                        accountInput = it
                        validationError = null
                    },
                    label = { Text("Р/счёт (IBAN) / Карта / Телефон MBank") },
                    placeholder = { Text("1180000123456789 или +996 555 ...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("owner_account_input")
                )

                OutlinedTextField(
                    value = beneficiaryInput,
                    onValueChange = {
                        beneficiaryInput = it
                        validationError = null
                    },
                    label = { Text("Получатель (ФИО / ИП / ОсОО)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("owner_beneficiary_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = innInput,
                        onValueChange = {
                            innInput = it.filter(Char::isDigit).take(14)
                            validationError = null
                        },
                        label = { Text("ИНН (14 цифр)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f).testTag("owner_inn_input")
                    )
                    OutlinedTextField(
                        value = bikInput,
                        onValueChange = {
                            bikInput = it.filter(Char::isDigit).take(6)
                            validationError = null
                        },
                        label = { Text("БИК (6 цифр)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("owner_bik_input")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                // Commission Percentage Slider (5% - 25%)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ставка комиссии с каждого заказа", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = "${commissionPct.roundToInt()}%",
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = UberBlue
                        )
                    }
                    Slider(
                        value = commissionPct,
                        onValueChange = { commissionPct = it },
                        valueRange = 5f..25f,
                        steps = 19,
                        modifier = Modifier.testTag("owner_commission_slider")
                    )
                    Text(
                        text = "С заказа 400 сом: владельцу автоматически ${(400 * commissionPct.roundToInt() / 100.0).roundToInt()} с, водителю ${400 - (400 * commissionPct.roundToInt() / 100.0).roundToInt()} с.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Auto-Payout & Instant Per-Order Split Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Автоматический перевод на реквизиты", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        Text("Списывать комиссию и отправлять на ваш счёт без ручного участия", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = autoPayoutEnabled,
                        onCheckedChange = { autoPayoutEnabled = it },
                        modifier = Modifier.testTag("owner_auto_payout_switch")
                    )
                }

                if (autoPayoutEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Мгновенный сплит с каждого заказа", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                            Text("Отправлять сразу после завершения каждой поездки (иначе — по порогу)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = instantSplit,
                            onCheckedChange = { instantSplit = it },
                            modifier = Modifier.testTag("owner_instant_split_switch")
                        )
                    }

                    if (!instantSplit) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Порог автовыплаты:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(500, 1000, 2500, 5000).forEach { limit ->
                                    FilterChip(
                                        selected = thresholdSom == limit,
                                        onClick = { thresholdSom = limit },
                                        label = { Text("$limit с", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (!validationError.isNullOrBlank()) {
                    Text(
                        text = validationError!!,
                        color = UberRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        val candidate = OwnerBankConfig(
                            bankName = bankName,
                            accountOrCard = accountInput.trim(),
                            beneficiaryName = beneficiaryInput.trim(),
                            inn = innInput.trim(),
                            bik = bikInput.trim(),
                            commissionPercent = commissionPct.roundToInt(),
                            autoPayoutEnabled = autoPayoutEnabled,
                            instantPerOrderSplit = instantSplit,
                            autoPayoutThresholdSom = thresholdSom
                        )
                        val err = OwnerPayoutGatewayClient.validateBankConfig(candidate)
                        if (err != null) {
                            validationError = err
                        } else {
                            validationError = null
                            onSaveConfig(candidate)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UberBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("owner_save_bank_button")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Сохранить реквизиты и включить авто-комиссию", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. Live Order Commission Split Ledger
        Text("Автоматическое расщепление по заказам", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (ledger.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Завершите любую поездку пассажира или водителя (или нажмите «Тест заказа») — комиссия появится здесь автоматически.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            ledger.take(10).forEach { entry ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${entry.sourceLabel} • ${entry.city}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "Заказ ${entry.grossAmountSom} с → Водителю ${entry.driverNetSom} с • ${entry.timeLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (entry.settledAutomatically) "Авто-перевод → ${entry.destinationMasked}" else "Накоплено к автовыплате",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.settledAutomatically) UberGreen else UberAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "+${entry.ownerCommissionSom} с",
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Black,
                                color = UberGreen,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "${entry.commissionPercent}%",
                                fontFamily = JetBrainsMono,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 4. Bank Settlements History
        if (settlements.isNotEmpty()) {
            Text("История перечислений на счёт владельца", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            settlements.take(8).forEach { rec ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = UberGreen.copy(alpha = 0.10f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, UberGreen.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${rec.bankName} ${rec.destinationMasked}", fontWeight = FontWeight.Bold)
                            Text(
                                text = "${if (rec.automatic) "Автовыплата" else "Ручной вывод"} • ${rec.referenceCode} • ${rec.timeLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (rec.liveGatewayDelivered) "Подтверждено банковским шлюзом HTTPS" else "Зафиксировано в реестре авто-сплита",
                                style = MaterialTheme.typography.labelSmall,
                                color = UberBlue
                            )
                        }
                        Text(
                            text = "+${rec.amountSom} с",
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Black,
                            color = UberGreen,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
