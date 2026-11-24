package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.firebase.RideStatus
import com.example.data.model.KgGeoCatalog
import com.example.data.model.ServiceType
import com.example.ui.KgTaxiUiState
import com.example.ui.KgTaxiViewModel
import com.example.ui.theme.KgInk
import com.example.ui.theme.KgTurquoise
import com.example.ui.theme.KgTurquoiseLight

/**
 * Passenger home: greeting, "Куда едем?", service tiles and recent addresses.
 * Choosing anything opens the order screen (map, addresses, tariff cards, order button).
 */
@Composable
fun PassengerHomeMenu(
    state: KgTaxiUiState,
    viewModel: KgTaxiViewModel,
    onOpenMyRides: () -> Unit,
    onRequestGps: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Ask for the location once when the home opens (silent if already allowed).
    androidx.compose.runtime.LaunchedEffect(Unit) { onRequestGps() }
    val firstName = state.profile.displayName.substringBefore(' ').takeIf { it.isNotBlank() && it != "Пассажир" && it != "Гость" }
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
    val econom = KgGeoCatalog.taxiTariffs.firstOrNull { it.id == "econom" }?.let { state.priced(it) }
    // Recent destinations from real orders, without repeats.
    val recent = remember(state.passengerRides) {
        state.passengerRides.filter { it.status == RideStatus.COMPLETED || it.status == RideStatus.CANCELLED }
            .distinctBy { it.dropoffAddress }
            .take(4)
    }

    var showMyRides by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (showMyRides) {
        MyRidesSheet(state = state, viewModel = viewModel, recent = recent, onDismiss = { showMyRides = false })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            if (firstName != null) "$greeting, $firstName" else greeting,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = KgInk
        )

        // Search: opens the order screen.
        Surface(
            onClick = { viewModel.openPassengerOrder(ServiceType.TAXI) },
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(2.dp, KgTurquoise),
            modifier = Modifier.fillMaxWidth().testTag("home_search")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = KgTurquoise)
                Text("Куда едем?", style = MaterialTheme.typography.titleMedium, color = KgInk)
            }
        }

        // Service tiles 2×2.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeTile(
                icon = Icons.Default.DirectionsCar,
                title = "Такси",
                subtitle = econom?.let { "от ${it.baseSom} сом" } ?: "по городу",
                highlighted = true,
                tag = "home_tile_taxi",
                modifier = Modifier.weight(1f)
            ) { viewModel.openPassengerOrder(ServiceType.TAXI) }
            HomeTile(
                icon = Icons.Default.Route,
                title = "Межгород",
                subtitle = "Ош, Иссык-Куль",
                highlighted = false,
                tag = "home_tile_intercity",
                modifier = Modifier.weight(1f)
            ) { viewModel.openPassengerOrder(ServiceType.INTERCITY) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeTile(
                icon = Icons.Default.Event,
                title = "Предзаказ",
                subtitle = "межгород на завтра",
                highlighted = false,
                tag = "home_tile_preorder",
                modifier = Modifier.weight(1f)
            ) { viewModel.openPassengerPreorder() }
            HomeTile(
                icon = Icons.Default.History,
                title = "Мои поездки",
                subtitle = if (state.passengerRides.isEmpty()) "пока пусто" else "повторить",
                highlighted = false,
                tag = "home_tile_rides",
                modifier = Modifier.weight(1f),
                onClick = { showMyRides = true }
            )
        }

        // Pre-orders waiting for departure.
        if (state.passengerPreorders.isNotEmpty()) {
            Text("Ваши предзаказы", fontWeight = FontWeight.Bold, color = KgInk)
            state.passengerPreorders.forEach { r ->
                RecentRow(
                    icon = Icons.Default.Event,
                    title = "${r.city} → ${r.dropoffAddress}",
                    subtitle = (r.scheduledAtMillis?.let { com.example.util.formatDeparture(it) } ?: "") +
                        if (r.status == RideStatus.BOOKED) " • водитель найден" else " • ищем водителя",
                    onClick = { viewModel.openPassengerOrder(ServiceType.INTERCITY) }
                )
            }
        }

        // Open map with "where I am" (instead of the recent list at the top).
        Text("Вы здесь", fontWeight = FontWeight.Bold, color = KgInk)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(18.dp))
                .testTag("home_location_map")
        ) {
            if (!state.offlineMode) {
                val me = state.pickupPoint
                com.example.ui.components.OsmMapLayer(
                    center = me,
                    centerKey = "${me.lat},${me.lng}",
                    pins = buildList {
                        state.nearbyDrivers.forEach { d ->
                            add(com.example.ui.components.OsmPin(d.point, "", 0xFF3B82F6.toInt(), "KG Taxi", small = true))
                        }
                        add(com.example.ui.components.OsmPin(me, "Я", 0xFF0097A7.toInt(), state.pickupAddress))
                    },
                    route = emptyList(),
                    routeColor = 0xFF0097A7.toInt(),
                    darkTheme = false,
                    fitTo = emptyList(),
                    onTap = { viewModel.openPassengerOrder(ServiceType.TAXI) },
                    zoom = 15.5,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize().background(KgTurquoiseLight), contentAlignment = Alignment.Center) {
                    Text("Нет интернета — карта недоступна", color = KgInk)
                }
            }
            // Address of the current place and "my location" button.
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).fillMaxWidth(0.78f)
            ) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = KgTurquoise, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(state.pickupAddress, style = MaterialTheme.typography.bodySmall, color = KgInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SmallFloatingActionButton(
                onClick = onRequestGps,
                containerColor = KgTurquoise,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).testTag("home_locate_me")
            ) { Icon(Icons.Default.GpsFixed, contentDescription = "Моё местоположение") }
        }

        // AI guide: stories about places of Kyrgyzstan (moved here from the intercity screen).
        if (com.example.FeatureFlags.AI_TOUR_GUIDE) {
            com.example.ui.components.AiTourGuideCard(
                language = state.language,
                currentTopic = state.tourGuideTopic,
                storyText = state.tourGuideText,
                isLoading = state.tourGuideLoading,
                onRequestStory = viewModel::requestAiTourGuide
            )
        }

    }
}

