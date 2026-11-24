package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.FeatureFlags
import com.example.R
import com.example.data.model.*
import com.example.i18n.AppLanguage
import com.example.i18n.KgStrings
import com.example.ui.KgTaxiUiState
import com.example.ui.KgTaxiViewModel
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun PassengerHomeScreen(
    strings: KgStrings,
    state: KgTaxiUiState,
    viewModel: KgTaxiViewModel,
    onRequestGps: () -> Unit,
    onRequestVoice: () -> Unit,
    onOpenAiChat: () -> Unit,
    onOpenDriverChat: () -> Unit,
    onOpenMyRides: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Home menu first (search, tiles, recent); the map and tariffs open after a choice.
    if (state.passengerHome && state.activeRide == null) {
        PassengerHomeMenu(state = state, viewModel = viewModel, onOpenMyRides = onOpenMyRides, onRequestGps = onRequestGps, modifier = modifier)
        return
    }
    androidx.activity.compose.BackHandler(enabled = state.activeRide == null) { viewModel.backToPassengerHome() }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 720.dp
        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (state.activeRide != null) InteractiveKgMap(
                    strings = strings,
                    city = state.selectedCity,
                    pickupPoint = state.pickupPoint,
                    dropoffPoint = state.dropoffPoint,
                    waypoints = state.waypoints,
                    routePolyline = state.routePolyline,
                    routeFromOsrm = state.routeFromOsrm,
                    distanceKm = state.routeDistanceKm,
                    durationMin = state.routeDurationMin,
                    nearbyDrivers = state.nearbyDrivers,
                    activeRide = state.activeRide,
                    showSurgeHeatmap = state.showSurgeHeatmap,
                    showTrafficOverlay = state.showTrafficOverlay,
                    mapPickModeIsDropoff = state.mapPickModeIsDropoff,
                    darkTheme = state.darkTheme,
                    offlineMode = state.offlineMode,
                    mapLayerStyle = state.mapLayerStyle,
                    onSelectCity = viewModel::selectCity,
                    onTapMapPoint = { viewModel.onMapTapPickPoint(it) },
                    onTogglePickTarget = viewModel::toggleMapPickTarget,
                    onLocateGps = onRequestGps,
                    onToggleSurge = viewModel::toggleSurgeHeatmap,
                    onToggleTraffic = viewModel::toggleTrafficOverlay,
                    onCycleMapLayer = viewModel::cycleMapLayerStyle,
                    onVoiceSearch = onRequestVoice,
                    onOpenAiAssistant = onOpenAiChat,
                    modifier = Modifier.weight(1.1f).fillMaxHeight()
                )
                PassengerControlSheet(
                    strings = strings,
                    state = state,
                    viewModel = viewModel,
                    onRequestVoice = onRequestVoice,
                    onOpenDriverChat = onOpenDriverChat,
                    modifier = Modifier.weight(0.9f).fillMaxHeight()
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.activeRide != null) InteractiveKgMap(
                    strings = strings,
                    city = state.selectedCity,
                    pickupPoint = state.pickupPoint,
                    dropoffPoint = state.dropoffPoint,
                    waypoints = state.waypoints,
                    routePolyline = state.routePolyline,
                    routeFromOsrm = state.routeFromOsrm,
                    distanceKm = state.routeDistanceKm,
                    durationMin = state.routeDurationMin,
                    nearbyDrivers = state.nearbyDrivers,
                    activeRide = state.activeRide,
                    showSurgeHeatmap = state.showSurgeHeatmap,
                    showTrafficOverlay = state.showTrafficOverlay,
                    mapPickModeIsDropoff = state.mapPickModeIsDropoff,
                    darkTheme = state.darkTheme,
                    offlineMode = state.offlineMode,
                    mapLayerStyle = state.mapLayerStyle,
                    onSelectCity = viewModel::selectCity,
                    onTapMapPoint = { viewModel.onMapTapPickPoint(it) },
                    onTogglePickTarget = viewModel::toggleMapPickTarget,
                    onLocateGps = onRequestGps,
                    onToggleSurge = viewModel::toggleSurgeHeatmap,
                    onToggleTraffic = viewModel::toggleTrafficOverlay,
                    onCycleMapLayer = viewModel::cycleMapLayerStyle,
                    onVoiceSearch = onRequestVoice,
                    onOpenAiAssistant = onOpenAiChat,
                    modifier = Modifier.fillMaxWidth().height(255.dp)
                )
                PassengerControlSheet(
                    strings = strings,
                    state = state,
                    viewModel = viewModel,
                    onRequestVoice = onRequestVoice,
                    onOpenDriverChat = onOpenDriverChat,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PassengerControlSheet(
    strings: KgStrings,
    state: KgTaxiUiState,
    viewModel: KgTaxiViewModel,
    onRequestVoice: () -> Unit,
    onOpenDriverChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    var breakdownExpanded by remember { mutableStateOf(false) }
    var showBidDialog by remember { mutableStateOf(false) }
    var bidInput by remember { mutableStateOf("") }
    var showReserveDialog by remember { mutableStateOf(false) }
    var showWaypointDialog by remember { mutableStateOf(false) }
    var showForOtherDialog by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(modifier = modifier.navigationBarsPadding()) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // If an Active Ride is running, show live ActiveTripCard prominently!
        if (state.activeRide != null) {
            ActiveTripCard(
                strings = strings,
                ride = state.activeRide,
                displayCurrency = state.displayCurrency,
                chatMessageCount = state.driverChatMessages.size,
                onOpenDriverChat = onOpenDriverChat,
                onTriggerSos = { viewModel.triggerEmergencySos(fromActiveRide = true) },
                onTriggerRideCheck = viewModel::triggerManualRideCheck,
                onDismissRideCheck = viewModel::dismissRideCheck,
                onEditDestination = { viewModel.editDestinationInTrip(it) },
                onCompleteNow = viewModel::completeActiveRideNow,
                onCancelRide = { reason -> viewModel.cancelActiveRide(reason) }
            )
            return@Column
        }

        // Back to the home menu + city picker (the map with city buttons is gone from this screen).
        var cityMenuOpen by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = viewModel::backToPassengerHome, modifier = Modifier.testTag("order_back_home")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Главная")
            }
            Spacer(Modifier.weight(1f))
            Box {
                FilterChip(
                    selected = true,
                    onClick = { cityMenuOpen = true },
                    label = { Text(state.selectedCity.name) },
                    leadingIcon = { Icon(Icons.Default.LocationCity, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("order_city_picker")
                )
                DropdownMenu(expanded = cityMenuOpen, onDismissRequest = { cityMenuOpen = false }) {
                    KgGeoCatalog.cities.forEach { city ->
                        DropdownMenuItem(
                            text = { Text(city.name) },
                            onClick = { cityMenuOpen = false; viewModel.selectCity(city) },
                            leadingIcon = if (city.name == state.selectedCity.name) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }
        }

        // Weather / currency strip: hidden to keep the order screen short.
        if (FeatureFlags.DEMO_EXTRAS) CityWeatherAndCurrencyStrip(
            weather = state.cityWeather,
            currency = state.displayCurrency,
            businessModeActive = state.profile.businessProfileActive,
            onSelectCurrency = viewModel::setDisplayCurrency,
            onToggleBusinessMode = {
                viewModel.updateProfile { it.copy(businessProfileActive = !it.businessProfileActive) }
            },
            onRefreshWeather = viewModel::refreshCityWeatherAndPasses
        )

        // Address Inputs Card ("Откуда" / "Куда") + Voice Search
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
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
                    Icon(Icons.Default.TripOrigin, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    // Point A: detected automatically, tap to change (another address, map, someone else).
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.openPickupDialog(true) }
                            .testTag("pickup_row")
                    ) {
                        Text(
                            if (state.pickupConfirmed) "Точка подачи" else "Укажите точку подачи",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.pickupConfirmed) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFC62828)
                        )
                        Text(
                            text = state.pickupAddress,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { viewModel.openPickupDialog(true) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text("Изменить")
                    }
                    if (state.profile.businessProfileActive) {
                        Surface(shape = RoundedCornerShape(6.dp), color = UberBlue.copy(alpha = 0.16f)) {
                            Text(
                                "B2B",
                                style = MaterialTheme.typography.labelSmall,
                                color = UberBlue,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Square, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = {
                            Text(
                                text = state.dropoffAddress.ifBlank { strings.whereTo },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("destination_search_input")
                    )
                    IconButton(
                        onClick = { showWaypointDialog = true },
                        modifier = Modifier.testTag("add_waypoint_button")
                    ) {
                        Icon(
                            Icons.Default.AddLocationAlt,
                            contentDescription = "Добавить остановку",
                            tint = UberAmber
                        )
                    }
                    if (FeatureFlags.DEMO_EXTRAS) IconButton(
                        onClick = viewModel::saveCurrentRouteOffline,
                        modifier = Modifier.testTag("save_offline_route_button")
                    ) {
                        Icon(
                            Icons.Default.BookmarkAdd,
                            contentDescription = "Сохранить маршрут офлайн",
                            tint = UberBlue
                        )
                    }
                    if (FeatureFlags.VOICE_COMMANDS) IconButton(
                        onClick = { showVoiceDialog = true },
                        modifier = Modifier.testTag("open_ai_voice_order_button")
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = strings.voiceSearch, tint = UberBlue)
                    }
                }

                // Multi-Stop Waypoint Chips (A -> Stop 1 -> Stop 2 -> B)
                if (state.waypoints.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.waypoints.forEachIndexed { idx, wp ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.removeWaypoint(wp.id) },
                                label = { Text("${idx + 1}. ${wp.name}", style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove stop", modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }
            }
        }

        // Pick "from" / "to" on a full-screen map.
        var pickOnMap by remember { mutableStateOf<Boolean?>(null) } // true = dropoff, false = pickup
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickOnMap = false }, modifier = Modifier.weight(1f).testTag("pick_pickup_on_map")) {
                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Откуда на карте", maxLines = 1)
            }
            OutlinedButton(onClick = { pickOnMap = true }, modifier = Modifier.weight(1f).testTag("pick_dropoff_on_map")) {
                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Куда на карте", maxLines = 1)
            }
        }
        if (state.pickupDialogOpen) {
            PickupPointDialog(
                state = state,
                onQuery = viewModel::setPickupSearchQuery,
                onPickPlace = viewModel::setPickupFromPlace,
                onMyLocation = viewModel::usePickupMyLocation,
                onPickOnMap = { viewModel.openPickupDialog(false); pickOnMap = false },
                onDismiss = { viewModel.openPickupDialog(false) }
            )
        }
        pickOnMap?.let { isDropoff ->
            com.example.ui.components.MapPointPickerDialog(
                title = if (isDropoff) "Куда поедем?" else "Откуда вас забрать?",
                start = if (isDropoff) state.dropoffPoint else state.pickupPoint,
                pinColor = if (isDropoff) Color(0xFF1F2D30) else MaterialTheme.colorScheme.primary,
                onPicked = { p -> viewModel.onMapTapPickPoint(p, isDropoff); pickOnMap = null },
                onDismiss = { pickOnMap = null }
            )
        }

        // Route length and time (was on the map).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Route, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(
                "${String.format(java.util.Locale.US, "%.1f", state.routeDistanceKm)} км • ${state.routeDurationMin} мин в пути",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Smart Pickup Spots (Uber US Hub Entrances — Airport, Asia Mall, TSUM, Ala-Too, etc.)
        val smartSpots = remember(state.pickupAddress) { KgGeoCatalog.pickupSpotsForAddress(state.pickupAddress) }
        if (FeatureFlags.DEMO_EXTRAS) Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = null, tint = UberBlue, modifier = Modifier.size(14.dp))
            smartSpots.forEach { spot ->
                FilterChip(
                    selected = state.selectedPickupSpot == spot,
                    onClick = { viewModel.selectPickupSpot(spot) },
                    label = { Text(spot, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("pickup_spot_${spot.hashCode()}")
                )
            }
        }

        // 1-Tap Repeat Last Trip Bar (if trip history exists)
        val lastTrip = state.trips.firstOrNull()
        if (lastTrip != null && FeatureFlags.DEMO_EXTRAS) {
            Surface(
                onClick = { viewModel.repeatTripFromHistory(lastTrip) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().testTag("repeat_last_trip_bar")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, tint = UberBlue, modifier = Modifier.size(18.dp))
                        Column {
                            Text("Повторить поездку (${lastTrip.tariffName})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text("${lastTrip.pickupAddress} → ${lastTrip.dropoffAddress}", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text("${lastTrip.totalPriceSom}с", fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, color = UberBlue)
                }
            }
        }

        // 6-Service Selector Carousel
        val serviceItems = listOf(
            Triple(ServiceType.TAXI, strings.serviceTaxi, Icons.Default.DirectionsCar),
            Triple(ServiceType.HOURLY, strings.serviceHourly, Icons.Default.Schedule),
            Triple(ServiceType.COURIER, strings.serviceCourier, Icons.Default.Inventory2),
            Triple(ServiceType.CARGO, strings.serviceCargo, Icons.Default.LocalShipping),
            Triple(ServiceType.INTERCITY, strings.serviceIntercity, Icons.Default.AirlineSeatReclineNormal),
            Triple(ServiceType.FOOD, strings.serviceFood, Icons.Default.Restaurant)
        ).filter { (type, _, _) -> FeatureFlags.isServiceEnabled(type) }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            serviceItems.forEach { (type, label, icon) ->
                val selected = state.serviceType == type
                Surface(
                    onClick = { viewModel.setServiceType(type) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("service_${type.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Intercity pre-orders waiting for their departure.
        if (state.passengerPreorders.isNotEmpty()) {
            PassengerPreordersCard(
                preorders = state.passengerPreorders,
                onCancel = viewModel::cancelPreorder
            )
        }

        // Dynamic Body Based on Selected Service
        when (state.serviceType) {
            ServiceType.TAXI -> {
                // Adaptive Learning Suggestion Banner
                val topTariff = KgGeoCatalog.taxiTariffs.firstOrNull { it.id == state.profile.preferredTariffId && it.id in FeatureFlags.ENABLED_TARIFF_IDS }
                if (topTariff != null && topTariff.id != state.selectedTariffId) {
                    Surface(
                        onClick = { viewModel.selectTariff(topTariff.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = UberBlue.copy(alpha = 0.11f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = UberBlue, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "${strings.adaptiveForYou}: ${topTariff.nameRu}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = UberBlue
                                )
                            }
                            Text(strings.apply, style = MaterialTheme.typography.labelMedium, color = UberBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Photo banner: hidden to keep tariffs right under the addresses.
                if (FeatureFlags.DEMO_EXTRAS) Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_uber_black_sedan_1795421299011),
                        contentDescription = "KG Taxi fleet",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(12.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Text(
                            text = if (state.profile.kgOneActive) "KG One активен • -7% на все тарифы" else "Быстрая подача по всему Кыргызстану",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Tariffs in one row of compact cards: name, price, car icon (like modern taxi apps).
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KgGeoCatalog.taxiTariffs.filter { it.id in FeatureFlags.ENABLED_TARIFF_IDS }.forEach { tariff ->
                        val selected = state.selectedTariffId == tariff.id
                        val pricedTariff = state.priced(tariff)
                        val estPrice = ((pricedTariff.baseSom + state.routeDistanceKm * pricedTariff.perKmSom) * state.selectedCity.effectiveSurge).roundToInt()
                        val displayName = when (state.language) {
                            AppLanguage.RU -> tariff.nameRu
                            AppLanguage.KG -> tariff.nameKg
                            AppLanguage.EN -> tariff.nameEn
                        }
                        Surface(
                            onClick = { viewModel.selectTariff(tariff.id) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = if (selected) 4.dp else 0.dp,
                            modifier = Modifier
                                .width(112.dp)
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .testTag("tariff_${tariff.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = when (tariff.id) {
                                        "minivan", "microbus" -> Icons.Default.AirportShuttle
                                        "child" -> Icons.Default.ChildCare
                                        "lady" -> Icons.Default.Woman
                                        "green_ev" -> Icons.Default.ElectricCar
                                        "courier" -> Icons.Default.Inventory2
                                        "sober_driver" -> Icons.Default.Key
                                        "cargo_15", "cargo_35" -> Icons.Default.LocalShipping
                                        "cargo_5", "cargo_10" -> Icons.Default.FireTruck
                                        "tow" -> Icons.Default.CarRepair
                                        "business", "premium" -> Icons.Default.Star
                                        "airport" -> Icons.Default.Flight
                                        "hourly" -> Icons.Default.Schedule
                                        "inva" -> Icons.Default.Accessible
                                        "wedding" -> Icons.Default.Favorite
                                        "cargo_loaders" -> Icons.Default.Groups
                                        else -> Icons.Default.DirectionsCar
                                    },
                                    contentDescription = displayName,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(30.dp)
                                )
                                Text(displayName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(
                                    text = state.displayCurrency.formatShort(estPrice),
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = tariff.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Pickup Speed Selector (Wait & Save -15% / Standard / Priority +30s)
                // Hidden until it can be guaranteed (no real matching for pickup speed / shared rides).
                if (FeatureFlags.DEMO_EXTRAS) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = state.pickupSpeed == PickupSpeed.WAIT_AND_SAVE,
                        onClick = { viewModel.setPickupSpeed(PickupSpeed.WAIT_AND_SAVE) },
                        label = { Text(strings.speedWaitSave) },
                        modifier = Modifier.testTag("speed_wait_save")
                    )
                    FilterChip(
                        selected = state.pickupSpeed == PickupSpeed.STANDARD,
                        onClick = { viewModel.setPickupSpeed(PickupSpeed.STANDARD) },
                        label = { Text(strings.speedStandard) },
                        modifier = Modifier.testTag("speed_standard")
                    )
                    FilterChip(
                        selected = state.pickupSpeed == PickupSpeed.PRIORITY,
                        onClick = { viewModel.setPickupSpeed(PickupSpeed.PRIORITY) },
                        label = { Text(strings.speedPriority) },
                        modifier = Modifier.testTag("speed_priority")
                    )
                }
                }

                // Carpool / Share Mode ("Попутчик • Вместе -30%")
                if (FeatureFlags.DEMO_EXTRAS) {
                Surface(
                    onClick = viewModel::toggleCarpoolMode,
                    shape = RoundedCornerShape(14.dp),
                    color = if (state.carpoolModeEnabled) UberGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (state.carpoolModeEnabled) 1.5.dp else 0.dp,
                            color = if (state.carpoolModeEnabled) UberGreen else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .testTag("carpool_share_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = if (state.carpoolModeEnabled) UberGreen else UberBlue)
                            Column {
                                Text(
                                    text = "Попутчик «Вместе» • Экономия -30%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.carpoolModeEnabled) UberGreen else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Совместная поездка по пути (Аэропорт Манас, пр. Чуй, Ош)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = state.carpoolModeEnabled,
                            onCheckedChange = { viewModel.toggleCarpoolMode() }
                        )
                    }
                }
                }

                // Ride Preferences Chips + Split Fare
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = state.preferences.quietRide,
                        onClick = { viewModel.updatePreferences { it.copy(quietRide = !it.quietRide) } },
                        label = { Text(strings.prefQuiet) }
                    )
                    FilterChip(
                        selected = state.preferences.climateAc,
                        onClick = { viewModel.updatePreferences { it.copy(climateAc = !it.climateAc) } },
                        label = { Text(strings.prefClimate) }
                    )
                    FilterChip(
                        selected = state.preferences.luggageHelp,
                        onClick = { viewModel.updatePreferences { it.copy(luggageHelp = !it.luggageHelp) } },
                        label = { Text(strings.prefLuggage) }
                    )
                    FilterChip(
                        selected = state.preferences.petFriendly,
                        onClick = { viewModel.updatePreferences { it.copy(petFriendly = !it.petFriendly) } },
                        label = { Text(strings.prefPet) }
                    )
                    FilterChip(
                        selected = state.splitCount > 1,
                        onClick = { viewModel.setSplitCount(if (state.splitCount >= 4) 1 else state.splitCount + 1) },
                        label = { Text("${strings.splitFare}: ÷${state.splitCount}") },
                        modifier = Modifier.testTag("split_fare_chip")
                    )
                }
            }
            ServiceType.HOURLY -> HourlyRentalPanel(strings, state, viewModel::setHourlyHours, viewModel::selectExcursion, viewModel::requestAiTourGuide)
            ServiceType.COURIER -> CourierPanel(state, viewModel::setCourierDetails, viewModel::setCourierParcelPhotoUri)
            ServiceType.CARGO -> CargoPanel(strings, state, viewModel::setCargoOptions)
            ServiceType.INTERCITY -> IntercitySeatPickerPanel(strings, state, viewModel::selectIntercityRoute, viewModel::setIntercityDeparture, viewModel::toggleIntercitySeat, viewModel::requestAiTourGuide)
            ServiceType.FOOD -> FoodDeliveryPanel(strings, state, viewModel::selectFoodVenue, viewModel::addFoodToCart, viewModel::removeFoodFromCart)
        }

        // Saved addresses, "для другого" and destination suggestions (below the tariffs).
        // Live Autocomplete (Nominatim Live + Curated KG Landmarks) & Rating/Price Filtered Places
        val matchingPlaces = remember(
            state.searchQuery,
            state.selectedCity,
            state.filterMinRating,
            state.filterMaxPriceLevel,
            state.liveSearchResults
        ) {
            val base = state.selectedCity.landmarks.filter {
                it.rating >= state.filterMinRating && it.priceLevel <= state.filterMaxPriceLevel
            }
            if (state.searchQuery.isBlank()) base.take(5)
            else {
                val curated = KgGeoCatalog.cities.flatMap { it.landmarks }.filter {
                    (it.name.contains(state.searchQuery, ignoreCase = true) ||
                        it.address.contains(state.searchQuery, ignoreCase = true) ||
                        it.category.contains(state.searchQuery, ignoreCase = true)) &&
                        it.rating >= state.filterMinRating &&
                        it.priceLevel <= state.filterMaxPriceLevel
                }
                (state.liveSearchResults + curated).distinctBy { it.name }.take(8)
            }
        }

        // Quick Saved Cards: Дом / Работа + Другому человеку + Filters + Reserve
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = { viewModel.selectSavedAddressQuick(strings.home, state.profile.homeAddress) },
                label = { Text(strings.home) },
                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("quick_home_chip")
            )
            AssistChip(
                onClick = { viewModel.selectSavedAddressQuick(strings.work, state.profile.workAddress) },
                label = { Text(strings.work) },
                leadingIcon = { Icon(Icons.Default.Work, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("quick_work_chip")
            )
            FilterChip(
                selected = state.bookingForOther,
                onClick = { showForOtherDialog = true },
                label = { Text(if (state.bookingForOther && state.recipientName.isNotBlank()) "Для: ${state.recipientName}" else "Заказать другому человеку") },
                leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("book_for_other_chip")
            )
            // Rating / budget filters used made-up place data; scheduled rides are not supported by
            // real orders yet (they would go out immediately). Hidden with the demo extras.
            if (FeatureFlags.DEMO_EXTRAS) FilterChip(
                selected = state.filterMinRating >= 4.5,
                onClick = { viewModel.setRatingFilter(if (state.filterMinRating >= 4.5) 0.0 else 4.5) },
                label = { Text(strings.filterRating) },
                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = UberAmber, modifier = Modifier.size(16.dp)) }
            )
            if (FeatureFlags.DEMO_EXTRAS) FilterChip(
                selected = state.filterMaxPriceLevel == 1,
                onClick = { viewModel.setPriceLevelFilter(if (state.filterMaxPriceLevel == 1) 3 else 1) },
                label = { Text("$ Бюджет") }
            )
            if (FeatureFlags.DEMO_EXTRAS) FilterChip(
                selected = state.reserveTimeLabel != null,
                onClick = { showReserveDialog = true },
                label = { Text(state.reserveTimeLabel ?: strings.reserveTrip) },
                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("reserve_schedule_chip")
            )
        }

        // Address suggestions: only while typing in "Куда".
        if (state.searchQuery.isNotBlank()) Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            matchingPlaces.forEach { place ->
                Surface(
                    onClick = { viewModel.selectDestinationPlace(place) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.testTag("landmark_${place.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = UberBlue, modifier = Modifier.size(15.dp))
                        Column {
                            Text(place.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            Text(
                                place.address,
                                style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Bidding Offers List if active
        if (state.biddingActive && state.bidOffers.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = UberBlue.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, UberBlue.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Предложения водителей (ваша цена ${state.userBidSom}с)", fontWeight = FontWeight.Bold, color = UberBlue)
                        IconButton(onClick = viewModel::closeBidding, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close bidding")
                        }
                    }
                    state.bidOffers.forEach { offer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${offer.driverName} ★ ${offer.rating}", fontWeight = FontWeight.Bold)
                                Text("${offer.carModel} • ${offer.etaMin} мин", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = { viewModel.acceptBidOffer(offer) },
                                colors = ButtonDefaults.buttonColors(containerColor = UberGreen, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("accept_bid_${offer.id}")
                            ) {
                                Text("${offer.offeredPriceSom}с", fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Expandable Transparent Price Breakdown Card
        val pb = state.priceBreakdown
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { breakdownExpanded = !breakdownExpanded }
                        .testTag("price_breakdown_toggle"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(strings.priceBreakdown, style = MaterialTheme.typography.titleSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = state.displayCurrency.formatSom(pb.finalTotalSom),
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (breakdownExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle breakdown"
                        )
                    }
                }

                AnimatedVisibility(visible = breakdownExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HorizontalDivider()
                        PriceRow("Базовая подача / тариф", "${pb.baseSom} ${strings.currencySom}")
                        if (pb.distanceSom > 0) PriceRow("Километраж / доставка", "${pb.distanceSom} ${strings.currencySom}")
                        if (pb.waypointsSom > 0) PriceRow("Промежуточные остановки (${state.waypoints.size})", "+${pb.waypointsSom} ${strings.currencySom}", UberAmber)
                        if (pb.meetAndGreetSom > 0) PriceRow("Встреча с табличкой (Meet & Greet)", "+${pb.meetAndGreetSom} ${strings.currencySom}", UberBlue)
                        if (pb.surgeSom > 0) PriceRow("Коэффициент спроса (x${state.selectedCity.effectiveSurge})", "+${pb.surgeSom} ${strings.currencySom}", UberAmber)
                        if (pb.speedDeltaSom != 0) PriceRow("Скорость подачи", "${if (pb.speedDeltaSom > 0) "+" else ""}${pb.speedDeltaSom} ${strings.currencySom}")
                        if (pb.carpoolDiscountSom > 0) PriceRow("Попутчик «Вместе» (-30%)", "-${pb.carpoolDiscountSom} ${strings.currencySom}", UberGreen)
                        if (pb.kgOneDiscountSom > 0) PriceRow("Подписка KG Plus (-7% + защита спроса)", "-${pb.kgOneDiscountSom} ${strings.currencySom}", UberGreen)
                        if (pb.promoDiscountSom > 0) PriceRow("Промокод (-${state.appliedPromoPercent}%)", "-${pb.promoDiscountSom} ${strings.currencySom}", UberGreen)
                        if (pb.bonusUsedSom > 0) PriceRow("Списано бонусов", "-${pb.bonusUsedSom} ${strings.currencySom}", UberGreen)
                        if (FeatureFlags.WALLET && pb.kgPlusCashbackSom > 0) PriceRow(if (state.profile.kgOneActive) "Кэшбэк KG Plus (10%)" else "Кэшбэк баллами (5%)", "+${pb.kgPlusCashbackSom} б.", UberBlue)
                        if (pb.splitCount > 1) PriceRow("Split Fare (÷${pb.splitCount})", "${pb.perPersonSom} ${strings.currencySom}/чел", UberBlue)

                        // Promo code + Bonus toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = state.promoCodeInput,
                                onValueChange = viewModel::setPromoInput,
                                placeholder = { Text("Промокод") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(onClick = viewModel::applyPromoCode) {
                                Text(strings.apply)
                            }
                        }
                        // Bonus points are off with the wallet.
                        if (FeatureFlags.WALLET) Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${strings.bonusPoints}: ${state.profile.bonusPoints} ${strings.currencySom}")
                            Switch(checked = state.useBonusPoints, onCheckedChange = { viewModel.toggleUseBonusPoints() })
                        }
                    }
                }
            }
        }

    }

    // Order bar: always visible at the bottom of the screen.
    val pb = state.priceBreakdown
    if (state.activeRide == null) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        // Bottom CTA Row: Bidding ("Предложить цену") + Primary Uber Black Order CTA
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Own price is off: prices are set only by the owner.
            if (state.serviceType == ServiceType.TAXI && FeatureFlags.DEMO_EXTRAS) {
                OutlinedButton(
                    onClick = {
                        bidInput = (pb.finalTotalSom * 0.9).roundToInt().toString()
                        showBidDialog = true
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("bid_your_price_button")
                ) {
                    Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Своя цена", fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { viewModel.requestOrder() },
                enabled = state.serviceType != ServiceType.FOOD || state.foodCart.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("primary_order_ride_button")
            ) {
                Text(
                    text = (if (state.serviceType == ServiceType.INTERCITY && state.intercityDepartureMillis != null) "Предзаказать" else strings.orderRide) +
                        " • ${state.displayCurrency.formatSom(pb.finalTotalSom)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
            }
        }
    }
    }

    // Bidding Dialog
    if (showBidDialog) {
        AlertDialog(
            onDismissRequest = { showBidDialog = false },
            title = { Text(strings.bidYourPrice, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Рекомендуемая цена: ${state.priceBreakdown.finalTotalSom} ${strings.currencySom}")
                    OutlinedTextField(
                        value = bidInput,
                        onValueChange = { bidInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Ваша цена (${strings.currencySom})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val offer = bidInput.toIntOrNull() ?: state.priceBreakdown.finalTotalSom
                    viewModel.startBidding(offer)
                    showBidDialog = false
                }) {
                    Text("Отправить водителям")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBidDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    // Uber Reserve + Flight Tracker + Airport Meet & Greet Dialog (FRU / OSS)
    if (showReserveDialog) {
        var pickedSlot by remember { mutableStateOf("Сегодня, 18:30") }
        var pickedFlight by remember { mutableStateOf(state.selectedFlight) }
        var meetGreetOn by remember { mutableStateOf(state.meetAndGreetEnabled) }
        var meetGreetText by remember { mutableStateOf(state.meetAndGreetSignText.ifBlank { state.profile.displayName }) }
        AlertDialog(
            onDismissRequest = { showReserveDialog = false },
            title = { Text("${strings.reserveTrip} & ${strings.flightTracker}", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Выберите время подачи:", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Через 1 час", "Сегодня, 18:30", "Завтра, 06:00", "Завтра, 09:00").forEach { slot ->
                            FilterChip(
                                selected = pickedSlot == slot,
                                onClick = { pickedSlot = slot },
                                label = { Text(slot) }
                            )
                        }
                    }
                    Text("Отслеживание авиарейса (FRU / OSS):", style = MaterialTheme.typography.titleSmall)
                    KgGeoCatalog.flights.forEach { flight ->
                        val selected = pickedFlight?.code == flight.code
                        Surface(
                            onClick = { pickedFlight = if (selected) null else flight },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) UberBlue.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("${flight.code} (${flight.airport}) • ${flight.origin}", fontWeight = FontWeight.Bold)
                                    Text("Прилёт ${flight.etaTime} • ${flight.status}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (selected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = UberBlue)
                            }
                        }
                    }

                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Встреча с табличкой (Meet & Greet)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = if (state.profile.kgOneActive) "45 мин ожидания • Бесплатно с KG One" else "45 мин ожидания после посадки (+150 сом)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = meetGreetOn, onCheckedChange = { meetGreetOn = it }, modifier = Modifier.testTag("meet_greet_switch"))
                    }
                    if (meetGreetOn) {
                        OutlinedTextField(
                            value = meetGreetText,
                            onValueChange = { meetGreetText = it },
                            label = { Text("Текст на табличке (имя гостя / компания)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setReserveSchedule(pickedSlot, pickedFlight)
                    viewModel.setMeetAndGreet(meetGreetOn, meetGreetText)
                    showReserveDialog = false
                }) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setReserveSchedule(null, null)
                    viewModel.setMeetAndGreet(false, "")
                    showReserveDialog = false
                }) { Text("Сбросить") }
            }
        )
    }

    if (showVoiceDialog) {
        VoiceOrderAssistantDialog(
            isParsing = state.voiceOrderParsing,
            lastResultSummary = state.lastVoiceOrderSummary,
            onLaunchSystemMic = {
                showVoiceDialog = false
                onRequestVoice()
            },
            onSubmitVoicePhrase = viewModel::handleVoiceCommand,
            onDismiss = { showVoiceDialog = false }
        )
    }

    // Multi-Stop Waypoint Picker Dialog
    if (showWaypointDialog) {
        AlertDialog(
            onDismissRequest = { showWaypointDialog = false },
            title = { Text("Добавить остановку по пути (+45 сом)", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Выберите до 2 промежуточных точек в г. ${state.selectedCity.name}:", style = MaterialTheme.typography.bodySmall)
                    state.selectedCity.landmarks.forEach { place ->
                        val added = state.waypoints.any { it.id == place.id }
                        Surface(
                            onClick = {
                                if (added) viewModel.removeWaypoint(place.id) else viewModel.addWaypoint(place)
                                showWaypointDialog = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (added) UberAmber.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(place.name, fontWeight = FontWeight.SemiBold)
                                    Text(place.address, style = MaterialTheme.typography.labelSmall)
                                }
                                Icon(
                                    imageVector = if (added) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                                    contentDescription = null,
                                    tint = if (added) UberGreen else UberBlue
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWaypointDialog = false }) { Text("Закрыть") }
            }
        )
    }

    // Order for Someone Else Dialog
    if (showForOtherDialog) {
        var rName by remember { mutableStateOf(state.recipientName) }
        var rPhone by remember { mutableStateOf(state.recipientPhone.ifBlank { "+996 " }) }
        AlertDialog(
            onDismissRequest = { showForOtherDialog = false },
            title = { Text("Заказать другому человеку", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Водитель увидит имя получателя, а PIN безопасности можно переслать ему в 1 клик.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = rName, onValueChange = { rName = it }, label = { Text("Имя получателя") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = rPhone, onValueChange = { rPhone = it }, label = { Text("Телефон (+996...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setBookingForOther(true, rName, rPhone)
                    showForOtherDialog = false
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setBookingForOther(false, "", "")
                    showForOtherDialog = false
                }) { Text("Отменить") }
            }
        )
    }
}

@Composable
private fun PriceRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontFamily = JetBrainsMono, color = valueColor)
    }
}

/** "Мои предзаказы": departure, status, driver and a call / cancel button. */
@Composable
private fun PassengerPreordersCard(
    preorders: List<com.example.data.firebase.SharedRide>,
    onCancel: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmId by remember { mutableStateOf<String?>(null) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().testTag("passenger_preorders_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Мои предзаказы", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            preorders.forEach { r ->
                val booked = r.status == com.example.data.firebase.RideStatus.BOOKED
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${r.city} → ${r.dropoffAddress}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${r.scheduledAtMillis?.let { com.example.util.formatDeparture(it) }.orEmpty()} • " +
                            "${r.seats ?: 1} мест. • ${r.priceSom} сом",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (booked) "Водитель: ${r.driverName.orEmpty()} • ${r.driverCar.orEmpty()} ${r.driverPlate.orEmpty()}".trim()
                        else "Ищем водителя…",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (booked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (booked && !r.driverPayPhone.isNullOrBlank()) {
                            OutlinedButton(onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_DIAL,
                                            android.net.Uri.parse("tel:${r.driverPayPhone}")
                                        )
                                    )
                                }
                            }) {
                                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Позвонить")
                            }
                        }
                        TextButton(onClick = { confirmId = r.id }) { Text("Отменить") }
                    }
                }
            }
        }
    }
    confirmId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmId = null },
            title = { Text("Отменить предзаказ?") },
            text = { Text("Заказ пропадёт у водителя, поездки не будет.") },
            confirmButton = {
                TextButton(onClick = { onCancel(id); confirmId = null }) { Text("Отменить предзаказ") }
            },
            dismissButton = { TextButton(onClick = { confirmId = null }) { Text("Оставить") } }
        )
    }
}


/** Point A: where the driver should come. My location, a typed address, or a point on the map. */
@Composable
private fun PickupPointDialog(
    state: KgTaxiUiState,
    onQuery: (String) -> Unit,
    onPickPlace: (com.example.data.model.KgPlace) -> Unit,
    onMyLocation: () -> Unit,
    onPickOnMap: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Точка подачи") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Где вас будет ждать водитель? Можно указать другой адрес, если заказываете для другого человека.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onMyLocation, modifier = Modifier.fillMaxWidth().testTag("pickup_my_location")) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Моё местоположение")
                }
                OutlinedButton(onClick = onPickOnMap, modifier = Modifier.fillMaxWidth().testTag("pickup_on_map")) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Указать точку на карте")
                }
                OutlinedTextField(
                    value = state.pickupSearchQuery,
                    onValueChange = onQuery,
                    label = { Text("Или введите адрес") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pickup_search")
                )
                state.pickupSearchResults.forEach { place ->
                    Surface(
                        onClick = { onPickPlace(place) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(place.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (place.address.isNotBlank()) {
                                Text(place.address, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}
