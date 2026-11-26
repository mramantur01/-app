package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.model.*
import com.example.i18n.KgStrings
import com.example.ui.KgTaxiUiState
import com.example.ui.theme.*
import com.example.util.ExternalMapNavigator
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun DriverLineMapCockpit(
    strings: KgStrings,
    state: KgTaxiUiState,
    onToggleOnline: () -> Unit,
    onAcceptOrder: (DriverIncomingOrder) -> Unit,
    onSkipOrder: (DriverIncomingOrder) -> Unit,
    onAdvanceStage: () -> Unit,
    onCancelAcceptedOrder: () -> Unit,
    onToggleSurgeMap: () -> Unit,
    onToggleAutoAccept: () -> Unit,
    onToggleHomeFilter: () -> Unit,
    onToggleCoffeeBreak: () -> Unit,
    onAcceptBackToBack: () -> Unit,
    onDeclineBackToBack: () -> Unit,
    onOpenDriverPassengerChat: () -> Unit,
    onSelectCity: (KgCity) -> Unit,
    onOpenEarningsTab: () -> Unit,
    onOpenProTab: () -> Unit,
    onOpenVoiceControl: () -> Unit,
    onOpenElqrDialog: () -> Unit,
    onOpenMeetGreetPlacard: () -> Unit,
    onTriggerSos: () -> Unit,
    onToggleTariff: (String) -> Unit = {},
    onBookPreorder: (String) -> Unit = {},
    onReleasePreorder: (String) -> Unit = {},
    onToggleAlertDay: (Int) -> Unit = {},
    onStartPreorder: (com.example.data.firebase.SharedRide) -> Unit = {},
    onCancelWithReason: (String) -> Unit = { onCancelAcceptedOrder() },
    onToggleDemand: () -> Unit = {},
    onToggleLastOrder: () -> Unit = {},
    onToggleHomeMode: () -> Unit = {},
    onAnswerRouteChange: (Boolean) -> Unit = {},
    onSetRadius: (Int) -> Unit = {},
    onSetVoice: (Boolean) -> Unit = {},
    onSetNightMap: (Boolean) -> Unit = {},
    onSetHome: (GeoPoint) -> Unit = {},
    onSetMinRating: (Double) -> Unit = {},
    onSetMinTripKm: (Int) -> Unit = {},
    onSetDeaf: (Boolean) -> Unit = {},
    /** Take an order from the list (or as the next one while on a trip). */
    onTakeOrder: (DriverIncomingOrder) -> Unit = onAcceptOrder,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLineSettings by remember { mutableStateOf(false) }
    var showPreorders by remember { mutableStateOf(false) }
    var showEarnings by remember { mutableStateOf(false) }
    var showCancelReasons by remember { mutableStateOf(false) }
    var showRadar by remember { mutableStateOf(false) }

    // Sort incoming offers so home-bound orders appear first when Destination Filter is active
    val targetKey = state.driverHomeTargetDistrict.substringBefore(" /").trim()
    val prioritizedOffers = remember(state.driverIncomingOrders, state.driverHomeFilterEnabled, targetKey) {
        if (state.driverHomeFilterEnabled) {
            state.driverIncomingOrders.sortedByDescending {
                it.dropoffAddress.contains(targetKey, ignoreCase = true) ||
                    it.pickupAddress.contains(targetKey, ignoreCase = true)
            }
        } else {
            state.driverIncomingOrders
        }
    }
    val currentOffer = prioritizedOffers.firstOrNull()

    // Live 15-second Uber / Yandex Pro offer countdown timer (paused during Coffee Break)
    var offerSecondsLeft by remember(currentOffer?.id, state.driverOnline, state.driverCoffeeBreakActive) { mutableIntStateOf(15) }
    LaunchedEffect(currentOffer?.id, state.driverOnline, state.driverAcceptedOrder?.id, state.driverAutoAccept, state.driverCoffeeBreakActive) {
        if (state.driverOnline && !state.driverCoffeeBreakActive && state.driverAcceptedOrder == null && currentOffer != null) {
            if (state.driverAutoAccept) {
                delay(1800)
                onAcceptOrder(currentOffer)
                return@LaunchedEffect
            }
            offerSecondsLeft = 15
            while (offerSecondsLeft > 0) {
                delay(1000)
                offerSecondsLeft -= 1
            }
            onSkipOrder(currentOffer)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. FULL-BLEED DRIVER LIVE NAVIGATION MAP (Google Maps or Uber/Yandex Pro Vector Map)
        DriverNavigationMapSurface(
            city = state.selectedCity,
            activeOrder = state.driverAcceptedOrder,
            orderStage = state.driverOrderStage,
            currentOffer = if (state.driverOnline && state.driverAcceptedOrder == null) currentOffer else null,
            showSurgeHeatmap = com.example.FeatureFlags.SURGE_PRICING && (state.showSurgeHeatmap || state.driverOnline),
            darkTheme = state.darkTheme || (state.driverNightMapAuto && com.example.util.DriverWorkPrefs.isNight()),
            offlineMode = state.offlineMode,
            heat = if (state.driverShowDemand && state.driverAcceptedOrder == null) state.driverDemandPoints else emptyList(),
            modifier = Modifier.fillMaxSize()
        )

        // 2. FLOATING TOP HUD BAR (Yandex Pro Activity + Uber Earnings Pill + Voice)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .then(if (state.driverOnline || state.driverAcceptedOrder != null) Modifier.statusBarsPadding() else Modifier)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Left: "Уйти с линии" while on the line (the profile is in the bottom menu, not repeated here).
                // Round red power button; first tap shows "Уйти с линии", second tap goes offline.
                var offlineArmed by remember { mutableStateOf(false) }
                LaunchedEffect(offlineArmed) {
                    if (offlineArmed) { kotlinx.coroutines.delay(3000); offlineArmed = false }
                }
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                if (state.driverOnline && state.driverAcceptedOrder == null) {
                    Surface(
                        onClick = { if (offlineArmed) { offlineArmed = false; onToggleOnline() } else offlineArmed = true },
                        shape = CircleShape,
                        color = UberRed,
                        shadowElevation = 6.dp,
                        modifier = Modifier.height(44.dp).animateContentSize().testTag("driver_hud_go_offline")
                    ) {
                        Row(
                            Modifier.then(if (offlineArmed) Modifier.padding(start = 12.dp, end = 14.dp) else Modifier.width(44.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = "Уйти с линии", tint = Color.White, modifier = Modifier.size(22.dp))
                            if (offlineArmed) {
                                Spacer(Modifier.width(6.dp))
                                Text("Уйти с линии", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
                // Where the driver is (by GPS): at the very top edge of the map, next to the power button.
                if (state.driverAcceptedOrder == null && !offlineArmed) {
                    DetectedCityChip(state.selectedCity.name, modifier = Modifier.widthIn(max = 130.dp), onMap = true)
                }
                }

                // Center: "$" — tap and today's earnings appear in its place; tap again to hide.
                // Just the sign, no circle; the earnings show as a small pill.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showEarnings = !showEarnings }
                        .animateContentSize()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .testTag("driver_hud_earnings_pill")
                ) {
                    if (showEarnings) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 4.dp) {
                            Text(
                                "${state.driverTodayEarningsSom} сом",
                                color = com.example.ui.theme.KgTurquoiseDark,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        Text(
                            "$",
                            color = com.example.ui.theme.KgTurquoiseDark,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(Color.White, blurRadius = 8f)
                            )
                        )
                    }
                }

                // Right: line settings (tariffs and city) and, if switched on, voice control
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (com.example.FeatureFlags.VOICE_COMMANDS) DriverRoundHudButton(
                        onClick = onOpenVoiceControl,
                        modifier = Modifier.testTag("driver_voice_command_button")
                    ) { Icon(Icons.Default.Mic, contentDescription = "Голосовое управление", tint = KgTurquoiseDark) }
                    DriverRoundHudButton(
                        onClick = { showLineSettings = true },
                        modifier = Modifier.testTag("driver_line_settings_button")
                    ) { Icon(Icons.Default.Tune, contentDescription = "Тарифы и город", tint = KgTurquoiseDark) }
                }
            }


            // Secondary HUD strip: city switch — only before going on the line.
            if (com.example.FeatureFlags.DEMO_EXTRAS && !state.driverOnline && state.driverAcceptedOrder == null) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Destination Filter Quick Pill ("По пути домой")
                if (com.example.FeatureFlags.DEMO_EXTRAS) {
                Surface(
                    onClick = onToggleHomeFilter,
                    shape = RoundedCornerShape(16.dp),
                    color = if (state.driverHomeFilterEnabled) UberGreen else Color(0xFF141414).copy(alpha = 0.85f),
                    shadowElevation = 3.dp,
                    modifier = Modifier.testTag("driver_hud_home_filter")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text(
                            text = if (state.driverHomeFilterEnabled) "Домой: ${state.driverHomeTargetDistrict}" else "По пути",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Auto-Accept Toggle Pill (Yandex Pro Автоприём)
                Surface(
                    onClick = onToggleAutoAccept,
                    shape = RoundedCornerShape(16.dp),
                    color = if (state.driverAutoAccept) UberBlue else Color(0xFF141414).copy(alpha = 0.85f),
                    shadowElevation = 3.dp,
                    modifier = Modifier.testTag("driver_hud_auto_accept")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.FlashAuto, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text(
                            text = if (state.driverAutoAccept) "Автоприём ВКЛ" else "Автоприём",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Uber Coffee Break Quick Pill ("Пауза / Кофе")
                Surface(
                    onClick = onToggleCoffeeBreak,
                    shape = RoundedCornerShape(16.dp),
                    color = if (state.driverCoffeeBreakActive) UberAmber else Color(0xFF141414).copy(alpha = 0.85f),
                    shadowElevation = 3.dp,
                    modifier = Modifier.testTag("driver_hud_coffee_break")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.LocalCafe,
                            contentDescription = "Coffee Break",
                            tint = if (state.driverCoffeeBreakActive) Color.Black else UberAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (state.driverCoffeeBreakActive) "Пауза ВКЛ" else "Кофе",
                            color = if (state.driverCoffeeBreakActive) Color.Black else Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                }

                if (state.driverShiftPass != DriverShiftPass.NONE) {
                    Surface(
                        onClick = onOpenEarningsTab,
                        shape = RoundedCornerShape(16.dp),
                        color = UberGreen
                    ) {
                        Text(
                            text = "Смена 0%",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                }

                // Surge Zones Pill
                Surface(
                    onClick = onToggleSurgeMap,
                    shape = RoundedCornerShape(16.dp),
                    color = UberPurple.copy(alpha = 0.92f),
                    shadowElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = UberAmber, modifier = Modifier.size(14.dp))
                        Text(
                            text = if (com.example.FeatureFlags.SURGE_PRICING) "${state.selectedCity.name} • x${state.selectedCity.surgeMultiplier}" else state.selectedCity.name,
                            color = Color.White,
                            fontFamily = JetBrainsMono,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Quick City Cycle Pill
                Surface(
                    onClick = {
                        val idx = KgGeoCatalog.cities.indexOfFirst { it.name == state.selectedCity.name }
                        val next = KgGeoCatalog.cities[(idx + 1) % KgGeoCatalog.cities.size]
                        onSelectCity(next)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF141414).copy(alpha = 0.85f)
                ) {
                    Text(
                        text = "Сменить город ↻",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Preorders: small calendar button on the right side of the map (not during an order or an offer).
        if (com.example.FeatureFlags.REAL_RIDES && state.driverAcceptedOrder == null && !(state.driverOnline && currentOffer != null)) {
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DriverDemandFab(on = state.driverShowDemand, count = state.driverDemandPoints.size, onClick = onToggleDemand)
                DriverPreordersFab(state = state, onClick = { showPreorders = true })
            }
        }

        // 3. FLOATING BOTTOM DRIVER COCKPIT PANEL (3 Authentic States: Offline GO / Live Offer / Active Execution)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val activeOrder = state.driverAcceptedOrder
            when {
                // STATE C: ACTIVE ORDER STEP-BY-STEP EXECUTION (Yandex Pro / Uber Driver)
                activeOrder != null -> {
                    val backToBack = state.driverBackToBackOffer
                    if (state.driverOrderStage == DriverOrderStage.IN_TRIP && backToBack != null) {
                        DriverBackToBackBanner(
                            nextOffer = backToBack,
                            onAcceptChain = onAcceptBackToBack,
                            onDeclineChain = onDeclineBackToBack
                        )
                    } else if (state.driverQueuedNextOrder != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = UberGreen,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Следующий заказ в цепочке: ${state.driverQueuedNextOrder.pickupAddress} (+${state.driverQueuedNextOrder.upfrontNetEarningsSom} с)",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DriverLastOrderChip(on = state.driverLastOrder, onClick = onToggleLastOrder)
                        if (com.example.FeatureFlags.REAL_RIDES && state.driverQueuedNextOrder == null) {
                            val near = nextOrdersNear(state, activeOrder)
                            if (near.isNotEmpty()) DriverRadarChip("Следующий рядом (${near.size})") { showRadar = true }
                        }
                    }
                    activeOrder.passengerWishes?.let { w -> DriverWishesNote(w) }
                    DriverActiveOrderExecutionSheet(
                        order = activeOrder,
                        stage = state.driverOrderStage,
                        waitingSeconds = state.driverOrderWaitingSeconds,
                        chatCount = state.driverActiveOrderMessages.size,
                        onAdvanceStage = onAdvanceStage,
                        onCancelOrder = { showCancelReasons = true },
                        onOpenChat = onOpenDriverPassengerChat,
                        onOpenElqr = onOpenElqrDialog,
                        onOpenMeetGreet = onOpenMeetGreetPlacard,
                        onTriggerSos = onTriggerSos
                    )
                }

                // STATE B0: ONLINE COFFEE BREAK PAUSE
                state.driverOnline && state.driverCoffeeBreakActive -> {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 12.dp,
                        modifier = Modifier.fillMaxWidth().testTag("driver_coffee_break_sheet")
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.LocalCafe, contentDescription = null, tint = UberAmber, modifier = Modifier.size(28.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Пауза «Кофе-брейк»", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                    Text("Новые заказы приостановлены, статус линии и приоритет сохранены", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onToggleOnline, modifier = Modifier.height(48.dp)) {
                                    Text("Уйти с линии", color = UberRed)
                                }
                                Button(
                                    onClick = onToggleCoffeeBreak,
                                    colors = ButtonDefaults.buttonColors(containerColor = UberBlue, contentColor = Color.White),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text("Вернуться к заказам", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // STATE B: ONLINE — INCOMING ORDER OFFER CARD WITH 15s COUNTDOWN
                state.driverOnline && currentOffer != null -> {
                    val isHomeMatch = state.driverHomeFilterEnabled && (
                        currentOffer.dropoffAddress.contains(targetKey, ignoreCase = true) ||
                            currentOffer.pickupAddress.contains(targetKey, ignoreCase = true)
                        )
                    DriverIncomingOfferSheet(
                        order = currentOffer,
                        secondsLeft = offerSecondsLeft,
                        isOnTheWayHome = isHomeMatch,
                        autoAcceptOn = state.driverAutoAccept,
                        onAccept = { onAcceptOrder(currentOffer) },
                        onSkip = { onSkipOrder(currentOffer) },
                        onGoOffline = onToggleOnline
                    )
                }

                // STATE A2: ONLINE — waiting for orders. Clean screen, one big button to leave the line.
                state.driverOnline -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DriverLineQuickChips(state = state, onToggleLastOrder = onToggleLastOrder, onToggleHomeMode = onToggleHomeMode, onOpenRadar = { showRadar = true })
                        DriverWaitingForOrdersCard(
                            city = state.selectedCity.name,
                            todayEarningsSom = state.driverTodayEarningsSom,
                            tripsToday = state.driverQuestTripsDone,
                            onGoOffline = onToggleOnline
                        )
                    }
                }

                // STATE A: OFFLINE — ICONIC BIG ROUND UBER "НА ЛИНИЮ" (GO) BUTTON + DEMAND CARD
                else -> {
                    DriverOfflineGo(state = state, onGoOnline = onToggleOnline)
                }
            }
        }

        if (state.driverDeafMode && state.driverOnline && state.driverAcceptedOrder == null && currentOffer != null) {
            DriverOfferFlash(key = currentOffer.id)
        }

        DriverPreordersSidePanel(
            visible = showPreorders,
            state = state,
            onBook = onBookPreorder,
            onRelease = onReleasePreorder,
            onStart = { ride ->
                showPreorders = false
                onStartPreorder(ride)
            },
            onToggleAlertDay = onToggleAlertDay,
            onDismiss = { showPreorders = false }
        )
    }

    if (showCancelReasons) {
        DriverCancelReasonsDialog(
            onPick = { reason ->
                showCancelReasons = false
                onCancelWithReason(reason)
            },
            onDismiss = { showCancelReasons = false }
        )
    }

    if (showRadar) {
        DriverRadarSheet(
            state = state,
            onTake = { o ->
                showRadar = false
                onTakeOrder(o)
            },
            onDismiss = { showRadar = false }
        )
    }

    state.driverRouteRequest?.let { req ->
        DriverRouteRequestDialog(req = req, oldPrice = state.driverAcceptedOrder?.grossFareSom, onAnswer = onAnswerRouteChange)
    }

    if (showLineSettings) {
        DriverLineSettingsSheet(
            state = state,
            onToggleTariff = onToggleTariff,
            onSelectCity = onSelectCity,
            onSetRadius = onSetRadius,
            onSetVoice = onSetVoice,
            onSetNightMap = onSetNightMap,
            onSetHome = onSetHome,
            onSetMinRating = onSetMinRating,
            onSetMinTripKm = onSetMinTripKm,
            onSetDeaf = onSetDeaf,
            onDismiss = { showLineSettings = false }
        )
    }
}

@Composable
private fun PulsingGoOnlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "go_pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "go_scale"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer Uber Blue Radar Halo
        Box(
            modifier = Modifier
                .size(102.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(UberBlue.copy(alpha = 0.24f))
        )
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = UberBlue,
            shadowElevation = 12.dp,
            modifier = modifier
                .size(86.dp)
                .border(3.dp, Color.White, CircleShape)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "GO",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "НА ЛИНИЮ",
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun DriverIncomingOfferSheet(
    order: DriverIncomingOrder,
    secondsLeft: Int,
    isOnTheWayHome: Boolean,
    autoAcceptOn: Boolean,
    onAccept: () -> Unit,
    onSkip: () -> Unit,
    onGoOffline: () -> Unit
) {
    val progress = (secondsLeft / 15f).coerceIn(0f, 1f)
    val urgentColor = if (secondsLeft <= 5) UberRed else UberBlue

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 14.dp,
        modifier = Modifier.fillMaxWidth().testTag("driver_live_offer_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 15-Second Countdown Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = urgentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Header Badges: Tariff, Payment, Surge, Countdown Seconds
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) {
                        Text(
                            text = order.tariffName,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            text = order.paymentMethodLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (isOnTheWayHome) {
                        Surface(shape = RoundedCornerShape(8.dp), color = UberGreen) {
                            Text(
                                text = "По пути",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Text(
                    text = if (autoAcceptOn) "АВТО" else "${secondsLeft}с",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Black,
                    color = urgentColor,
                    fontSize = 15.sp
                )
            }

            // Huge Upfront Net Earnings + Trip Distance/Time (Uber Driver Signature)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "+${order.upfrontNetEarningsSom} сом",
                            fontFamily = JetBrainsMono,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = UberGreen
                        )
                        if (order.surgeLabel != null) {
                            Surface(shape = RoundedCornerShape(6.dp), color = UberPurple) {
                                Text(
                                    text = order.surgeLabel,
                                    color = Color.White,
                                    fontFamily = JetBrainsMono,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Чистыми • Тариф ${order.grossFareSom} с • ${order.passengerName} ${com.example.data.model.ratingLabel(order.passengerRating, order.passengerTrips)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Clean Route A -> B Timeline
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.TripOrigin, contentDescription = null, tint = UberBlue, modifier = Modifier.size(14.dp))
                    Column {
                        Text(
                            text = "Подача ${order.pickupEtaMin} мин (${order.pickupDistanceKm} км)",
                            style = MaterialTheme.typography.labelSmall,
                            color = UberBlue,
                            fontWeight = FontWeight.Bold
                        )
                        Text(order.pickupAddress, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Square, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                    Column {
                        Text(
                            text = "Поездка ${order.durationMin} мин (${order.distanceKm} км)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(order.dropoffAddress, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            order.passengerWishes?.let { w -> DriverWishesNote(w) }

            // Action Buttons: Skip (-4) & Big ACCEPT
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(54.dp).testTag("driver_skip_offer_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Пропустить", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = UberBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(54.dp).testTag("driver_accept_${order.id}")
                ) {
                    Text(
                        text = "ПРИНЯТЬ ЗАКАЗ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

        }
    }
}

@Composable
private fun DriverActiveOrderExecutionSheet(
    order: DriverIncomingOrder,
    stage: DriverOrderStage,
    waitingSeconds: Int,
    chatCount: Int,
    onAdvanceStage: () -> Unit,
    onCancelOrder: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenElqr: () -> Unit,
    onOpenMeetGreet: () -> Unit,
    onTriggerSos: () -> Unit
) {
    val context = LocalContext.current
    val stageTitle = when (stage) {
        DriverOrderStage.EN_ROUTE_TO_PICKUP -> "1/3 • Еду к пассажиру (${order.pickupEtaMin} мин)"
        DriverOrderStage.WAITING_AT_PICKUP -> "2/3 • На месте — ожидание пассажира"
        DriverOrderStage.IN_TRIP -> "3/3 • В пути к точке Б (${order.durationMin} мин)"
    }
    val navDestination = if (stage == DriverOrderStage.EN_ROUTE_TO_PICKUP) order.pickupPoint else order.dropoffPoint
    val navAddress = if (stage == DriverOrderStage.EN_ROUTE_TO_PICKUP) order.pickupAddress else order.dropoffAddress

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 14.dp,
        modifier = Modifier.fillMaxWidth().testTag("driver_active_execution_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Stage Header + SOS Shield
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stageTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = UberBlue)
                    Text(
                        text = "${order.passengerName} ${com.example.data.model.ratingLabel(order.passengerRating, order.passengerTrips)} • ${order.tariffName} • ${order.paymentMethodLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(
                    onClick = onTriggerSos,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = UberRed.copy(alpha = 0.14f)),
                    modifier = Modifier.testTag("driver_sos_102_button")
                ) {
                    Icon(Icons.Default.Shield, contentDescription = "SOS 112", tint = UberRed)
                }
            }

            // Waiting Timer + Passenger 4-Digit PIN Verification during Stage 2
            if (stage == DriverOrderStage.WAITING_AT_PICKUP) {
                val freeLimit = if (!order.meetAndGreetSign.isNullOrBlank()) 45 * 60 else 180
                val left = (freeLimit - waitingSeconds).coerceAtLeast(0)
                val paidMin = ((waitingSeconds - freeLimit).coerceAtLeast(0) + 59) / 60
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (paidMin > 0) UberRed.copy(alpha = 0.14f) else UberGreen.copy(alpha = 0.14f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (paidMin > 0) "Платное ожидание" else "Бесплатное ожидание",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (paidMin > 0) UberRed else UberGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (paidMin > 0) "+${paidMin * 8} сом" else String.format(Locale.US, "%02d:%02d", left / 60, left % 60),
                                fontFamily = JetBrainsMono,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = if (paidMin > 0) UberRed else UberGreen
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("PIN сверка", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                            Text(order.passengerPin, fontFamily = JetBrainsMono, fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }

            // How the PIN check works, so both sides understand it the same way.
            if (stage == DriverOrderStage.WAITING_AT_PICKUP) {
                Text(
                    "PIN-код помогает водителю и пассажиру убедиться, что они нашли друг друга",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("pin_hint_driver")
                )
            }

            // Current Target Address Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (stage == DriverOrderStage.EN_ROUTE_TO_PICKUP) Icons.Default.TripOrigin else Icons.Default.Flag,
                        contentDescription = null,
                        tint = if (stage == DriverOrderStage.EN_ROUTE_TO_PICKUP) UberBlue else UberGreen
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (stage == DriverOrderStage.EN_ROUTE_TO_PICKUP) "Точка подачи (А)" else "Точка назначения (Б)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(navAddress, fontWeight = FontWeight.Bold, maxLines = 2)
                    }
                    Text(
                        text = "+${order.upfrontNetEarningsSom}с",
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.ExtraBold,
                        color = UberGreen
                    )
                }
            }

            // External Turn-by-Turn Navigator Strip (2GIS / Yandex / Google Maps) + ELQR + Meet & Greet
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("2GIS" to "2GIS", "YANDEX" to "Яндекс", "GOOGLE" to "Google").forEach { (prov, title) ->
                    AssistChip(
                        onClick = {
                            ExternalMapNavigator.openTurnByTurnNavigation(
                                context = context,
                                destination = navDestination,
                                label = navAddress,
                                preferredProvider = prov
                            )
                        },
                        label = { Text(title, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Navigation, contentDescription = null, tint = UberBlue, modifier = Modifier.size(14.dp)) },
                        modifier = Modifier.testTag("driver_nav_${prov.lowercase()}")
                    )
                }
                AssistChip(
                    onClick = onOpenChat,
                    label = { Text("Чат ($chatCount)", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Default.Chat, contentDescription = null, tint = UberBlue, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.testTag("driver_open_passenger_chat_button")
                )
                if (order.passengerPhone.isNotBlank()) {
                    val ctx = LocalContext.current
                    AssistChip(
                        onClick = {
                            runCatching {
                                ctx.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${order.passengerPhone}"))
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        label = { Text("Позвонить", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Call, contentDescription = null, tint = UberGreen, modifier = Modifier.size(14.dp)) },
                        modifier = Modifier.testTag("driver_call_passenger_button")
                    )
                }
                // ELQR payment needs online payments; passengers pay cash or by transfer now.
                if (com.example.FeatureFlags.ONLINE_PAYMENTS) AssistChip(
                    onClick = onOpenElqr,
                    label = { Text("ELQR", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.testTag("driver_show_elqr_button")
                )
                if (!order.meetAndGreetSign.isNullOrBlank()) {
                    AssistChip(
                        onClick = onOpenMeetGreet,
                        label = { Text("Табличка", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = UberBlue, modifier = Modifier.size(14.dp)) },
                        modifier = Modifier.testTag("driver_meet_greet_button")
                    )
                }
            }

            // Uber Driver / Yandex Pro Swipe-to-Confirm Primary Action Slider + Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (stage != DriverOrderStage.IN_TRIP) {
                    OutlinedButton(
                        onClick = onCancelOrder,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(56.dp).testTag("driver_cancel_order_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = UberRed)
                    }
                }
                val (ctaText, ctaColor) = when (stage) {
                    DriverOrderStage.EN_ROUTE_TO_PICKUP -> "Свайп: НА МЕСТЕ" to UberBlue
                    DriverOrderStage.WAITING_AT_PICKUP -> "Свайп: ПОЕХАЛИ!" to UberGreen
                    DriverOrderStage.IN_TRIP -> "Свайп: ЗАВЕРШИТЬ (+${order.upfrontNetEarningsSom}с)" to com.example.ui.theme.KgTurquoiseDark
                }
                SwipeToConfirmSlider(
                    text = ctaText,
                    containerColor = ctaColor,
                    onConfirmed = onAdvanceStage,
                    modifier = Modifier.weight(1f).testTag("driver_complete_order_button")
                )
            }
        }
    }
}

@Composable
private fun DriverNavigationMapSurface(
    city: KgCity,
    activeOrder: DriverIncomingOrder?,
    orderStage: DriverOrderStage,
    currentOffer: DriverIncomingOrder?,
    showSurgeHeatmap: Boolean,
    darkTheme: Boolean,
    offlineMode: Boolean,
    heat: List<GeoPoint> = emptyList(),
    modifier: Modifier = Modifier
) {
    val hasValidMapsKey = remember {
        BuildConfig.MAPS_API_KEY.isNotBlank() &&
            !BuildConfig.MAPS_API_KEY.contains("YOUR_GOOGLE_MAPS_API_KEY")
    }
    if (hasValidMapsKey && !offlineMode) {
        val camState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(LatLng(city.center.lat, city.center.lng), 13.5f)
        }
        LaunchedEffect(city.name) {
            camState.position = CameraPosition.fromLatLngZoom(LatLng(city.center.lat, city.center.lng), 13.5f)
        }
        GoogleMap(
            modifier = modifier,
            cameraPositionState = camState,
            properties = MapProperties(isTrafficEnabled = true),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, compassEnabled = false)
        ) {
            if (showSurgeHeatmap) {
                city.activeSurgeZones.forEach { z ->
                    Circle(
                        center = LatLng(z.center.lat, z.center.lng),
                        radius = z.radiusKm * 750.0,
                        fillColor = UberPurple.copy(alpha = 0.24f),
                        strokeColor = UberPurple,
                        strokeWidth = 2f
                    )
                }
            }
            val targetOrder = activeOrder ?: currentOffer
            if (targetOrder != null) {
                Polyline(
                    points = listOf(
                        LatLng(targetOrder.pickupPoint.lat, targetOrder.pickupPoint.lng),
                        LatLng(targetOrder.dropoffPoint.lat, targetOrder.dropoffPoint.lng)
                    ),
                    color = if (orderStage == DriverOrderStage.IN_TRIP) UberGreen else UberBlue,
                    width = 12f
                )
            }
        }
    } else if (!offlineMode) {
        // Free real street map (OpenStreetMap) when there is no Google Maps key.
        val targetOrder = activeOrder ?: currentOffer
        val pins = buildList {
            if (targetOrder != null) {
                add(OsmPin(targetOrder.pickupPoint, "A", 0xFF0097A7.toInt(), "Подача: ${targetOrder.pickupAddress}"))
                add(OsmPin(targetOrder.dropoffPoint, "B", 0xFF1F2D30.toInt(), "Куда: ${targetOrder.dropoffAddress}"))
            }
        }
        OsmMapLayer(
            center = city.center,
            centerKey = city.name,
            pins = pins,
            route = if (targetOrder != null) listOf(targetOrder.pickupPoint, targetOrder.dropoffPoint) else emptyList(),
            routeColor = if (orderStage == DriverOrderStage.IN_TRIP) 0xFF05944F.toInt() else 0xFF0097A7.toInt(),
            darkTheme = darkTheme,
            fitTo = remember(targetOrder?.id) { targetOrder?.let { listOf(it.pickupPoint, it.dropoffPoint) } ?: emptyList() },
            onTap = null,
            modifier = modifier,
            heat = heat
        )
    } else {
        // High-Craft Uber Driver / Yandex Pro Vector Navigation Map Canvas
        val bgBase = if (darkTheme) Color(0xFF141820) else Color(0xFFEAF0F6)
        val roadMinor = if (darkTheme) Color(0xFF232B38) else Color(0xFFFFFFFF)
        val roadMajor = if (darkTheme) Color(0xFF2F3A4D) else Color(0xFFD6E0EC)
        val blockFill = if (darkTheme) Color(0xFF1A202B) else Color(0xFFDFE7F0)
        val parkFill = if (darkTheme) Color(0xFF172E26) else Color(0xFFCDEBD7)

        val pulseAnim = rememberInfiniteTransition(label = "driver_radar")
        val radarRadius by pulseAnim.animateFloat(
            initialValue = 18f,
            targetValue = 46f,
            animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart),
            label = "radar"
        )

        Box(modifier = modifier.background(bgBase)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Urban blocks & parks (Ala-Too / Oak Park / Bishkek grid style)
                drawRect(color = parkFill, topLeft = Offset(w * 0.08f, h * 0.14f), size = androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.16f))
                drawRect(color = blockFill, topLeft = Offset(w * 0.42f, h * 0.20f), size = androidx.compose.ui.geometry.Size(w * 0.48f, h * 0.18f))
                drawRect(color = parkFill, topLeft = Offset(w * 0.60f, h * 0.46f), size = androidx.compose.ui.geometry.Size(w * 0.30f, h * 0.14f))

                // Street grid
                for (i in 1..7) {
                    val y = h * i / 8f
                    drawLine(roadMinor, Offset(0f, y), Offset(w, y), strokeWidth = if (i == 3 || i == 5) 14f else 6f)
                }
                for (i in 1..5) {
                    val x = w * i / 6f
                    drawLine(roadMinor, Offset(x, 0f), Offset(x, h), strokeWidth = if (i == 2 || i == 4) 14f else 6f)
                }
                // Major avenue diagonal (Chui / Aitmatov style)
                drawLine(roadMajor, Offset(0f, h * 0.38f), Offset(w, h * 0.32f), strokeWidth = 20f)
                drawLine(roadMajor, Offset(w * 0.34f, 0f), Offset(w * 0.42f, h), strokeWidth = 18f)

                // Glowing Yandex Pro / Uber Purple Surge Demand Zones
                if (showSurgeHeatmap) {
                    val zoneOffsets = listOf(
                        Offset(w * 0.30f, h * 0.34f) to 110f,
                        Offset(w * 0.72f, h * 0.30f) to 95f,
                        Offset(w * 0.56f, h * 0.48f) to 85f
                    )
                    zoneOffsets.forEach { (center, rad) ->
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(UberPurple.copy(alpha = 0.44f), UberPurple.copy(alpha = 0.10f), Color.Transparent),
                                center = center,
                                radius = rad
                            ),
                            radius = rad,
                            center = center
                        )
                    }
                }

                // Active / Offer Route Line on Map
                val driverPos = Offset(w * 0.46f, h * 0.42f)
                val pickupPos = Offset(w * 0.28f, h * 0.28f)
                val dropoffPos = Offset(w * 0.78f, h * 0.34f)

                val focusOrder = activeOrder ?: currentOffer
                if (focusOrder != null) {
                    val routeColor = if (activeOrder != null && orderStage == DriverOrderStage.IN_TRIP) UberGreen else UberBlue
                    val path = Path().apply {
                        if (activeOrder != null && orderStage == DriverOrderStage.IN_TRIP) {
                            moveTo(driverPos.x, driverPos.y)
                            quadraticTo(w * 0.62f, h * 0.28f, dropoffPos.x, dropoffPos.y)
                        } else {
                            moveTo(driverPos.x, driverPos.y)
                            quadraticTo(w * 0.36f, h * 0.34f, pickupPos.x, pickupPos.y)
                            quadraticTo(w * 0.52f, h * 0.22f, dropoffPos.x, dropoffPos.y)
                        }
                    }
                    drawPath(path = path, color = routeColor, style = Stroke(width = 11f, cap = StrokeCap.Round))
                    drawCircle(color = UberBlue, radius = 12f, center = pickupPos)
                    drawCircle(color = Color.White, radius = 5f, center = pickupPos)
                    drawCircle(color = Color.Black, radius = 12f, center = dropoffPos)
                    drawCircle(color = UberGreen, radius = 5f, center = dropoffPos)
                }

                // Driver GPS Navigation Chevron Puck + Pulse
                drawCircle(
                    color = UberBlue.copy(alpha = (1f - radarRadius / 48f).coerceIn(0f, 0.35f)),
                    radius = radarRadius,
                    center = driverPos
                )
                drawCircle(color = Color.White, radius = 19f, center = driverPos)
                drawCircle(color = UberBlue, radius = 15f, center = driverPos)
                val arrow = Path().apply {
                    moveTo(driverPos.x, driverPos.y - 9f)
                    lineTo(driverPos.x + 7f, driverPos.y + 6f)
                    lineTo(driverPos.x, driverPos.y + 2f)
                    lineTo(driverPos.x - 7f, driverPos.y + 6f)
                    close()
                }
                drawPath(arrow, color = Color.White)
            }
        }
    }
}


/** Driver is on the line and waiting: nothing else on screen, so it is easy to use while driving. */
/** Round white button for the top bar. */
@Composable
private fun DriverRoundHudButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White, shadowElevation = 6.dp, modifier = modifier.size(46.dp)) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** Offline: just the big round GO over the map and a small status line under it. */
@Composable
private fun DriverOfflineGo(state: KgTaxiUiState, onGoOnline: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            onClick = onGoOnline,
            shape = CircleShape,
            color = com.example.ui.theme.KgTurquoise,
            shadowElevation = 10.dp,
            modifier = Modifier.size(104.dp).testTag("driver_online_toggle_button")
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("GO", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text("В путь", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 4.dp, modifier = Modifier.testTag("driver_offline_card")) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF9AA5A7)))
                Text(
                    "Не на линии • ${state.selectedCity.name}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF263238)
                )
            }
        }
        val rest = state.driverRestUntil?.takeIf { it > System.currentTimeMillis() }
        val limit = state.appConfig.debtLimitSom
        val warn = when {
            state.registration?.type == "driver" && (state.registration.photoCheckAt ?: Long.MAX_VALUE) < System.currentTimeMillis() - com.example.ui.PHOTO_CHECK_DAYS * 24 * 3600_000L ->
                "Пройдите фотоконтроль во вкладке «Авто»"
            rest != null -> "Отдых после 12 часов на линии до " + java.text.SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(rest))
            limit > 0 && state.driverBalance.debtSom > limit -> "Долг сервису ${state.driverBalance.debtSom} сом — оплатите, чтобы выйти на линию"
            else -> null
        }
        if (warn != null) {
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFFF3E0), shadowElevation = 4.dp, modifier = Modifier.testTag("driver_offline_warning")) {
                Text(warn, color = Color(0xFFE65100), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
    }
}

/** Round calendar button on the right of the map with the number of free pre-orders. */
@Composable
private fun DriverPreordersFab(state: KgTaxiUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val free = state.driverOpenPreorders.size
    val mine = state.driverBookedPreorders.size
    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.size(54.dp).testTag("driver_preorders_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Event, contentDescription = "Предзаказы", tint = KgTurquoiseDark, modifier = Modifier.size(26.dp))
            }
        }
        val badge = free + mine
        if (badge > 0) {
            Surface(
                shape = CircleShape,
                color = UberRed,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 5.dp)) {
                    Text("$badge", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private val weekDays = listOf(
    java.util.Calendar.MONDAY to "Пн", java.util.Calendar.TUESDAY to "Вт", java.util.Calendar.WEDNESDAY to "Ср",
    java.util.Calendar.THURSDAY to "Чт", java.util.Calendar.FRIDAY to "Пт", java.util.Calendar.SATURDAY to "Сб",
    java.util.Calendar.SUNDAY to "Вс"
)

/** Pre-orders slide in from the right: alert days, my pre-orders and free ones. */
@Composable
private fun DriverPreordersSidePanel(
    visible: Boolean,
    state: KgTaxiUiState,
    onBook: (String) -> Unit,
    onRelease: (String) -> Unit,
    onStart: (com.example.data.firebase.SharedRide) -> Unit,
    onToggleAlertDay: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    if (visible) androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = onDismiss)
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = androidx.compose.animation.slideInHorizontally { it },
            exit = androidx.compose.animation.slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.86f).testTag("driver_preorders_panel")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Event, contentDescription = null, tint = KgTurquoiseDark)
                        Spacer(Modifier.width(8.dp))
                        Text("Предзаказы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Закрыть") }
                    }

                    // Alert days: a notification comes when a pre-order for one of these days appears.
                    Surface(shape = RoundedCornerShape(16.dp), color = KgTurquoise.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = KgTurquoiseDark, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Уведомлять о предзаказах на дни", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                                weekDays.forEach { (day, label) ->
                                    val on = day in state.driverPreorderAlertDays
                                    Surface(
                                        onClick = { onToggleAlertDay(day) },
                                        shape = CircleShape,
                                        color = if (on) KgTurquoise else Color.White,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (on) KgTurquoise else Color(0xFFCFD8DC)),
                                        modifier = Modifier.weight(1f).aspectRatio(1f).testTag("preorder_alert_day_$day")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else Color(0xFF455A64))
                                        }
                                    }
                                }
                            }
                            Text(
                                if (state.driverPreorderAlertDays.isEmpty()) "Отметьте дни — пришлём уведомление, когда появится заказ."
                                else "Пришлём уведомление, когда появится предзаказ на отмеченный день.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text("Мои предзаказы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (state.driverBookedPreorders.isEmpty()) {
                        Text(
                            "Пока нет. Возьмите заказ из списка ниже.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.driverBookedPreorders.forEach { r ->
                        PreorderRow(r) {
                            Button(onClick = { onStart(r) }, modifier = Modifier.testTag("preorder_start_${r.id}")) {
                                Text("Начать")
                            }
                            if (r.passengerPhone.isNotBlank()) {
                                OutlinedButton(onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${r.passengerPhone}")))
                                    }
                                }) { Icon(Icons.Default.Call, contentDescription = "Позвонить пассажиру") }
                            }
                            TextButton(onClick = { onRelease(r.id) }) { Text("Отказаться") }
                        }
                    }
                    HorizontalDivider()
                    Text("Свободные • ${state.selectedCity.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (state.driverOpenPreorders.isEmpty()) {
                        Text(
                            "Сейчас свободных предзаказов нет.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.driverOpenPreorders.forEach { r ->
                        PreorderRow(r) {
                            Button(
                                onClick = { onBook(r.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = KgTurquoise),
                                modifier = Modifier.testTag("preorder_book_${r.id}")
                            ) { Text("Взять") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreorderRow(r: com.example.data.firebase.SharedRide, actions: @Composable RowScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                r.scheduledAtMillis?.let { com.example.util.formatDeparture(it) }.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = UberBlue
            )
            Text("${r.city} → ${r.dropoffAddress}", fontWeight = FontWeight.SemiBold)
            Text(
                "Откуда: ${r.pickupAddress}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                listOfNotNull(r.passengerName, if (r.serviceType == "INTERCITY") "${r.seats ?: 1} мест." else r.tariffName, "${r.priceSom} сом").joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
                content = actions
            )
        }
    }
}

@Composable
private fun DriverWaitingForOrdersCard(
    city: String,
    todayEarningsSom: Int,
    tripsToday: Int,
    onGoOffline: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth().testTag("driver_waiting_card")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(UberGreen)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Вы на линии", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "$city • ищем заказы рядом с вами",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = UberGreen)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Сегодня", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$todayEarningsSom сом", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Поездок", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$tripsToday", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Before going on the line: which tariffs to take and which city to work in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverLineSettingsSheet(
    state: KgTaxiUiState,
    onToggleTariff: (String) -> Unit,
    onSelectCity: (KgCity) -> Unit,
    onSetRadius: (Int) -> Unit = {},
    onSetVoice: (Boolean) -> Unit = {},
    onSetNightMap: (Boolean) -> Unit = {},
    onSetHome: (GeoPoint) -> Unit = {},
    onSetMinRating: (Double) -> Unit = {},
    onSetMinTripKm: (Int) -> Unit = {},
    onSetDeaf: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    var pickHome by remember { mutableStateOf(false) }
    if (pickHome) {
        MapPointPickerDialog(
            title = "Где ваш дом?",
            start = state.driverHomePoint ?: state.selectedCity.center,
            pinColor = KgTurquoise,
            onPicked = { p -> pickHome = false; onSetHome(p) },
            onDismiss = { pickHome = false }
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Тарифы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Включите классы, по которым хотите получать заказы",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            KgGeoCatalog.taxiTariffs.filter { it.id in com.example.FeatureFlags.ENABLED_TARIFF_IDS }.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { t ->
                        val on = t.id in state.driverEnabledTariffs
                        Surface(
                            onClick = { onToggleTariff(t.id) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (on) UberBlue else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f).height(56.dp).testTag("line_tariff_${t.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    t.nameRu,
                                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            HorizontalDivider()
            Text("Город", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Определяется по геолокации: заказы приходят там, где вы сейчас.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            com.example.ui.components.DetectedCityChip(state.selectedCity.name)
            HorizontalDivider()
            Text("Радиус подачи", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Заказы, до которых ехать не дальше выбранного", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "1 км", 3 to "3 км", 5 to "5 км", 10 to "10 км", 0 to "Любой").forEach { (km, label) ->
                    FilterChip(
                        selected = state.driverPickupRadiusKm == km,
                        onClick = { onSetRadius(km) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("line_radius_$km")
                    )
                }
            }
            HorizontalDivider()
            Text("Длина поездки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Любая", 3 to "от 3 км", 5 to "от 5 км", 10 to "от 10 км").forEach { (km, label) ->
                    FilterChip(selected = state.driverMinTripKm == km, onClick = { onSetMinTripKm(km) }, label = { Text(label) }, modifier = Modifier.testTag("line_min_trip_$km"))
                }
            }
            HorizontalDivider()
            Text("Рейтинг пассажира", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Не показывать заказы от пассажиров с рейтингом ниже. Новые пассажиры видны всегда.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.0 to "Любой", 4.0 to "4.0+", 4.5 to "4.5+", 4.8 to "4.8+").forEach { (v, label) ->
                    FilterChip(selected = state.driverMinPassengerRating == v, onClick = { onSetMinRating(v) }, label = { Text(label) })
                }
            }
            HorizontalDivider()
            Text("Дом", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (state.driverHomeAddress.isBlank()) "Укажите адрес — включайте «Домой» 2 раза в день и берите заказы по пути к дому."
                else state.driverHomeAddress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { pickHome = true }, modifier = Modifier.fillMaxWidth().testTag("line_set_home")) {
                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state.driverHomePoint == null) "Указать дом на карте" else "Изменить адрес дома")
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Голосовые подсказки", fontWeight = FontWeight.Bold)
                    Text("«Новый заказ, подача 5 минут, 250 сом»", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.driverVoiceAnnounce, onCheckedChange = onSetVoice, modifier = Modifier.testTag("line_voice"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Я плохо слышу", fontWeight = FontWeight.Bold)
                    Text("Новый заказ мигает экраном; пассажиры пишут в чат, а не звонят", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.driverDeafMode, onCheckedChange = onSetDeaf, modifier = Modifier.testTag("line_deaf"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ночная карта", fontWeight = FontWeight.Bold)
                    Text("Тёмная карта с 20:00 до 6:00 — меньше слепит", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.driverNightMapAuto, onCheckedChange = onSetNightMap, modifier = Modifier.testTag("line_night_map"))
            }
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Готово", fontWeight = FontWeight.Bold) }
        }
    }
}


/** Round "demand" button: shows where passengers are waiting right now. */
@Composable
private fun DriverDemandFab(on: Boolean, count: Int, onClick: () -> Unit) {
    Box {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (on) Color(0xFFFF6D00) else Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.size(54.dp).testTag("driver_demand_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.LocalFireDepartment, contentDescription = "Карта спроса",
                    tint = if (on) Color.White else Color(0xFFFF6D00), modifier = Modifier.size(26.dp))
            }
        }
        if (count > 0) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFFF6D00),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 5.dp)) {
                    Text("$count", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** On the line: time on the line, "Последний заказ" and "Домой". */
@Composable
private fun DriverLineQuickChips(state: KgTaxiUiState, onToggleLastOrder: () -> Unit, onToggleHomeMode: () -> Unit, onOpenRadar: () -> Unit = {}) {
    val h = state.driverOnlineMinutes24h / 60
    val m = state.driverOnlineMinutes24h % 60
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 4.dp) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = if (state.driverOnlineMinutes24h >= 11 * 60) UberRed else KgTurquoiseDark, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${h} ч ${m} мин из 12", style = MaterialTheme.typography.labelLarge, color = Color(0xFF263238))
            }
        }
        if (state.driverRadarOrders.isNotEmpty()) DriverRadarChip("Все заказы (${state.driverRadarOrders.size})", onOpenRadar)
        DriverLastOrderChip(on = state.driverLastOrder, onClick = onToggleLastOrder)
        Surface(
            onClick = onToggleHomeMode,
            shape = RoundedCornerShape(18.dp),
            color = if (state.driverHomeModeOn) KgTurquoise else Color.White,
            shadowElevation = 4.dp,
            modifier = Modifier.testTag("driver_home_mode")
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Home, contentDescription = null, tint = if (state.driverHomeModeOn) Color.White else KgTurquoiseDark, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (state.driverHomeModeOn) "Домой: вкл" else "Домой (${state.driverHomeUsesLeft})",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.driverHomeModeOn) Color.White else Color(0xFF263238)
                )
            }
        }
    }
}