@Composable
private fun HomeTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (highlighted) KgTurquoise else KgTurquoiseLight,
        modifier = modifier.heightIn(min = 108.dp).testTag(tag)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = if (highlighted) Color.White else KgTurquoise, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (highlighted) Color.White else KgInk)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (highlighted) Color.White.copy(alpha = 0.9f) else KgInk.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun RecentRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = com.example.ui.theme.KgTurquoiseBg, border = androidx.compose.foundation.BorderStroke(1.dp, KgTurquoiseLight), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = RoundedCornerShape(10.dp), color = KgTurquoiseLight, modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = KgTurquoise, modifier = Modifier.size(20.dp)) }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = KgInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = KgInk.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = KgInk.copy(alpha = 0.4f))
        }
    }
}


/** "Мои поездки": saved places, recent addresses and the history of orders with repeat and complaint. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyRidesSheet(
    state: KgTaxiUiState,
    viewModel: KgTaxiViewModel,
    recent: List<com.example.data.firebase.SharedRide>,
    onDismiss: () -> Unit
) {
    var complainRide by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.example.data.firebase.SharedRide?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .testTag("my_rides_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Мои поездки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = KgInk)

            Text("Недавние адреса", fontWeight = FontWeight.Bold, color = KgInk)
            RecentRow(
                icon = Icons.Default.Home,
                title = "Дом",
                subtitle = state.profile.homeAddress.ifBlank { "Укажите адрес в профиле" },
                onClick = { onDismiss(); viewModel.goToSavedAddress("Дом", state.profile.homeAddress) }
            )
            RecentRow(
                icon = Icons.Default.Work,
                title = "Работа",
                subtitle = state.profile.workAddress.ifBlank { "Укажите адрес в профиле" },
                onClick = { onDismiss(); viewModel.goToSavedAddress("Работа", state.profile.workAddress) }
            )
            recent.forEach { r ->
                RecentRow(
                    icon = Icons.Default.Schedule,
                    title = r.dropoffAddress,
                    subtitle = "из: ${r.pickupAddress}",
                    onClick = { onDismiss(); viewModel.goToRecentDestination(r) }
                )
            }

            Text("История заказов", fontWeight = FontWeight.Bold, color = KgInk, modifier = Modifier.padding(top = 6.dp))
            if (state.passengerRides.isEmpty()) {
                Text("Здесь появятся ваши заказы.", color = KgInk.copy(alpha = 0.6f))
            }
            state.passengerRides.take(30).forEach { r ->
                RideHistoryRow(
                    ride = r,
                    onRepeat = { onDismiss(); viewModel.repeatRide(r) },
                    onComplain = { complainRide = r }
                )
            }
        }
    }
    complainRide?.let { ride ->
        var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { complainRide = null },
            title = { Text("Жалоба на поездку") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${ride.pickupAddress} → ${ride.dropoffAddress}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = text, onValueChange = { text = it.take(1000) }, label = { Text("Что случилось?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.sendSupportMessage(text, ride.id); complainRide = null }, enabled = text.isNotBlank()) { Text("Отправить") }
            },
            dismissButton = { TextButton(onClick = { complainRide = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun RideHistoryRow(ride: com.example.data.firebase.SharedRide, onRepeat: () -> Unit, onComplain: () -> Unit) {
    val date = androidx.compose.runtime.remember(ride.createdAtMillis) {
        if (ride.createdAtMillis > 0) java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.US).format(java.util.Date(ride.createdAtMillis)) else ""
    }
    val status = when (ride.status) {
        RideStatus.COMPLETED -> "Выполнен"
        RideStatus.CANCELLED -> "Отменён"
        RideStatus.BOOKED -> "Предзаказ принят"
        RideStatus.SEARCHING -> "Ищем водителя"
        else -> "В пути"
    }
    Surface(shape = RoundedCornerShape(14.dp), color = com.example.ui.theme.KgTurquoiseBg, border = androidx.compose.foundation.BorderStroke(1.dp, KgTurquoiseLight), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(date, style = MaterialTheme.typography.labelMedium, color = KgInk.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                Text("${ride.priceSom} сом", fontWeight = FontWeight.Bold, color = KgInk)
            }
            Text("${ride.pickupAddress} → ${ride.dropoffAddress}", color = KgInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(status, ride.driverName?.takeIf { it.isNotBlank() }, ride.driverCar?.takeIf { it.isNotBlank() }).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = if (ride.status == RideStatus.CANCELLED) Color(0xFFC62828) else KgTurquoise
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRepeat, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Повторить")
                }
                if (ride.status == RideStatus.COMPLETED || ride.status == RideStatus.CANCELLED) {
                    TextButton(onClick = onComplain, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Default.ReportProblem, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Пожаловаться")
                    }
                }
            }
        }
    }
}
