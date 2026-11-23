package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.KgGeoCatalog
import com.example.data.model.OwnerBankConfig
import com.example.i18n.KgStrings
import com.example.ui.KgTaxiUiState
import com.example.ui.components.DispatcherFleetMapAndSosPanel
import com.example.ui.components.OwnerCommissionAndBankPanel
import com.example.ui.theme.*
import com.example.util.OwnerPayoutGatewayClient

@Composable
fun DispatcherDashboardScreen(
    strings: KgStrings,
    state: KgTaxiUiState,
    onExportPdfReport: () -> Unit,
    onSendPromoAlert: () -> Unit,
    onSendTrafficAlert: () -> Unit,
    onAssignDriver: (String) -> Unit,
    onSimulateSos: () -> Unit,
    onResolveSos: (String) -> Unit,
    onSaveOwnerBankConfig: (OwnerBankConfig) -> Unit,
    onTriggerOwnerPayout: () -> Unit,
    onRunTestOwnerOrderSplit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Владелец & Банк, 1 = Мониторинг
    var selectedCityFilter by remember { mutableStateOf("Все города") }
    val activeOrdersCount = state.dispatcherActiveOrders.size
    val onlineDriversCount = state.dispatcherActiveOrders.mapNotNull { it.assignedDriverName }.distinct().size
    val totalRevenueSom = state.dispatcherActiveOrders.sumOf { it.grossFareSom }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Кабинет владельца и диспетчера", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(
                    text = if (state.ownerBankConfig.isConfigured) {
                        "Авто-комиссия ${state.ownerBankConfig.commissionPercent}% → ${state.ownerBankConfig.bankName} ${OwnerPayoutGatewayClient.maskAccount(state.ownerBankConfig.accountOrCard)}"
                    } else {
                        "Укажите реквизиты банка для авто-выплат комиссий"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.ownerBankConfig.isConfigured) UberGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = onExportPdfReport,
                modifier = Modifier.testTag("dispatcher_export_pdf_button")
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("PDF")
            }
        }

        // Top 2-Tab Switcher: Владелец & Банк vs Мониторинг парка
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("dispatcher_tab_owner_bank")
            ) {
                Text("Владелец & Банк", maxLines = 1)
            }
            SegmentedButton(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Default.MonitorHeart, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("dispatcher_tab_monitoring")
            ) {
                Text("Мониторинг", maxLines = 1)
            }
        }

        if (activeTab == 0) {
            OwnerCommissionAndBankPanel(
                config = state.ownerBankConfig,
                pendingBalanceSom = state.ownerPendingBalanceSom,
                totalSettledSom = state.ownerTotalSettledSom,
                ledger = state.ownerCommissionLedger,
                settlements = state.ownerSettlements,
                isPayoutBusy = state.ownerPayoutBusy,
                onSaveConfig = onSaveOwnerBankConfig,
                onTriggerManualPayout = onTriggerOwnerPayout,
                onRunTestOrderSplit = onRunTestOwnerOrderSplit
            )
            return@Column
        }

        // KPI Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(
                title = strings.activeOrders,
                value = "$activeOrdersCount",
                accent = UberBlue,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Водителей на заказах",
                value = "$onlineDriversCount",
                accent = UberGreen,
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(
                title = "Сумма активных заказов",
                value = "$totalRevenueSom ${strings.currencySom}",
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Комиссия (${state.ownerBankConfig.commissionPercent}%)",
                value = "${state.ownerTotalSettledSom + state.ownerPendingBalanceSom} ${strings.currencySom}",
                accent = UberGreen,
                modifier = Modifier.weight(1f)
            )
        }

        // Live Fleet Map & Emergency SOS Monitor
        DispatcherFleetMapAndSosPanel(
            city = state.selectedCity,
            drivers = state.nearbyDrivers,
            sosAlerts = state.sosAlerts,
            darkTheme = state.darkTheme,
            onSimulateSos = onSimulateSos,
            onResolveSos = onResolveSos
        )

        // Real-Time City Demand Bar Chart (Compose Canvas)
        val cityBars = listOf(
            "Бишкек" to 0.95f,
            "Ош" to 0.68f,
            "Чолп-Ата" to 0.54f,
            "Каракол" to 0.46f,
            "Джалал-А." to 0.41f,
            "Нарын" to 0.28f
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Спрос по городам КР (Live Analytics)", fontWeight = FontWeight.Bold)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    val slotW = size.width / cityBars.size
                    val barW = slotW * 0.52f
                    cityBars.forEachIndexed { i, (_, frac) ->
                        val barH = size.height * frac
                        val left = i * slotW + (slotW - barW) / 2f
                        drawRoundRect(
                            color = if (i == 0) UberBlue else if (i == 2) UberGreen else Color(0xFF5E6B7A),
                            topLeft = Offset(left, size.height - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(10f, 10f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    cityBars.forEach { (label, _) ->
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }

        // Push Notification Triggers: Promotions & Real-Time Traffic Alerts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSendPromoAlert,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp).testTag("dispatcher_push_promo_button")
            ) {
                Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Промо ECO15", maxLines = 1)
            }
            OutlinedButton(
                onClick = onSendTrafficAlert,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp).testTag("dispatcher_push_traffic_button")
            ) {
                Icon(Icons.Default.Traffic, contentDescription = null, modifier = Modifier.size(16.dp), tint = UberAmber)
                Spacer(Modifier.width(6.dp))
                Text("Алерт пробок", maxLines = 1)
            }
        }

        // Interactive Dispatch Queue (Assign Drivers Manually in Real Time)
        Text("Очередь распределения заказов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        state.dispatcherActiveOrders.forEach { order ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (order.assignedDriverName != null) UberGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${order.tariffName} • ${order.passengerName} (${order.city})", fontWeight = FontWeight.Bold)
                        Text("${order.pickupAddress} → ${order.dropoffAddress}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        Text(
                            text = if (order.assignedDriverName != null) "Назначен: ${order.assignedDriverName}" else "Ожидает назначения • ${order.grossFareSom} ${strings.currencySom}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (order.assignedDriverName != null) UberGreen else UberBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (order.assignedDriverName == null) {
                        Button(
                            onClick = { onAssignDriver(order.id) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("dispatch_assign_${order.id}")
                        ) {
                            Text("Назначить", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Assigned", tint = UberGreen)
                    }
                }
            }
        }

        // City Filter Chips for Completed & Live Orders
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val cities = listOf("Все города") + KgGeoCatalog.cities.map { it.name }
            cities.forEach { cityLabel ->
                FilterChip(
                    selected = selectedCityFilter == cityLabel,
                    onClick = { selectedCityFilter = cityLabel },
                    label = { Text(cityLabel) }
                )
            }
        }

        // Live & Historical Orders Table
        Text("Последние и активные заказы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (state.activeRide != null && (selectedCityFilter == "Все города" || state.activeRide.city == selectedCityFilter)) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = UberBlue.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("LIVE • ${state.activeRide.tariffName} (${state.activeRide.city})", fontWeight = FontWeight.Bold, color = UberBlue)
                        Text("${state.activeRide.pickupAddress} → ${state.activeRide.dropoffAddress}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    Text("${state.activeRide.totalPriceSom}с", fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                }
            }
        }
        val filteredTrips = state.trips.filter { selectedCityFilter == "Все города" || it.city == selectedCityFilter }
        filteredTrips.take(8).forEach { trip ->
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${trip.serviceType} • ${trip.tariffName} (${trip.city})", fontWeight = FontWeight.SemiBold)
                        Text("${trip.pickupAddress} → ${trip.dropoffAddress}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    Text("${trip.totalPriceSom} ${strings.currencySom}", fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun KpiCard(title: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontFamily = JetBrainsMono, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = accent)
        }
    }
}
