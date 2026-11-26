package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
        in 5..11 -> com.example.i18n.t("Доброе утро", "Кутман таң")
        in 12..17 -> com.example.i18n.t("Добрый день", "Кутман күн")
        in 18..22 -> com.example.i18n.t("Добрый вечер", "Кутман кеч")
        else -> com.example.i18n.t("Доброй ночи", "Бейпил түн")
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
    var showFood by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (showFood) {
        FoodOrderScreen(state = state, viewModel = viewModel, onDismiss = { showFood = false })
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
                Text(com.example.i18n.t("Куда едем?", "Кайда барабыз?"), style = MaterialTheme.typography.titleMedium, color = KgInk)
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
                icon = Icons.Default.Restaurant,
                title = com.example.i18n.t("Еда", "Тамак-аш"),
                subtitle = com.example.i18n.t("кафе, продукты", "кафе, азык-түлүк"),
                highlighted = false,
                tag = "home_tile_food",
                modifier = Modifier.weight(1f),
                onClick = { showFood = true }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeTile(
                icon = Icons.Default.Event,
                title = com.example.i18n.t("Предзаказ", "Алдын ала заказ"),
                subtitle = com.example.i18n.t("межгород на завтра", "эртеңки шаар аралык"),
                highlighted = false,
                tag = "home_tile_preorder",
                modifier = Modifier.weight(1f)
            ) { viewModel.openPassengerPreorder() }
            HomeTile(
                icon = Icons.Default.Route,
                title = com.example.i18n.t("Межгород", "Шаар аралык"),
                subtitle = com.example.i18n.t("Ош, Иссык-Куль", "Ош, Ысык-Көл"),
                highlighted = false,
                tag = "home_tile_intercity",
                modifier = Modifier.weight(1f)
            ) { viewModel.openPassengerOrder(ServiceType.INTERCITY) }
        }
        // "Мои поездки" moved from the tiles to a row under them.
        RecentRow(
            icon = Icons.Default.History,
            title = com.example.i18n.t("Мои поездки", "Менин сапарларым"),
            subtitle = if (state.passengerRides.isEmpty()) "пока пусто" else "история и повтор заказа",
            onClick = { showMyRides = true }
        )

        // Pre-orders waiting for departure.
        if (state.passengerPreorders.isNotEmpty()) {
            Text(com.example.i18n.t("Ваши предзаказы", "Алдын ала заказдарыңыз"), fontWeight = FontWeight.Bold, color = KgInk)
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
        Text(com.example.i18n.t("Вы здесь", "Сиз бул жердесиз"), fontWeight = FontWeight.Bold, color = KgInk)
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

        // Safety, become a driver, support.
        InfoCardFrame(Icons.Default.Map, com.example.i18n.t("Регионы и города", "Аймактар жана шаарлар"), collapseKey = "regions_expanded_v1") {
            com.example.ui.components.RegionsBrowser(state.selectedCity.name)
        }
        SafetyCard()
        BecomeDriverCard(
            isDriver = state.isAdmin || com.example.data.model.AppRole.DRIVER in state.allowedRoles,
            status = state.registration?.driverStatus,
            onStart = viewModel::openDriverApplication,
            onOpenDriverMode = { viewModel.setRole(com.example.data.model.AppRole.DRIVER) }
        )
        SupportCard(sending = state.supportSending, onSend = { text -> viewModel.sendSupportMessage(text, null) })
        viewModel.myInviteCode()?.let { code -> InviteFriendCard(code = code, percent = state.appConfig.referralPercent) }
        HomeAdSlots()

    }
}

