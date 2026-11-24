package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.util.ExternalMapNavigator
import kotlin.math.roundToInt

/**
 * Authentic Uber Driver / Yandex Pro Swipe-to-Confirm slider to prevent accidental
 * single-tap stage changes while driving. Also supports tap-on-pill for accessibility.
 */
@Composable
fun SwipeToConfirmSlider(
    text: String,
    containerColor: Color,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val thumbSizeDp = 48.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    val padPx = with(density) { 4.dp.toPx() }

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var dragOffsetPx by remember(text) { mutableFloatStateOf(0f) }
    var triggered by remember(text) { mutableStateOf(false) }

    val maxDragPx = (trackWidthPx - thumbSizePx - padPx * 2).coerceAtLeast(1f)
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffsetPx,
        label = "swipeThumb"
    )

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .clickable {
                if (!triggered) {
                    triggered = true
                    onConfirmed()
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Progress fill behind the thumb
        val progressFraction = (animatedOffset / maxDragPx).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = (0.14f + progressFraction * 0.86f).coerceIn(0f, 1f))
                .background(Color.White.copy(alpha = 0.16f))
        )

        // Centered label
        Text(
            text = "» $text",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.4.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = 44.dp, end = 12.dp)
        )

        // Draggable white puck
        Box(
            modifier = Modifier
                .padding(4.dp)
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .size(thumbSizeDp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        if (!triggered) {
                            dragOffsetPx = (dragOffsetPx + delta).coerceIn(0f, maxDragPx)
                        }
                    },
                    onDragStopped = {
                        if (!triggered && dragOffsetPx >= maxDragPx * 0.68f) {
                            triggered = true
                            dragOffsetPx = maxDragPx
                            onConfirmed()
                        } else if (!triggered) {
                            dragOffsetPx = 0f
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = text,
                tint = containerColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Yandex Pro / Uber Driver Back-to-Back ("По цепочке") next-order banner shown during IN_TRIP.
 */
@Composable
fun DriverBackToBackBanner(
    nextOffer: DriverIncomingOrder,
    onAcceptChain: () -> Unit,
    onDeclineChain: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101828),
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, UberGreen, RoundedCornerShape(18.dp))
            .testTag("driver_back_to_back_banner")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(shape = RoundedCornerShape(6.dp), color = UberGreen) {
                        Text(
                            text = "ПО ЦЕПОЧКЕ",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "Рядом с высадкой • ${nextOffer.tariffName}",
                        color = Color.White.copy(alpha = 0.82f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text(
                    text = "+${nextOffer.upfrontNetEarningsSom} с",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Black,
                    color = UberGreen,
                    fontSize = 15.sp
                )
            }
            Text(
                text = "${nextOffer.pickupAddress} → ${nextOffer.dropoffAddress}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDeclineChain,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(40.dp).testTag("driver_chain_decline")
                ) {
                    Text("Не брать", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = onAcceptChain,
                    colors = ButtonDefaults.buttonColors(containerColor = UberGreen, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(40.dp).testTag("driver_chain_accept")
                ) {
                    Text("Взять следующим", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Yandex Pro Commission-Free Shift Pass Card (12h / 24h vs 14% commission).
 */
@Composable
fun DriverShiftPassCard(
    activePass: DriverShiftPass,
    onSelectPass: (DriverShiftPass) -> Unit
) {
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = UberBlue)
                    Text("Покупка смены (0% комиссии)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                if (activePass != DriverShiftPass.NONE) {
                    Surface(shape = RoundedCornerShape(8.dp), color = UberGreen) {
                        Text(
                            text = "0% АКТИВНА",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Text(
                text = "Как в Яндекс Про: выберите смену без комиссии сервиса или платите стандартные 14% с заказа.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DriverShiftPass.entries.forEach { pass ->
                    val selected = activePass == pass
                    Surface(
                        onClick = { onSelectPass(pass) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("driver_shift_pass_${pass.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pass.title,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = pass.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (pass.costSom == 0) "14%" else "${pass.costSom} с",
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (selected) UberGreen else UberBlue
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Uber Earnings Trends / Yandex Pro Hourly Demand Forecast Chart + Navigate to Peak Zone.
 */
@Composable
fun DriverHourlyDemandChartCard(
    city: KgCity,
    forecast: List<HourlyDemandPoint>
) {
    val context = LocalContext.current
    val peak = remember(forecast) { forecast.maxByOrNull { it.demandFraction } ?: forecast.first() }
    val peakZone = city.activeSurgeZones.maxByOrNull { it.multiplier }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text("Прогноз спроса по часам (${city.name})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Пик в ${peak.hourLabel} (${peak.surgeLabel}) • ${peak.hotDistrict}",
                        style = MaterialTheme.typography.bodySmall,
                        color = UberPurple,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(Icons.Default.Insights, contentDescription = null, tint = UberPurple)
            }

            Canvas(modifier = Modifier.fillMaxWidth().height(92.dp)) {
                val count = forecast.size.coerceAtLeast(1)
                val slotW = size.width / count
                val barW = slotW * 0.56f
                forecast.forEachIndexed { idx, pt ->
                    val h = size.height * pt.demandFraction.coerceIn(0.18f, 1f)
                    val left = idx * slotW + (slotW - barW) / 2f
                    val color = when {
                        pt.demandFraction >= 0.85f -> UberPurple
                        pt.isCurrentHour -> UberGreen
                        else -> UberBlue.copy(alpha = 0.75f)
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                forecast.forEach { pt ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = pt.surgeLabel,
                            fontFamily = JetBrainsMono,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (pt.demandFraction >= 0.85f) UberPurple else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(pt.hourLabel, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (peakZone != null) {
                FilledTonalButton(
                    onClick = {
                        ExternalMapNavigator.openTurnByTurnNavigation(
                            context = context,
                            destination = peakZone.center,
                            label = "Зона пика: ${peakZone.name}",
                            preferredProvider = "2GIS"
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("driver_navigate_peak_zone")
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, tint = UberPurple, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Маршрут к зоне пика: ${peakZone.name} (x${peakZone.multiplier})", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Uber Coffee Break & Driving Fatigue Monitor (12h daily limit) + Quick Gas/EV/Cafe chips.
 */
@Composable
fun DriverFatigueAndCoffeeBreakCard(
    city: KgCity,
    drivingMinutesToday: Int,
    coffeeBreakActive: Boolean,
    onToggleCoffeeBreak: () -> Unit
) {
    val context = LocalContext.current
    val maxMinutes = 12 * 60
    val progress = (drivingMinutesToday.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
    val hrs = drivingMinutesToday / 60
    val mins = drivingMinutesToday % 60
    val remMins = (maxMinutes - drivingMinutesToday).coerceAtLeast(0)

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
                    Icon(Icons.Default.LocalCafe, contentDescription = null, tint = if (coffeeBreakActive) UberAmber else UberBlue)
                    Column {
                        Text("Отдых и контроль усталости", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = "За рулём: ${hrs}ч ${mins}м • Осталось ${remMins / 60}ч ${remMins % 60}м из 12ч",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onToggleCoffeeBreak,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (coffeeBreakActive) UberAmber else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.testTag("driver_coffee_break_toggle")
                ) {
                    Text(
                        text = if (coffeeBreakActive) "С паузы" else "Кофе-брейк",
                        color = if (coffeeBreakActive) Color.Black else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                color = if (progress > 0.8f) UberRed else UberGreen,
                trackColor = MaterialTheme.colorScheme.surface
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Triple("АЗС Газпром / Red", Icons.Default.LocalGasStation, GeoPoint(city.center.lat + 0.008, city.center.lng - 0.005)),
                    Triple("Зарядка EV (60 кВт)", Icons.Default.EvStation, GeoPoint(city.center.lat - 0.006, city.center.lng + 0.009)),
                    Triple("Кофейня 24/7", Icons.Default.Coffee, GeoPoint(city.center.lat + 0.004, city.center.lng + 0.006))
                ).forEach { (label, icon, pt) ->
                    AssistChip(
                        onClick = {
                            ExternalMapNavigator.openTurnByTurnNavigation(context, pt, label, "2GIS")
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(icon, contentDescription = null, tint = UberBlue, modifier = Modifier.size(15.dp)) }
                    )
                }
            }
        }
    }
}

/**
 * Driver-side Chat with Passenger inside Active Order (quick Russian/Kyrgyz/English templates + AI translate).
 */
@Composable
fun DriverSidePassengerChatOverlay(
    passengerName: String,
    messages: List<DriverChatMessage>,
    translateTargetLang: String,
    isTranslating: Boolean,
    onSelectTranslateLang: (String) -> Unit,
    onTranslateAll: () -> Unit,
    onSendDriverMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    var input by remember { mutableStateOf("") }
    val driverTemplates = listOf(
        "Подъезжаю, буду через 2 мин",
        "Стою у шлагбаума, включил аварийку",
        "Я на месте, жду вас у входа",
        "Жетип калдым, күтүп турам"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_driver_side_chat")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Column {
                        Text("Чат с пассажиром: $passengerName", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        Text("Быстрые шаблоны + AI перевод RU/KG/EN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f))
                    }
                }
                FilledTonalButton(onClick = onTranslateAll, enabled = !isTranslating) {
                    Text(if (isTranslating) "..." else "AI → $translateTargetLang", style = MaterialTheme.typography.labelSmall)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Перевод:", style = MaterialTheme.typography.labelSmall)
                listOf("RU", "KG", "EN").forEach { code ->
                    FilterChip(
                        selected = translateTargetLang == code,
                        onClick = { onSelectTranslateLang(code) },
                        label = { Text(code, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMeDriver = !msg.fromPassenger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMeDriver) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isMeDriver) UberBlue else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.widthIn(max = 290.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = msg.originalText,
                                    color = if (isMeDriver) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                if (!msg.translatedText.isNullOrBlank()) {
                                    Text(
                                        text = "AI: ${msg.translatedText}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isMeDriver) Color.White.copy(alpha = 0.85f) else UberBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1-Tap Driver Quick Replies
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                driverTemplates.forEach { tpl ->
                    AssistChip(
                        onClick = { onSendDriverMessage(tpl) },
                        label = { Text(tpl, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Написать пассажиру...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("driver_side_chat_input")
                )
                FilledIconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            onSendDriverMessage(input)
                            input = ""
                        }
                    },
                    modifier = Modifier.size(48.dp).testTag("driver_side_chat_send")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
