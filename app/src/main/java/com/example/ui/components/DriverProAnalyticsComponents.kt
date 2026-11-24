package com.example.ui.components

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
import androidx.compose.ui.unit.dp
import com.example.data.model.DriverPayoutRecord
import com.example.data.model.DriverTipEntry
import com.example.ui.theme.*

@Composable
fun DriverDestinationFilterCard(
    enabled: Boolean,
    targetDistrict: String,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectDistrict: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val districts = listOf("Джал", "Восток-5", "Центр / ЦУМ", "Асанбай", "Аэропорт Манас", "Ош Центр")

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) UberGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.HomeWork,
                        contentDescription = null,
                        tint = if (enabled) UberGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text("Фильтр «По пути домой»", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (enabled) "Приоритет заказов в район: $targetDistrict" else "Подсвечивает попутные заказы в конце смены",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("driver_home_filter_switch")
                )
            }

            if (enabled) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    districts.forEach { dist ->
                        FilterChip(
                            selected = targetDistrict == dist,
                            onClick = { onSelectDistrict(dist) },
                            label = { Text(dist, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("driver_home_dist_${dist.take(4)}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DriverRatingsTipsAndPayoutSection(
    availableBalanceSom: Int,
    tips: List<DriverTipEntry>,
    payouts: List<DriverPayoutRecord>,
    onWithdrawEarnings: (bankName: String, targetAccount: String, amountSom: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedBank by remember { mutableStateOf("MBank") }
    var targetAccount by remember { mutableStateOf("") }
    // Real per-category ratings need passenger reviews stored on the server; none yet.
    val ratingMetrics = emptyList<Pair<String, Float>>()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Instant Payout Card (MBank / Элкарт / O!Деньги)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = UberGreen)
                        Text("Мгновенный вывод средств", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "$availableBalanceSom сом",
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.ExtraBold,
                        color = UberGreen
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("MBank", "Элкарт", "O!Деньги").forEach { bank ->
                        FilterChip(
                            selected = selectedBank == bank,
                            onClick = { selectedBank = bank },
                            label = { Text(bank, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("payout_bank_${bank.lowercase()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = targetAccount,
                    onValueChange = { targetAccount = it },
                    label = { Text("Номер телефона / карты ($selectedBank)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (availableBalanceSom >= 500 && targetAccount.isNotBlank()) {
                            onWithdrawEarnings(selectedBank, targetAccount.trim(), availableBalanceSom)
                        }
                    },
                    enabled = availableBalanceSom >= 500,
                    colors = ButtonDefaults.buttonColors(containerColor = UberGreen, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("driver_instant_payout_button")
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (availableBalanceSom >= 500) "Вывести $availableBalanceSom сом на $selectedBank" else "Минимум 500 сом для вывода",
                        fontWeight = FontWeight.Bold
                    )
                }

                payouts.take(3).forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${p.bankName} (${p.targetAccount}) • ${p.timeLabel}", style = MaterialTheme.typography.labelSmall)
                        Text("-${p.amountSom} с", fontFamily = JetBrainsMono, style = MaterialTheme.typography.labelSmall, color = UberBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Driver Rating Breakdown & Passenger Tips Log
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Рейтинг и чаевые", fontWeight = FontWeight.Bold)
                    Text(
                        text = "+${tips.sumOf { it.amountSom }} с чаевых",
                        fontFamily = JetBrainsMono,
                        color = UberGreen,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                ratingMetrics.forEach { (label, score) ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                            Text(String.format(java.util.Locale.US, "%.2f", score), fontFamily = JetBrainsMono, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(
                            progress = { (score / 5f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                            color = UberAmber,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                tips.take(3).forEach { tip ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${tip.passengerName} • ${tip.compliment}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text(tip.timeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = UberGreen.copy(alpha = 0.14f)) {
                            Text(
                                text = "+${tip.amountSom} с",
                                fontFamily = JetBrainsMono,
                                color = UberGreen,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