/** Places for advertising at the bottom of the home page; the format is chosen later. */
@Composable
private fun HomeAdSlots() {
    Text(com.example.i18n.t("Партнёры", "Өнөктөштөр"), fontWeight = FontWeight.Bold, color = KgInk)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("home_ad_slots")) {
        repeat(2) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f).height(84.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(com.example.i18n.t("Место для рекламы", "Жарнама орду"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
            Text(com.example.i18n.t("Мои поездки", "Менин сапарларым"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = KgInk)

            Text(com.example.i18n.t("Недавние адреса", "Акыркы даректер"), fontWeight = FontWeight.Bold, color = KgInk)
            RecentRow(
                icon = Icons.Default.Home,
                title = com.example.i18n.t("Дом", "Үй"),
                subtitle = state.profile.homeAddress.ifBlank { "Укажите адрес в профиле" },
                onClick = { onDismiss(); viewModel.goToSavedAddress("Дом", state.profile.homeAddress) }
            )
            RecentRow(
                icon = Icons.Default.Work,
                title = com.example.i18n.t("Работа", "Жумуш"),
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

            Text(com.example.i18n.t("История заказов", "Заказдар тарыхы"), fontWeight = FontWeight.Bold, color = KgInk, modifier = Modifier.padding(top = 6.dp))
            if (state.passengerRides.isEmpty()) {
                Text(com.example.i18n.t("Здесь появятся ваши заказы.", "Бул жерде заказдарыңыз пайда болот."), color = KgInk.copy(alpha = 0.6f))
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
        var reason by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { complainRide = null },
            title = { Text("Жалоба на поездку") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${ride.pickupAddress} → ${ride.dropoffAddress}", style = MaterialTheme.typography.bodySmall)
                    Text("Что не так?", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Цена выше обычной", "Водитель", "Машина", "Маршрут", "Забыл вещи", "Безопасность").forEach { r ->
                            FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(r) })
                        }
                    }
                    OutlinedTextField(value = text, onValueChange = { text = it.take(1000) }, label = { Text("Что случилось?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.sendSupportMessage(listOfNotNull(reason?.let { "[$it]" }, text.trim().ifBlank { null }).joinToString(" "), ride.id); complainRide = null }, enabled = reason != null || text.isNotBlank()) { Text(com.example.i18n.t("Отправить", "Жөнөтүү")) }
            },
            dismissButton = { TextButton(onClick = { complainRide = null }) { Text(com.example.i18n.t("Отмена", "Жокко чыгаруу")) } }
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
                    Text(com.example.i18n.t("Повторить", "Кайталоо"))
                }
                if (ride.status == RideStatus.COMPLETED || ride.status == RideStatus.CANCELLED) {
                    TextButton(onClick = onComplain, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Default.ReportProblem, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(com.example.i18n.t("Пожаловаться", "Арыздануу"))
                    }
                }
            }
        }
    }
}


/**
 * Card with a title. With [collapseKey] it gets an arrow in the corner to hide / show the content;
 * the choice is remembered on the phone.
 */
@Composable
private fun InfoCardFrame(
    icon: ImageVector,
    title: String,
    collapseKey: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("home_ui", android.content.Context.MODE_PRIVATE) }
    var expanded by remember(collapseKey) { mutableStateOf(collapseKey == null || prefs.getBoolean(collapseKey, true)) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = com.example.ui.theme.KgTurquoiseBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, KgTurquoiseLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = KgTurquoise, modifier = Modifier.size(22.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = KgInk, modifier = Modifier.weight(1f))
                if (collapseKey != null) {
                    IconButton(
                        onClick = {
                            expanded = !expanded
                            prefs.edit().putBoolean(collapseKey, expanded).apply()
                        },
                        modifier = Modifier.size(36.dp).testTag("toggle_$collapseKey")
                    ) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Свернуть" else "Развернуть",
                            tint = KgTurquoise
                        )
                    }
                }
            }
            if (expanded) content()
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = KgTurquoise, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = KgInk)
    }
}

/** What keeps a ride safe, plus a quick call to 112. */
@Composable
private fun SafetyCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    InfoCardFrame(Icons.Default.Shield, com.example.i18n.t("Безопасность", "Коопсуздук"), collapseKey = "safety_expanded") {
        InfoLine(Icons.Default.VerifiedUser, com.example.i18n.t("Водители проверены: паспорт, права и фото авто", "Айдоочулар текшерилген: паспорт, күбөлүк жана унаанын сүрөтү"))
        InfoLine(Icons.Default.Share, com.example.i18n.t("В поездке — «Поделиться» маршрутом с близкими", "Сапарда маршрутту жакындарыңыз менен «Бөлүшүңүз»"))
        InfoLine(Icons.Default.Pin, com.example.i18n.t("PIN-код помогает пассажиру и водителю убедиться, что они нашли друг друга", "PIN-код жүргүнчү менен айдоочуга бири-бирин туура тапканын текшерүүгө жардам берет"))
        InfoLine(Icons.Default.Sos, com.example.i18n.t("Кнопка SOS в поездке отправляет тревогу диспетчеру", "Сапардагы SOS баскычы диспетчерге кабар жөнөтөт"))
        OutlinedButton(
            onClick = {
                runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:112")))
                }
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
            modifier = Modifier.fillMaxWidth().testTag("home_call_112")
        ) {
            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(com.example.i18n.t("Экстренный вызов 112", "Шашылыш чалуу 112"))
        }
    }
}

