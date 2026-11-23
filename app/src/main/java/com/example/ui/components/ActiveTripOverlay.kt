package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.LocalTripEntity
import com.example.data.model.ActiveRideState
import com.example.data.model.DisplayCurrency
import com.example.data.model.ServiceType
import com.example.data.model.TripStatus
import com.example.i18n.KgStrings
import com.example.ui.theme.*
import com.example.util.ExternalMapNavigator

@Composable
fun ActiveTripCard(
    strings: KgStrings,
    ride: ActiveRideState,
    displayCurrency: DisplayCurrency,
    chatMessageCount: Int,
    onOpenDriverChat: () -> Unit,
    onTriggerSos: () -> Unit,
    onTriggerRideCheck: () -> Unit,
    onDismissRideCheck: () -> Unit,
    onEditDestination: (String) -> Unit,
    onCompleteNow: () -> Unit,
    onCancelRide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEditDestDialog by remember { mutableStateOf(false) }
    var newDestText by remember(ride.dropoffAddress) { mutableStateOf(ride.dropoffAddress) }

    val statusText = if (ride.serviceType == ServiceType.FOOD) {
        when (ride.status) {
            TripStatus.SEARCHING -> "Заказ отправлен в ресторан..."
            TripStatus.ASSIGNED -> "Ресторан готовит ваш заказ..."
            TripStatus.ARRIVED -> "Курьер забрал заказ и выехал!"
            TripStatus.IN_PROGRESS -> "Курьер в пути • ${(ride.progress * 100).toInt()}%"
            TripStatus.COMPLETED -> "Еда доставлена!"
            TripStatus.CANCELLED -> "Заказ отменён"
        }
    } else {
        when (ride.status) {
            TripStatus.SEARCHING -> "Ищем ближайшего водителя..."
            TripStatus.ASSIGNED -> "Водитель в пути • ~${ride.driverEtaMin} ${strings.minUnit}"
            TripStatus.ARRIVED -> "Водитель прибыл на точку подачи!"
            TripStatus.IN_PROGRESS -> "В поездке • ${(ride.progress * 100).toInt()}%"
            TripStatus.COMPLETED -> "Поездка завершена"
            TripStatus.CANCELLED -> "Отменено"
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // RideCheck Safety Alert Banner
            if (ride.rideCheckAlert) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = UberAmber.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = UberRed)
                            Text(
                                text = strings.rideCheckTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onDismissRideCheck,
                                colors = ButtonDefaults.buttonColors(containerColor = UberGreen, contentColor = Color.White),
                                modifier = Modifier.weight(1f).testTag("ridecheck_ok_button")
                            ) {
                                Text(strings.rideCheckAllGood)
                            }
                            Button(
                                onClick = {
                                    val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:102"))
                                    context.startActivity(dial)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = UberRed, contentColor = Color.White),
                                modifier = Modifier.weight(1f).testTag("ridecheck_sos_button")
                            ) {
                                Text(strings.sosEmergency)
                            }
                        }
                    }
                }
            }

            // Status Header + 4-Digit Safety PIN
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${ride.tariffName} • ${displayCurrency.formatSom(ride.totalPriceSom)}" +
                            if (ride.splitCount > 1) " (÷${ride.splitCount})" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 4-Digit Safety PIN Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "PIN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )
                        Text(
                            text = ride.safetyPin,
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("safety_pin_value")
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { ride.progress.coerceIn(0.05f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (ride.serviceType == ServiceType.FOOD) UberGreen else UberBlue,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Free / Paid Waiting Countdown Timer when driver has arrived
            if (ride.status == TripStatus.ARRIVED) {
                val freeLimit = if (!ride.meetAndGreetSignText.isNullOrBlank()) 45 * 60 else 180
                val remaining = (freeLimit - ride.waitingSeconds).coerceAtLeast(0)
                val paidMin = ((ride.waitingSeconds - freeLimit).coerceAtLeast(0) + 59) / 60
                val mm = remaining / 60
                val ss = remaining % 60
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (paidMin > 0) UberRed.copy(alpha = 0.14f) else UberGreen.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth().testTag("waiting_timer_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = if (paidMin > 0) UberRed else UberGreen, modifier = Modifier.size(18.dp))
                            Text(
                                text = if (paidMin > 0) "Платное ожидание (+${paidMin * 8} сом)" else "Бесплатное ожидание",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (paidMin > 0) UberRed else UberGreen
                            )
                        }
                        Text(
                            text = if (paidMin > 0) "+8 с/мин" else String.format(java.util.Locale.US, "%02d:%02d", mm, ss),
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (paidMin > 0) UberRed else UberGreen
                        )
                    }
                }
            }

            // Food Delivery Step-by-Step Live Timeline
            if (ride.serviceType == ServiceType.FOOD) {
                val stepIndex = when (ride.status) {
                    TripStatus.SEARCHING -> 0
                    TripStatus.ASSIGNED -> 1
                    TripStatus.ARRIVED -> 2
                    TripStatus.IN_PROGRESS, TripStatus.COMPLETED -> 3
                    TripStatus.CANCELLED -> 0
                }
                val foodSteps = listOf("Принят", "Готовится", "У курьера", "В пути")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    foodSteps.forEachIndexed { idx, label ->
                        val active = idx <= stepIndex
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = if (active) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (active) UberGreen else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) UberGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Driver & Vehicle Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Driver",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Column {
                        Text(
                            text = "${ride.driverName} ★ ${ride.driverRating}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = ride.driverCar,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (ride.businessTrip) {
                    Surface(shape = RoundedCornerShape(8.dp), color = UberBlue.copy(alpha = 0.15f)) {
                        Text(
                            text = "B2B",
                            style = MaterialTheme.typography.labelSmall,
                            color = UberBlue,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Route Summary + Edit Destination In-Trip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "A: ${ride.pickupAddress}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                    if (!ride.pickupSpotNote.isNullOrBlank()) {
                        Text(
                            text = "Точка встречи: ${ride.pickupSpotNote}",
                            style = MaterialTheme.typography.labelSmall,
                            color = UberBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    ride.waypoints.forEachIndexed { idx, wp ->
                        Text(
                            text = "Остановка ${idx + 1}: ${wp.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = UberAmber,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "B: ${ride.dropoffAddress}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    if (!ride.recipientName.isNullOrBlank()) {
                        Text(
                            text = "Получатель: ${ride.recipientName} ${ride.recipientPhone.orEmpty()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = UberGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!ride.meetAndGreetSignText.isNullOrBlank()) {
                        Text(
                            text = "✈ Meet & Greet табличка: «${ride.meetAndGreetSignText}» (45 мин ож.)",
                            style = MaterialTheme.typography.labelSmall,
                            color = UberBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!ride.courierParcelPhotoUri.isNullOrBlank()) {
                        Text(
                            text = "📷 Фото-чек посылки прикреплён",
                            style = MaterialTheme.typography.labelSmall,
                            color = UberGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                TextButton(
                    onClick = { showEditDestDialog = true },
                    modifier = Modifier.testTag("edit_destination_in_trip_button")
                ) {
                    Icon(Icons.Default.EditLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.editDestination, style = MaterialTheme.typography.labelMedium)
                }
            }

            // Driver Chat + AI Auto-Translate Button
            FilledTonalButton(
                onClick = onOpenDriverChat,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp).testTag("open_driver_chat_button")
            ) {
                Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp), tint = UberBlue)
                Spacer(Modifier.width(8.dp))
                Text("Чат с водителем (ИИ-перевод RU/KG/EN) • $chatMessageCount", fontWeight = FontWeight.Bold)
            }

            // Turn-by-Turn External Navigation Strip (Google Maps, 2GIS, Yandex)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("GOOGLE" to "Google Maps", "2GIS" to "2GIS", "YANDEX" to "Яндекс").forEach { (provider, title) ->
                    AssistChip(
                        onClick = {
                            ExternalMapNavigator.openTurnByTurnNavigation(
                                context = context,
                                destination = ride.dropoffPoint,
                                label = ride.dropoffAddress,
                                preferredProvider = provider
                            )
                        },
                        label = { Text(title, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(14.dp), tint = UberBlue)
                        },
                        modifier = Modifier.weight(1f).testTag("nav_${provider.lowercase()}_chip")
                    )
                }
            }

            // Safety Bar: SOS 102 + RideCheck + Share Live ETA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onTriggerSos()
                        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:102"))
                        context.startActivity(dial)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UberRed, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("sos_102_button")
                ) {
                    Icon(Icons.Default.Emergency, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.sosEmergency, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                FilledTonalIconButton(
                    onClick = onTriggerRideCheck,
                    modifier = Modifier.size(48.dp).testTag("trigger_ridecheck_button")
                ) {
                    Icon(Icons.Default.Shield, contentDescription = "RideCheck safety check", tint = UberBlue)
                }

                OutlinedButton(
                    onClick = {
                        val etaRemaining = ((1f - ride.progress).coerceAtLeast(0.1f) * ride.durationMin).toInt().coerceAtLeast(1)
                        val shareText = "KG Taxi Live ETA • ${ride.city}\n" +
                            "Статус: $statusText (осталось ~$etaRemaining мин)\n" +
                            "Откуда: ${ride.pickupAddress}\nКуда: ${ride.dropoffAddress}\n" +
                            "Водитель: ${ride.driverName} (${ride.driverCar})\nPIN: ${ride.safetyPin}"
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(send, strings.shareRoute))
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("share_route_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Live ETA", maxLines = 1)
                }
            }

            // Fast-complete / Cancel controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancelRide,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("cancel_ride_button")
                ) {
                    Text(strings.cancel)
                }
                Button(
                    onClick = onCompleteNow,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("complete_ride_demo_button")
                ) {
                    Text("Завершить и чек")
                }
            }
        }
    }

    if (showEditDestDialog) {
        AlertDialog(
            onDismissRequest = { showEditDestDialog = false },
            title = { Text(strings.editDestination) },
            text = {
                OutlinedTextField(
                    value = newDestText,
                    onValueChange = { newDestText = it },
                    label = { Text(strings.dropoffPoint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newDestText.isNotBlank()) onEditDestination(newDestText.trim())
                    showEditDestDialog = false
                }) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDestDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
fun PostTripReceiptDialog(
    strings: KgStrings,
    trip: LocalTripEntity,
    displayCurrency: DisplayCurrency,
    companyName: String,
    companyInn: String,
    isFavoriteDriver: Boolean,
    isBlockedDriver: Boolean,
    onToggleFavoriteDriver: (String) -> Unit,
    onToggleBlockedDriver: (String) -> Unit,
    onExportPdf: (LocalTripEntity) -> Unit,
    onConfirmElqrPaid: (Int, String) -> Unit,
    onSubmitReview: (rating: Int, tipSom: Int, lostItemNote: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var rating by remember { mutableIntStateOf(5) }
    var tipSom by remember { mutableIntStateOf(50) }
    var showLostItem by remember { mutableStateOf(false) }
    var lostItemNote by remember { mutableStateOf("") }
    var showQrDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.electronicReceipt, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(8.dp), color = UberGreen.copy(alpha = 0.15f)) {
                    Text(
                        text = displayCurrency.formatSom(trip.totalPriceSom + tipSom),
                        fontFamily = JetBrainsMono,
                        color = UberGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Official Receipt Summary Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("${trip.tariffName} • ${trip.city}", fontWeight = FontWeight.Bold)
                        Text("Откуда: ${trip.pickupAddress}", style = MaterialTheme.typography.bodySmall)
                        Text("Куда: ${trip.dropoffAddress}", style = MaterialTheme.typography.bodySmall)
                        Text("Водитель: ${trip.driverName} (${trip.driverCar})", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Оплата: ${trip.paymentMethod} • PIN: ${trip.safetyPin}" +
                                if (trip.businessTrip) " • Корпоративный чек B2B" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = UberBlue
                        )
                    }
                }

                // 5-Star Rating
                Text(strings.rateDriver, style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { rating = star }) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Star $star",
                                tint = if (star <= rating) UberAmber else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Tips
                Text(strings.tipDriver, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 50, 100, 200).forEach { amount ->
                        FilterChip(
                            selected = tipSom == amount,
                            onClick = { tipSom = amount },
                            label = { Text(if (amount == 0) "0" else "+$amount") }
                        )
                    }
                }

                // Favorite / Block Driver Preferences
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isFavoriteDriver,
                        onClick = { onToggleFavoriteDriver(trip.driverName) },
                        label = { Text(if (isFavoriteDriver) "★ В избранных" else "В избранные", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(15.dp), tint = if (isFavoriteDriver) UberGreen else MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.weight(1f).testTag("receipt_favorite_driver_chip")
                    )
                    FilterChip(
                        selected = isBlockedDriver,
                        onClick = { onToggleBlockedDriver(trip.driverName) },
                        label = { Text(if (isBlockedDriver) "Заблокирован" else "Не назначать", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(15.dp), tint = if (isBlockedDriver) UberRed else MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.weight(1f).testTag("receipt_block_driver_chip")
                    )
                }

                // ELQR / MBank QR Payment Button
                FilledTonalButton(
                    onClick = { showQrDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("receipt_elqr_pay_button")
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp), tint = UberBlue)
                    Spacer(Modifier.width(6.dp))
                    Text("Оплатить по ELQR / MBank (${trip.totalPriceSom + tipSom} сом)", fontWeight = FontWeight.Bold)
                }

                // PDF Export, B2B Text Receipt Share & Lost Item Flow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { onExportPdf(trip.copy(rating = rating, tipSom = tipSom)) },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).testTag("export_receipt_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("PDF", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = {
                            val totalWithTip = trip.totalPriceSom + tipSom
                            val b2bText = buildString {
                                appendLine("КВИТАНЦИЯ / АКТ KG TAXI")
                                if (trip.businessTrip) {
                                    appendLine("Компания: $companyName (ИНН: $companyInn)")
                                }
                                appendLine("Чек #${trip.id.takeLast(8)} • ${trip.city}")
                                appendLine("Маршрут: ${trip.pickupAddress} → ${trip.dropoffAddress}")
                                appendLine("Тариф: ${trip.tariffName} • Водитель: ${trip.driverName} (${trip.driverCar})")
                                appendLine("Итого: $totalWithTip сом (${displayCurrency.formatShort(totalWithTip)}) • Оплата: ${trip.paymentMethod}")
                            }
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Квитанция KG Taxi #${trip.id.takeLast(6)}")
                                putExtra(Intent.EXTRA_TEXT, b2bText)
                            }
                            context.startActivity(Intent.createChooser(share, "Поделиться квитанцией"))
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).testTag("share_b2b_receipt_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Чек", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { showLostItem = !showLostItem },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).testTag("lost_item_toggle_button")
                    ) {
                        Icon(Icons.Default.WorkOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Вещь", maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (showLostItem) {
                    OutlinedTextField(
                        value = lostItemNote,
                        onValueChange = { lostItemNote = it },
                        label = { Text("Что вы забыли в авто?") },
                        placeholder = { Text("Например: телефон, сумка, ключи...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmitReview(rating, tipSom, lostItemNote) },
                modifier = Modifier.testTag("submit_trip_review_button")
            ) {
                Text("Готово")
            }
        }
    )

    if (showQrDialog) {
        ElqrPaymentDialog(
            title = "Оплата поездки (ELQR)",
            amountSom = trip.totalPriceSom + tipSom,
            referenceCode = trip.id.takeLast(6),
            onConfirmPaid = {
                onConfirmElqrPaid(trip.totalPriceSom + tipSom, "Оплата поездки • ${trip.tariffName}")
                showQrDialog = false
            },
            onDismiss = { showQrDialog = false }
        )
    }
}
