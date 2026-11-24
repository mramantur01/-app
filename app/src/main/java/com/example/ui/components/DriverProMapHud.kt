package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
            showSurgeHeatmap = state.showSurgeHeatmap || state.driverOnline,
            darkTheme = state.darkTheme,
            offlineMode = state.offlineMode,
            modifier = Modifier.fillMaxSize()
        )

        // 2. FLOATING TOP HUD BAR (Yandex Pro Activity + Uber Earnings Pill + Voice)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Yandex Pro Activity & Rating Pill
                Surface(
                    onClick = onOpenProTab,
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF121212).copy(alpha = 0.92f),
                    shadowElevation = 6.dp,
                    modifier = Modifier.testTag("driver_hud_activity_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = "Activity",
                            tint = if (state.driverActivityScore >= 85) UberGreen else UberAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${state.driverActivityScore}",
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text("•", color = Color.White.copy(alpha = 0.45f))
                        Text(
                            text = "★ новый",
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            color = UberAmber,
                            fontSize = 13.sp
                        )
                    }
                }

                // Center: Iconic Uber Driver Earnings Pill
                Surface(
                    onClick = onOpenEarningsTab,
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
                        .testTag("driver_hud_earnings_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${state.driverTodayEarningsSom} с",
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "Open earnings",
                            tint = UberGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Right: Hands-Free Voice Control Button
                Surface(
                    onClick = onOpenVoiceControl,
                    shape = CircleShape,
                    color = Color(0xFF121212).copy(alpha = 0.92f),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(44.dp).testTag("driver_voice_command_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Mic, contentDescription = "Голосовое управление", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Secondary HUD strip: Quick City Switcher + Home Filter + Auto-Accept
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Destination Filter Quick Pill ("По пути домой")
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
                    DriverActiveOrderExecutionSheet(
                        order = activeOrder,
                        stage = state.driverOrderStage,
                        waitingSeconds = state.driverOrderWaitingSeconds,
                        chatCount = state.driverActiveOrderMessages.size,
                        onAdvanceStage = onAdvanceStage,
                        onCancelOrder = onCancelAcceptedOrder,
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

                // STATE A: OFFLINE — ICONIC BIG ROUND UBER "НА ЛИНИЮ" (GO) BUTTON + DEMAND CARD
                else -> {
                    PulsingGoOnlineButton(
                        onClick = onToggleOnline,
                        modifier = Modifier.testTag("driver_online_toggle_button")
                    )

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 10.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Вы не на линии",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "${state.selectedCity.name} • Тарифов: ${state.driverEnabledTariffs.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = UberGreen.copy(alpha = 0.14f),
                                    modifier = Modifier.clickable(onClick = onOpenEarningsTab)
                                ) {
                                    Text(
                                        text = "Квест ${state.driverQuestTripsDone}/${state.driverQuestTarget}",
                                        fontFamily = JetBrainsMono,
                                        color = UberGreen,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Hot Surge Zones Strip right on Offline Sheet (like Yandex Pro)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                state.selectedCity.activeSurgeZones.forEach { z ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = UberPurple.copy(alpha = 0.12f),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = z.name.substringBefore(" "),
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "x${z.multiplier}",
                                                fontFamily = JetBrainsMono,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = UberPurple,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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
                        text = "Чистыми • Тариф ${order.grossFareSom} с • ${order.passengerName} ★ ${order.passengerRating}",
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
                        Text("-4 актр.", style = MaterialTheme.typography.labelSmall, color = UberRed)
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

            // Go Offline compact footer
            TextButton(
                onClick = onGoOffline,
                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("driver_go_offline_button")
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = UberRed, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Уйти с линии", color = UberRed, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
                        text = "${order.passengerName} ★ ${order.passengerRating} • ${order.tariffName} • ${order.paymentMethodLabel}",
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
                AssistChip(
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
                    DriverOrderStage.IN_TRIP -> "Свайп: ЗАВЕРШИТЬ (+${order.upfrontNetEarningsSom}с)" to Color(0xFF111111)
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