/** Invitation to drive with KG Taxi (hidden for drivers and the owner). */
@Composable
private fun BecomeDriverCard(isDriver: Boolean, status: String?, onStart: () -> Unit, onOpenDriverMode: () -> Unit) {
    InfoCardFrame(Icons.Default.DirectionsCar, com.example.i18n.t("Стать водителем", "Айдоочу болуу"), collapseKey = "driver_invite_expanded") {
        if (isDriver) {
            Text(
                com.example.i18n.t("Вы уже водитель KG Taxi. Откройте режим водителя и нажмите GO, чтобы выйти на линию.", "Сиз KG Taxi айдоочусусуз. Айдоочу режимин ачып, линияга чыгуу үчүн GO басыңыз."),
                style = MaterialTheme.typography.bodyMedium,
                color = KgInk
            )
            Button(
                onClick = onOpenDriverMode,
                colors = ButtonDefaults.buttonColors(containerColor = KgTurquoise, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().testTag("home_driver_mode")
            ) { Text(com.example.i18n.t("Режим водителя", "Айдоочу режими")) }
            return@InfoCardFrame
        }
        Text(
            when (status) {
                "pending" -> com.example.i18n.t("Ваша заявка на проверке. Мы сообщим, когда откроется экран водителя.", "Арызыңыз текшерилүүдө. Айдоочу экраны ачылганда кабарлайбыз.")
                "rejected" -> com.example.i18n.t("Заявка отклонена. Можно исправить данные и подать снова.", "Арыз четке кагылды. Маалыматты оңдоп, кайра тапшырсаңыз болот.")
                else -> com.example.i18n.t("Зарабатывайте на своём авто. Регистрация в приложении: паспорт, права и фото машины — тариф подберём сами.", "Өз унааңыз менен иштеп табыңыз. Тиркемеде каттоо: паспорт, күбөлүк жана унаанын сүрөтү — тарифти өзүбүз тандайбыз.")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = KgInk
        )
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = KgTurquoise, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().testTag("home_become_driver")
        ) {
            Text(
                when (status) {
                    "pending" -> com.example.i18n.t("Статус заявки", "Арыздын абалы")
                    "rejected" -> com.example.i18n.t("Подать заново", "Кайра тапшыруу")
                    else -> "Начать"
                }
            )
        }
    }
}

/** Message to support right from the home screen. */
@Composable
private fun SupportCard(sending: Boolean, onSend: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    InfoCardFrame(Icons.Default.SupportAgent, com.example.i18n.t("Поддержка", "Колдоо кызматы"), collapseKey = "support_expanded") {
        Text(
            com.example.i18n.t("Есть вопрос или что-то пошло не так? Напишите нам: поможем с поездкой, оплатой или забытыми вещами. Ответим в ближайшее время.", "Сурооңуз барбы же бир нерсе туура эмес болдубу? Бизге жазыңыз: сапар, төлөм же унаада калган буюмдар боюнча жардам беребиз. Жакын арада жооп беребиз."),
            style = MaterialTheme.typography.bodyMedium,
            color = KgInk
        )
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag("home_support")) {
            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(com.example.i18n.t("Написать нам", "Бизге жазуу"))
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(com.example.i18n.t("Написать в поддержку", "Колдоо кызматына жазуу")) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(1000) },
                    label = { Text(com.example.i18n.t("Опишите вопрос", "Сурооңузду жазыңыз")) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("home_support_text")
                )
            },
            confirmButton = {
                Button(
                    onClick = { onSend(text); text = ""; open = false },
                    enabled = text.isNotBlank() && !sending
                ) { Text(com.example.i18n.t("Отправить", "Жөнөтүү")) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(com.example.i18n.t("Отмена", "Жокко чыгаруу")) } }
        )
    }
}


/** "Пригласи друга": my code and a share button. */
@Composable
private fun InviteFriendCard(code: String, percent: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    InfoCardFrame(Icons.Default.CardGiftcard, com.example.i18n.t("Пригласи друга", "Досуңду чакыр"), collapseKey = "invite_expanded") {
        Text(
            com.example.i18n.t("Отправьте другу свой код. Он получит скидку $percent% на первую поездку.", "Досуңузга кодуңузду жөнөтүңүз. Ал биринчи сапарга $percent% арзандатуу алат."),
            style = MaterialTheme.typography.bodyMedium,
            color = KgInk
        )
        Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, KgTurquoise)) {
            Text(
                code,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = KgTurquoise,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("invite_code")
            )
        }
        Button(
            onClick = {
                val text = "Еду с KG Taxi. Скачай приложение: https://mramantur-site.vercel.app/ и введи мой код $code — скидка $percent% на первую поездку."
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                }
                runCatching { context.startActivity(android.content.Intent.createChooser(send, "Пригласить друга")) }
            },
            colors = ButtonDefaults.buttonColors(containerColor = KgTurquoise, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().testTag("invite_share")
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(com.example.i18n.t("Поделиться кодом", "Кодду бөлүшүү"))
        }
    }
}
