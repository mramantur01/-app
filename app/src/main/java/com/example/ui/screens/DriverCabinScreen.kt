package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DriverShiftPass
import com.example.data.model.KgGeoCatalog
import com.example.i18n.KgStrings
import com.example.ui.KgTaxiUiState
import com.example.ui.KgTaxiViewModel
import com.example.ui.components.DriverDestinationFilterCard
import com.example.ui.components.DriverFatigueAndCoffeeBreakCard
import com.example.ui.components.DriverHourlyDemandChartCard
import com.example.ui.components.DriverLineMapCockpit
import com.example.ui.components.DriverRatingsTipsAndPayoutSection
import com.example.ui.components.DriverShiftPassCard
import com.example.ui.components.DriverSidePassengerChatOverlay
import com.example.ui.components.ElqrPaymentDialog
import com.example.ui.components.MeetAndGreetPlacardDialog
import com.example.ui.theme.*
import java.util.Locale

private enum class DriverCabinTab(val label: String) {
    LINE("Линия"),
    EARNINGS("Доход"),
    VEHICLE("Авто"),
    PRO("Про")
}

@Composable
fun DriverCabinScreen(
    strings: KgStrings,
    state: KgTaxiUiState,
    viewModel: KgTaxiViewModel,
    onRequestVoiceCommand: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(DriverCabinTab.LINE) }
    var showAddMaintDialog by remember { mutableStateOf(false) }
    var showDriverQrDialog by remember { mutableStateOf(false) }
    var showMeetGreetPlacard by remember { mutableStateOf(false) }
    var showDriverPassengerChat by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Main Tab Content Area
        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
            when (activeTab) {
                DriverCabinTab.LINE -> DriverLineMapCockpit(
                    strings = strings,
                    state = state,
                    onToggleOnline = viewModel::toggleDriverOnline,
                    onAcceptOrder = viewModel::acceptDriverOrder,
                    onSkipOrder = viewModel::skipDriverOrder,
                    onAdvanceStage = viewModel::advanceDriverOrderStage,
                    onCancelAcceptedOrder = viewModel::cancelDriverAcceptedOrder,
                    onToggleSurgeMap = viewModel::toggleSurgeHeatmap,
                    onToggleAutoAccept = viewModel::toggleDriverAutoAccept,
                    onToggleHomeFilter = {
                        viewModel.setDriverHomeFilter(!state.driverHomeFilterEnabled, state.driverHomeTargetDistrict)
                    },
                    onToggleCoffeeBreak = viewModel::toggleDriverCoffeeBreak,
                    onAcceptBackToBack = viewModel::acceptBackToBackOrder,
                    onDeclineBackToBack = viewModel::declineBackToBackOrder,
                    onOpenDriverPassengerChat = { showDriverPassengerChat = true },
                    onSelectCity = viewModel::selectCity,
                    onOpenEarningsTab = { activeTab = DriverCabinTab.EARNINGS },
                    onOpenProTab = { activeTab = DriverCabinTab.PRO },
                    onOpenVoiceControl = onRequestVoiceCommand,
                    onOpenElqrDialog = { showDriverQrDialog = true },
                    onOpenMeetGreetPlacard = { showMeetGreetPlacard = true },
                    onTriggerSos = { viewModel.triggerEmergencySos(fromActiveRide = false) }
                )

                DriverCabinTab.EARNINGS -> DriverEarningsTabContent(
                    strings = strings,
                    state = state,
                    onSelectShiftPass = viewModel::purchaseDriverShiftPass,
                    onWithdrawEarnings = viewModel::withdrawDriverEarnings
                )

                DriverCabinTab.VEHICLE -> DriverVehicleAndDocsTabContent(
                    strings = strings,
                    state = state,
                    viewModel = viewModel,
                    onOpenAddMaintenance = { showAddMaintDialog = true }
                )

                DriverCabinTab.PRO -> DriverProStatusTabContent(
                    state = state,
                    viewModel = viewModel,
                    onOpenMeetGreetPlacard = { showMeetGreetPlacard = true }
                )
            }
        }

        // Authentic Uber Driver / Yandex Pro 4-Tab Bottom Navigation Bar
        // Hidden while on the line so the driver sees only the map and the order.
        if (!state.driverOnline && state.driverAcceptedOrder == null) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.navigationBarsPadding()
        ) {
            NavigationBarItem(
                selected = activeTab == DriverCabinTab.LINE,
                onClick = { activeTab = DriverCabinTab.LINE },
                icon = { Icon(Icons.Default.Navigation, contentDescription = "Линия") },
                label = { Text(DriverCabinTab.LINE.label) },
                modifier = Modifier.testTag("driver_tab_line")
            )
            NavigationBarItem(
                selected = activeTab == DriverCabinTab.EARNINGS,
                onClick = { activeTab = DriverCabinTab.EARNINGS },
                icon = { Icon(Icons.Default.Payments, contentDescription = "Доход") },
                label = { Text(DriverCabinTab.EARNINGS.label) },
                modifier = Modifier.testTag("driver_tab_earnings")
            )
            NavigationBarItem(
                selected = activeTab == DriverCabinTab.VEHICLE,
                onClick = { activeTab = DriverCabinTab.VEHICLE },
                icon = { Icon(Icons.Default.DirectionsCar, contentDescription = "Авто") },
                label = { Text(DriverCabinTab.VEHICLE.label) },
                modifier = Modifier.testTag("driver_tab_vehicle")
            )
            NavigationBarItem(
                selected = activeTab == DriverCabinTab.PRO,
                onClick = { activeTab = DriverCabinTab.PRO },
                icon = { Icon(Icons.Default.VerifiedUser, contentDescription = "Про") },
                label = { Text(DriverCabinTab.PRO.label) },
                modifier = Modifier.testTag("driver_tab_pro")
            )
        }
        }
    }

    // Post-Trip Rate Passenger Dialog (Uber Driver / Yandex Pro signature)
    val rateOrder = state.driverPendingPassengerRating
    if (rateOrder != null) {
        var passengerStars by remember(rateOrder.id) { mutableIntStateOf(5) }
        AlertDialog(
            onDismissRequest = { viewModel.submitDriverPassengerRating(5) },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Поездка завершена!", fontWeight = FontWeight.Black)
                    Text(
                        text = "+${rateOrder.upfrontNetEarningsSom} ${strings.currencySom}",
                        fontFamily = JetBrainsMono,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = UberGreen
                    )
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Оцените пассажира: ${rateOrder.passengerName}", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.Center) {
                        (1..5).forEach { s ->
                            IconButton(onClick = { passengerStars = s }) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "$s stars",
                                    tint = if (s <= passengerStars) UberAmber else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.submitDriverPassengerRating(passengerStars) },
                    modifier = Modifier.fillMaxWidth().testTag("driver_rate_passenger_submit")
                ) {
                    Text("Отправить оценку и на линию", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showAddMaintDialog) {
        var vehicle by remember { mutableStateOf("Toyota Camry 70") }
        var service by remember { mutableStateOf("Замена тормозных колодок") }
        var dueKm by remember { mutableStateOf("98000") }
        var currKm by remember { mutableStateOf("97100") }
        AlertDialog(
            onDismissRequest = { showAddMaintDialog = false },
            title = { Text("Новое напоминание ТО") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = vehicle, onValueChange = { vehicle = it }, label = { Text("Автомобиль") })
                    OutlinedTextField(value = service, onValueChange = { service = it }, label = { Text("Работы / ТО") })
                    OutlinedTextField(value = currKm, onValueChange = { currKm = it.filter(Char::isDigit) }, label = { Text("Текущий пробег (км)") })
                    OutlinedTextField(value = dueKm, onValueChange = { dueKm = it.filter(Char::isDigit) }, label = { Text("Плановый пробег (км)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addMaintenanceLog(
                        vehicle = vehicle,
                        service = service,
                        dueKm = dueKm.toIntOrNull() ?: 100000,
                        currentKm = currKm.toIntOrNull() ?: 95000,
                        liters = state.fuelConsumptionPer100.toDoubleOrNull() ?: 8.5
                    )
                    showAddMaintDialog = false
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showAddMaintDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showDriverQrDialog && state.driverAcceptedOrder != null) {
        val ord = state.driverAcceptedOrder
        ElqrPaymentDialog(
            title = "Приём оплаты ELQR (Водитель)",
            amountSom = ord.grossFareSom,
            referenceCode = ord.id.takeLast(6),
            onConfirmPaid = {
                viewModel.markElqrPaymentDone(ord.grossFareSom, "Приём ELQR • ${ord.passengerName}")
                showDriverQrDialog = false
            },
            onDismiss = { showDriverQrDialog = false }
        )
    }

    if (showMeetGreetPlacard) {
        val guestSign = state.driverAcceptedOrder?.meetAndGreetSign
            ?: state.meetAndGreetSignText.ifBlank { "DR. MICHAEL WEBER" }
        val flightStr = state.selectedFlight?.let { "${it.code} • ${it.origin}" } ?: "TK 344 • Стамбул → Манас FRU"
        MeetAndGreetPlacardDialog(
            guestName = guestSign,
            flightLabel = flightStr,
            onDismiss = { showMeetGreetPlacard = false }
        )
    }

    val activeOrderForChat = state.driverAcceptedOrder
    if (showDriverPassengerChat && activeOrderForChat != null) {
        DriverSidePassengerChatOverlay(
            passengerName = activeOrderForChat.passengerName,
            messages = state.driverActiveOrderMessages,
            translateTargetLang = state.chatTranslateTargetLang,
            isTranslating = state.chatTranslating,
            onSelectTranslateLang = viewModel::setChatTranslateLang,
            onTranslateAll = viewModel::translateDriverSideChat,
            onSendDriverMessage = viewModel::sendDriverSideChatMessage,
            onDismiss = { showDriverPassengerChat = false }
        )
    }
}

@Composable
private fun DriverEarningsTabContent(
    strings: KgStrings,
    state: KgTaxiUiState,
    onSelectShiftPass: (DriverShiftPass) -> Unit,
    onWithdrawEarnings: (bankName: String, targetAccount: String, amountSom: Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Earnings Summary Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Доход сегодня (${state.selectedCity.name})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                )
                Text(
                    text = "${state.driverTodayEarningsSom} ${strings.currencySom}",
                    fontFamily = JetBrainsMono,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Поездок: ${state.driverQuestTripsDone}", style = MaterialTheme.typography.labelMedium, color = UberGreen)
                    Text("На линии: ${state.driverShiftHours} ч", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                    Text(
                        text = if (state.driverShiftPass != DriverShiftPass.NONE) "Комиссия: 0% (Смена)" else "Комиссия: ${state.ownerBankConfig.commissionPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.driverShiftPass != DriverShiftPass.NONE) UberGreen else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Quests & Bonuses Progress Card (made-up bonus; hidden until real quests exist)
        if (com.example.FeatureFlags.DEMO_EXTRAS) Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(strings.questsBonuses, fontWeight = FontWeight.Bold)
                    Text("+800 ${strings.currencySom}", color = UberGreen, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                }
                Text("Выполнено ${state.driverQuestTripsDone} из ${state.driverQuestTarget} поездок на этой неделе", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = { (state.driverQuestTripsDone.toFloat() / state.driverQuestTarget.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = UberBlue
                )
            }
        }

        // 7-Day Weekly Bar Chart
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            val maxWeek = (state.driverWeeklyEarnings.maxOfOrNull { it.second } ?: 5000).coerceAtLeast(1)
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Доход за 7 дней", fontWeight = FontWeight.Bold)
                    Text("${state.driverWeeklyEarnings.sumOf { it.second }} ${strings.currencySom}", fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, color = UberGreen)
                }
                Canvas(modifier = Modifier.fillMaxWidth().height(92.dp)) {
                    val slotW = size.width / state.driverWeeklyEarnings.size
                    val barW = slotW * 0.54f
                    state.driverWeeklyEarnings.forEachIndexed { idx, (_, amount) ->
                        val frac = amount.toFloat() / maxWeek.toFloat()
                        val h = size.height * frac.coerceIn(0.12f, 1f)
                        val left = idx * slotW + (slotW - barW) / 2f
                        drawRoundRect(
                            color = if (idx == state.driverWeeklyEarnings.lastIndex) UberGreen else UberBlue,
                            topLeft = Offset(left, size.height - h),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    state.driverWeeklyEarnings.forEach { (day, _) ->
                        Text(day, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Commission-free shift purchase (needs real payments)
        if (com.example.FeatureFlags.DRIVER_SHIFT_PASSES) DriverShiftPassCard(
            activePass = state.driverShiftPass,
            onSelectPass = onSelectShiftPass
        )

        // Hourly Demand Forecast Chart + Navigate to Peak Zone
        val hourlyForecast = remember(state.selectedCity.name) {
            KgGeoCatalog.hourlyDemandForecast(state.selectedCity)
        }
        // Demand forecast is invented data until there is real order history.
        if (com.example.FeatureFlags.SURGE_PRICING) DriverHourlyDemandChartCard(
            city = state.selectedCity,
            forecast = hourlyForecast
        )

        // Instant Payouts (MBank / Элкарт / O!Деньги) + Ratings & Tips
        // Passengers pay the driver directly (cash / transfer), so there is nothing to withdraw yet.
        if (com.example.FeatureFlags.ONLINE_PAYMENTS) DriverRatingsTipsAndPayoutSection(
            availableBalanceSom = state.driverTodayEarningsSom,
            tips = state.driverTips,
            payouts = state.driverPayouts,
            onWithdrawEarnings = onWithdrawEarnings
        )
    }
}

@Composable
private fun DriverVehicleAndDocsTabContent(
    strings: KgStrings,
    state: KgTaxiUiState,
    viewModel: KgTaxiViewModel,
    onOpenAddMaintenance: () -> Unit
) {
    val distVal = state.fuelDistanceKm.toDoubleOrNull() ?: 0.0
    val consVal = state.fuelConsumptionPer100.toDoubleOrNull() ?: 0.0
    val priceVal = state.fuelPriceSom.toDoubleOrNull() ?: 0.0
    val litersNeeded = (distVal / 100.0) * consVal
    val totalFuelSom = (litersNeeded * priceVal).toInt()
    val perKmSom = if (distVal > 0) totalFuelSom / distVal else 0.0
    val grossShift = state.driverTodayEarningsSom
    val commissionRate = state.ownerBankConfig.commissionPercent / 100.0
    val commissionSom = if (state.driverShiftPass != DriverShiftPass.NONE) state.driverShiftPass.costSom else (grossShift * commissionRate).toInt()
    val netShiftProfit = (grossShift - commissionSom - totalFuelSom).coerceAtLeast(0)
    val netPerHour = if (state.driverShiftHours > 0) (netShiftProfit / state.driverShiftHours).toInt() else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Fuel & Net Shift Profit Calculator
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = UberBlue)
                    Text("${strings.fuelCalculator} и чистая прибыль", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.fuelDistanceKm,
                        onValueChange = { viewModel.updateFuelInputs(it, state.fuelConsumptionPer100, state.fuelPriceSom) },
                        label = { Text("Км") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.fuelConsumptionPer100,
                        onValueChange = { viewModel.updateFuelInputs(state.fuelDistanceKm, it, state.fuelPriceSom) },
                        label = { Text("Л/100км") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.fuelPriceSom,
                        onValueChange = { viewModel.updateFuelInputs(state.fuelDistanceKm, state.fuelConsumptionPer100, it) },
                        label = { Text("Сом/л") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Расход: ${String.format(Locale.US, "%.1f", litersNeeded)} л", fontFamily = JetBrainsMono)
                    Text("Топливо: $totalFuelSom ${strings.currencySom} (${String.format(Locale.US, "%.1f", perKmSom)} с/км)", fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, color = UberBlue)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = UberGreen.copy(alpha = 0.13f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Чистая прибыль смены (${state.driverShiftHours} ч)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("Выручка ${grossShift}с − комиссия ${commissionSom}с − топливо ${totalFuelSom}с", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$netShiftProfit сом", fontFamily = JetBrainsMono, fontWeight = FontWeight.ExtraBold, color = UberGreen)
                            Text("~$netPerHour с/час", fontFamily = JetBrainsMono, style = MaterialTheme.typography.labelSmall, color = UberBlue)
                        }
                    }
                }
            }
        }

        // Vehicle Documents & Insurance (ОСАГО / Техосмотр / Права)
        Text("Документы и страховка (ОСАГО / ТО / Права)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (state.driverDocuments.isEmpty()) {
            Text(
                "Документы проверяются при одобрении заявки водителя. Сроки ОСАГО и техосмотра появятся здесь позже.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.driverDocuments.forEach { doc ->
            val urgent = doc.daysRemaining <= 30
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (urgent) UberRed.copy(alpha = 0.11f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(doc.title, fontWeight = FontWeight.Bold)
                        Text("${doc.number} • до ${doc.validUntil} (${doc.daysRemaining} дн.)", style = MaterialTheme.typography.bodySmall, color = if (urgent) UberRed else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (urgent) {
                        FilledTonalButton(
                            onClick = { viewModel.renewDriverDocument(doc.id) },
                            modifier = Modifier.testTag("renew_doc_${doc.id}")
                        ) {
                            Text("Продлить", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Icon(Icons.Default.Verified, contentDescription = "Valid", tint = UberGreen)
                    }
                }
            }
        }

        // Maintenance Reminders
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(strings.maintenance, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FilledTonalButton(
                onClick = onOpenAddMaintenance,
                modifier = Modifier.testTag("add_maintenance_button")
            ) {
                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("+ ТО")
            }
        }
        state.maintenanceLogs.forEach { item ->
            val leftKm = item.dueMileageKm - item.currentMileageKm
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
                        Text("${item.vehicleName} • ${item.serviceTitle}", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (item.completed) "Выполнено" else "Осталось: ${leftKm.coerceAtLeast(0)} км (при ${item.dueMileageKm} км)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!item.completed && leftKm <= 1500) UberRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Checkbox(
                        checked = item.completed,
                        onCheckedChange = { viewModel.toggleMaintenanceComplete(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DriverProStatusTabContent(
    state: KgTaxiUiState,
    viewModel: KgTaxiViewModel,
    onOpenMeetGreetPlacard: () -> Unit
) {
    val tierName = when {
        state.driverPriorityPoints >= 14 -> "Платина"
        state.driverPriorityPoints >= 9 -> "Золото"
        else -> "Серебро"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Status card (tier, priority, photo control) was invented data: demo only.
        if (com.example.FeatureFlags.DEMO_EXTRAS) Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Уровень KG Pro", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                        Text("$tierName • +${state.driverPriorityPoints} приоритета", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = UberAmber) {
                        Text(
                            text = "★ новый",
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Активность", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
                            Text(
                                text = "${state.driverActivityScore} / 100",
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = if (state.driverActivityScore >= 85) UberGreen else UberAmber
                            )
                            Text("Точка Б открыта при ≥ 80", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f))
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Фотоконтроль", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
                            Text(
                                text = if (state.driverPhotoControlVerified) "Пройден ✓" else "Требуется",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = UberGreen
                            )
                            Text("Toyota Camry 70", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f))
                        }
                    }
                }
            }
        }

        // Yandex Pro Tariff Selector (Choose which classes to receive on the line)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Активные тарифы на линии", fontWeight = FontWeight.Bold)
                Text("Включите классы, по которым хотите получать заказы:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KgGeoCatalog.taxiTariffs.filter { it.id in com.example.FeatureFlags.ENABLED_TARIFF_IDS }.forEach { t ->
                        val active = t.id in state.driverEnabledTariffs
                        FilterChip(
                            selected = active,
                            onClick = { viewModel.toggleDriverTariff(t.id) },
                            label = { Text(t.nameRu) },
                            modifier = Modifier.testTag("driver_tariff_toggle_${t.id}")
                        )
                    }
                    if (com.example.FeatureFlags.isServiceEnabled(com.example.data.model.ServiceType.COURIER)) FilterChip(
                        selected = "courier" in state.driverEnabledTariffs,
                        onClick = { viewModel.toggleDriverTariff("courier") },
                        label = { Text("Курьер") }
                    )
                }
            }
        }

        // Auto-Accept Toggle Card
        if (com.example.FeatureFlags.DEMO_EXTRAS) Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Автоприём заказов", fontWeight = FontWeight.Bold)
                    Text("Автоматически принимать заказы на линии (+2 к приоритету)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.driverAutoAccept,
                    onCheckedChange = { viewModel.toggleDriverAutoAccept() },
                    modifier = Modifier.testTag("driver_pro_auto_accept_switch")
                )
            }
        }

        // Destination Filter ("По пути домой")
        if (com.example.FeatureFlags.DEMO_EXTRAS) DriverDestinationFilterCard(
            enabled = state.driverHomeFilterEnabled,
            targetDistrict = state.driverHomeTargetDistrict,
            onToggleEnabled = { viewModel.setDriverHomeFilter(it, state.driverHomeTargetDistrict) },
            onSelectDistrict = { viewModel.setDriverHomeFilter(true, it) }
        )

        // Coffee break & fatigue monitor
        if (com.example.FeatureFlags.DEMO_EXTRAS) DriverFatigueAndCoffeeBreakCard(
            city = state.selectedCity,
            drivingMinutesToday = state.driverDrivingMinutesToday,
            coffeeBreakActive = state.driverCoffeeBreakActive,
            onToggleCoffeeBreak = viewModel::toggleDriverCoffeeBreak
        )

        // Photo Control & Airport VIP Meet & Greet Placard
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (com.example.FeatureFlags.DEMO_EXTRAS) FilledTonalButton(
                onClick = viewModel::passDriverPhotoControl,
                modifier = Modifier.weight(1f).height(48.dp).testTag("driver_photo_control_button")
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Фотоконтроль", maxLines = 1)
            }
            OutlinedButton(
                onClick = onOpenMeetGreetPlacard,
                modifier = Modifier.weight(1f).height(48.dp).testTag("driver_open_vip_placard_button")
            ) {
                Icon(Icons.Default.FlightLand, contentDescription = null, tint = UberBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Табличка в аэропорту", maxLines = 1)
            }
        }
    }
}