@Composable
private fun DriverLastOrderChip(on: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (on) UberRed else Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier.testTag("driver_last_order")
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FlagCircle, contentDescription = null, tint = if (on) Color.White else UberRed, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                if (on) "Последний заказ ✓" else "Последний заказ",
                style = MaterialTheme.typography.labelLarge,
                color = if (on) Color.White else Color(0xFF263238)
            )
        }
    }
}

/** Why the driver cancels an accepted order. The owner sees these reasons. */
@Composable
private fun DriverCancelReasonsDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Почему отменяете?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DRIVER_CANCEL_REASONS.forEach { r ->
                    OutlinedButton(onClick = { onPick(r) }, modifier = Modifier.fillMaxWidth().testTag("cancel_reason_${r.hashCode()}")) {
                        Text(r, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Не отменять") } }
    )
}

/** Passenger wants another address (and maybe a stop): the driver sees all of it and answers yes or no. */
@Composable
private fun DriverRouteRequestDialog(req: RouteRequest, oldPrice: Int?, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Default.AltRoute, contentDescription = null, tint = KgTurquoiseDark) },
        title = { Text("Пассажир хочет изменить маршрут") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!req.stopAddress.isNullOrBlank()) {
                    Text("Остановка по пути", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(req.stopAddress, fontWeight = FontWeight.SemiBold)
                }
                Text("Новый адрес", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(req.dropoffAddress, fontWeight = FontWeight.SemiBold)
                Text("Новая цена", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    (if (oldPrice != null) "$oldPrice → " else "") + "${req.priceSom} сом",
                    fontSize = 22.sp, fontWeight = FontWeight.Black, color = KgTurquoiseDark
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAnswer(true) },
                colors = ButtonDefaults.buttonColors(containerColor = KgTurquoise),
                modifier = Modifier.testTag("route_request_yes")
            ) { Text("Да, поеду") }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(false) }, modifier = Modifier.testTag("route_request_no")) { Text("Нет") }
        }
    )
}


/** Passenger's wishes and special needs, shown to the driver. */
@Composable
private fun DriverWishesNote(text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFFF8E1), modifier = Modifier.fillMaxWidth().testTag("driver_passenger_wishes")) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF9A825), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5D4037))
        }
    }
}


/** Open orders whose pickup is close to where the current trip ends (to take next). */
private fun nextOrdersNear(state: KgTaxiUiState, active: DriverIncomingOrder): List<DriverIncomingOrder> =
    state.driverRadarOrders.filter { it.id != active.id && KgGeoCatalog.haversineKm(active.dropoffPoint, it.pickupPoint) <= 3.0 }
        .sortedBy { KgGeoCatalog.haversineKm(active.dropoffPoint, it.pickupPoint) }

@Composable
private fun DriverRadarChip(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = UberBlue, shadowElevation = 4.dp, modifier = Modifier.testTag("driver_radar_chip")) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FormatListBulleted, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

/** All open orders to choose from; during a trip — the ones near the drop-off, to take next. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriverRadarSheet(state: KgTaxiUiState, onTake: (DriverIncomingOrder) -> Unit, onDismiss: () -> Unit) {
    val active = state.driverAcceptedOrder
    val list = if (active != null) nextOrdersNear(state, active) else state.driverRadarOrders.sortedBy { it.pickupDistanceKm }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(if (active != null) "Следующий заказ рядом с высадкой" else "Заказы рядом", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            if (active != null) Text("Заказ начнётся сразу после текущей поездки. Пассажир увидит, что вы приедете после неё.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (list.isEmpty()) Text("Сейчас подходящих заказов нет.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.forEach { o ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("+${o.upfrontNetEarningsSom} сом", fontWeight = FontWeight.Black, color = UberGreen, modifier = Modifier.weight(1f))
                            Text(o.tariffName, style = MaterialTheme.typography.labelMedium)
                        }
                        Text("Подача ${o.pickupDistanceKm} км • ${o.passengerName} ${ratingLabel(o.passengerRating, o.passengerTrips)}", style = MaterialTheme.typography.bodySmall)
                        Text(o.pickupAddress, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("→ ${o.dropoffAddress}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        o.passengerWishes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8D6E00)) }
                        Button(
                            onClick = { onTake(o) },
                            colors = ButtonDefaults.buttonColors(containerColor = KgTurquoise),
                            modifier = Modifier.align(Alignment.End).testTag("radar_take_${o.id}")
                        ) { Text(if (active != null) "Взять следующим" else "Взять") }
                    }
                }
            }
        }
    }
}


/** Visual alert for deaf drivers: the screen edge flashes when a new order comes. */
@Composable
private fun DriverOfferFlash(key: String) {
    var flashes by remember(key) { mutableIntStateOf(6) }
    LaunchedEffect(key) {
        while (flashes > 0) { delay(350); flashes-- }
    }
    if (flashes > 0 && flashes % 2 == 0) {
        Box(Modifier.fillMaxSize().border(10.dp, Color(0xFFFFC107)).testTag("driver_offer_flash"))
    }
}
