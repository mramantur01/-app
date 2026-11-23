package com.example.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.*
import com.example.i18n.KgStrings
import com.example.ui.KgTaxiUiState
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HourlyRentalPanel(
    strings: KgStrings,
    state: KgTaxiUiState,
    onSetHours: (Int) -> Unit,
    onSelectExcursion: (ExcursionPreset) -> Unit,
    onRequestTourGuide: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Editorial Kyrgyzstan Tour Hero Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_kyrgyzstan_tour_1795421299618),
                contentDescription = "Kyrgyzstan mountain excursions",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .padding(14.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text(
                        text = "Почасовая аренда • 650 ${strings.currencySom}/час",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Личный водитель или готовые горные туры по КР",
                        color = Color.White.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Hours Stepper
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Длительность: ${state.hourlyHours} ч", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = { onSetHours(state.hourlyHours - 1) }) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease hours")
                }
                Text(
                    text = "${state.hourlyHours * 650} ${strings.currencySom}",
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold
                )
                FilledTonalIconButton(onClick = { onSetHours(state.hourlyHours + 1) }) {
                    Icon(Icons.Default.Add, contentDescription = "Increase hours")
                }
            }
        }

        Text("Готовые экскурсионные маршруты", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KgGeoCatalog.excursionPresets.forEach { preset ->
                val selected = state.selectedExcursion?.id == preset.id
                Surface(
                    onClick = { onSelectExcursion(preset) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().testTag("excursion_${preset.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.title,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = preset.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${preset.priceSom} ${strings.currencySom}",
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else UberBlue
                        )
                    }
                }
            }
        }

        // AI Audio Tour Guide for Excursions (Gemini 3.8 Flash + Android TTS)
        AiTourGuideCard(
            language = state.language,
            currentTopic = state.tourGuideTopic,
            storyText = state.tourGuideText,
            isLoading = state.tourGuideLoading,
            onRequestStory = onRequestTourGuide
        )
    }
}

@Composable
fun CourierPanel(
    state: KgTaxiUiState,
    onUpdateCourier: (String, String) -> Unit,
    onAttachParcelPhoto: (String?) -> Unit
) {
    // Zero-permission Android Photo Picker (Google Play policy compliant)
    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onAttachParcelPhoto(uri.toString())
        }
    }

    var parcelThumb by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(state.courierParcelPhotoUri) {
        val rawUri = state.courierParcelPhotoUri
        parcelThumb = if (!rawUri.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    context.contentResolver.openInputStream(Uri.parse(rawUri))?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
                    }
                } catch (_: Exception) {
                    null
                }
            }
        } else null
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Курьерская доставка от двери до двери", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = state.courierRecipient,
            onValueChange = { onUpdateCourier(it, state.courierComment) },
            label = { Text("Телефон и имя получателя") },
            placeholder = { Text("+996 555 ... • Азат") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.courierComment,
            onValueChange = { onUpdateCourier(state.courierRecipient, it) },
            label = { Text("Что передаём / комментарий курьеру") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Parcel Photo Confirmation Strip
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val thumb = parcelThumb
                    if (thumb != null) {
                        Image(
                            bitmap = thumb,
                            contentDescription = "Фото посылки",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else if (!state.courierParcelPhotoUri.isNullOrBlank()) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Фото посылки прикреплено", tint = UberGreen)
                    } else {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = UberBlue)
                    }
                    Column {
                        Text(
                            text = if (state.courierParcelPhotoUri != null) "Фото посылки прикреплено" else "Фото-чек посылки",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Подтверждение состояния при отправке",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("courier_pick_photo_button")
                    ) {
                        Text(if (state.courierParcelPhotoUri != null) "Сменить" else "Выбрать")
                    }
                    if (state.courierParcelPhotoUri != null) {
                        IconButton(onClick = { onAttachParcelPhoto(null) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove photo", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CargoPanel(
    strings: KgStrings,
    state: KgTaxiUiState,
    onUpdateCargo: (String, Int) -> Unit
) {
    val vehicles = listOf("Портер (до 1.5 т)", "Спринтер (до 2.5 т)", "Газель (до 3.5 т)")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Грузовое такси по Кыргызстану", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            vehicles.forEach { v ->
                FilterChip(
                    selected = state.cargoVehicle == v,
                    onClick = { onUpdateCargo(v, state.cargoLoaders) },
                    label = { Text(v) }
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Грузчики (+350 ${strings.currencySom} / чел): ${state.cargoLoaders}", fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalIconButton(onClick = { onUpdateCargo(state.cargoVehicle, state.cargoLoaders - 1) }) {
                    Icon(Icons.Default.Remove, contentDescription = "Fewer loaders")
                }
                FilledTonalIconButton(onClick = { onUpdateCargo(state.cargoVehicle, state.cargoLoaders + 1) }) {
                    Icon(Icons.Default.Add, contentDescription = "More loaders")
                }
            }
        }
    }
}

@Composable
fun IntercitySeatPickerPanel(
    strings: KgStrings,
    state: KgTaxiUiState,
    onSelectRoute: (IntercityRoute) -> Unit,
    onSelectScheduledTrip: (IntercityScheduledTrip) -> Unit,
    onToggleSeat: (Int) -> Unit,
    onRequestTourGuide: (String) -> Unit
) {
    val scheduledTrips = remember(state.selectedIntercityRoute.id) {
        KgGeoCatalog.intercityScheduleForRoute(state.selectedIntercityRoute.id)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Направление межгород", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KgGeoCatalog.intercityRoutes.forEach { route ->
                val selected = state.selectedIntercityRoute.id == route.id
                FilterChip(
                    selected = selected,
                    onClick = { onSelectRoute(route) },
                    label = { Text("${route.fromCity} → ${route.toCity} • ${route.seatPriceSom}с") }
                )
            }
        }

        // Live Scheduled Departures with real-time free seat badges
        IntercityLiveScheduleCard(
            route = state.selectedIntercityRoute,
            trips = scheduledTrips,
            selectedTripId = state.selectedIntercityTripId,
            onSelectTrip = onSelectScheduledTrip
        )

        // Interactive 6-Seat Cabin Visual Picker
        Text(strings.seatSelectionTitle, style = MaterialTheme.typography.titleSmall)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ПЕРЕДНЯЯ ЧАСТЬ САЛОНА (HONDA STEPWGN / ALPHARD)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Row 1: Driver + Front Seat #1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 110.dp, height = 56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Водитель", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    SeatBox(
                        seatNumber = 1,
                        label = "1 (Переднее +${state.selectedIntercityRoute.frontSeatExtraSom}с)",
                        occupied = 1 in state.occupiedIntercitySeats,
                        selected = 1 in state.selectedIntercitySeats,
                        onClick = { onToggleSeat(1) },
                        modifier = Modifier.width(136.dp)
                    )
                }

                // Row 2: Seats 2, 3, 4
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(2, 3, 4).forEach { seat ->
                        SeatBox(
                            seatNumber = seat,
                            label = "Место $seat",
                            occupied = seat in state.occupiedIntercitySeats,
                            selected = seat in state.selectedIntercitySeats,
                            onClick = { onToggleSeat(seat) },
                            modifier = Modifier.width(84.dp)
                        )
                    }
                }

                // Row 3: Seats 5, 6
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(5, 6).forEach { seat ->
                        SeatBox(
                            seatNumber = seat,
                            label = "Место $seat",
                            occupied = seat in state.occupiedIntercitySeats,
                            selected = seat in state.selectedIntercitySeats,
                            onClick = { onToggleSeat(seat) },
                            modifier = Modifier.width(116.dp)
                        )
                    }
                }

                // Legend
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(UberBlue, "Выбрано")
                    LegendDot(MaterialTheme.colorScheme.surface, "Свободно")
                    LegendDot(MaterialTheme.colorScheme.outline, "Занято")
                }
            }
        }

        // Live Kyrgyzstan Mountain Passes Status (Too-Ashuu, Boom Gorge, Ala-Bel, Otmok)
        MountainPassesStatusCard(passes = state.mountainPasses)

        // AI Audio Tour Guide along the intercity highway
        AiTourGuideCard(
            language = state.language,
            currentTopic = state.tourGuideTopic,
            storyText = state.tourGuideText,
            isLoading = state.tourGuideLoading,
            onRequestStory = onRequestTourGuide
        )
    }
}

@Composable
private fun SeatBox(
    seatNumber: Int,
    label: String,
    occupied: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = when {
        occupied -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        selected -> UberBlue
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = when {
        occupied -> MaterialTheme.colorScheme.onSurfaceVariant
        selected -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) UberBlue else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = !occupied) { onClick() }
            .padding(horizontal = 6.dp)
            .testTag("intercity_seat_$seatNumber"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (occupied) "Занято" else label,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2
        )
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
                .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(3.dp))
        )
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun FoodDeliveryPanel(
    strings: KgStrings,
    state: KgTaxiUiState,
    onSelectVenue: (String) -> Unit,
    onAddFood: (FoodVenue, FoodMenuItem) -> Unit,
    onRemoveFood: (String) -> Unit
) {
    val filteredVenues = KgGeoCatalog.foodVenues.filter {
        it.rating >= state.filterMinRating && it.priceLevel <= state.filterMaxPriceLevel
    }.ifEmpty { KgGeoCatalog.foodVenues }
    val activeVenue = filteredVenues.firstOrNull { it.id == state.selectedVenueId } ?: filteredVenues.first()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Editorial Kyrgyz Food Hero Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_kg_food_hero_1795421299190),
                contentDescription = "Kyrgyz food delivery",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(14.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text(
                        text = "KG Eats • Доставка из любимых заведений",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (state.profile.kgOneActive) "KG One активен: бесплатная доставка 0 сом!" else "Доставка от 20 мин • Бесплатно с KG One",
                        color = if (state.profile.kgOneActive) UberGreenDark else Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Venues Carousel
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filteredVenues.forEach { venue ->
                val selected = venue.id == activeVenue.id
                Surface(
                    onClick = { onSelectVenue(venue.id) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.testTag("venue_${venue.id}")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = venue.name,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "★ ${venue.rating} (${venue.reviewCount}) • ${venue.deliveryMin} мин",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Menu Items
        activeVenue.menu.forEach { item ->
            val qty = state.foodCart.firstOrNull { it.item.id == item.id }?.quantity ?: 0
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
                        Text(item.name, fontWeight = FontWeight.Bold)
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${item.priceSom} ${strings.currencySom} • ${item.weightGrams} г",
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            color = UberBlue,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (qty > 0) {
                            IconButton(onClick = { onRemoveFood(item.id) }) {
                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove")
                            }
                            Text("$qty", fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = { onAddFood(activeVenue, item) },
                            modifier = Modifier.testTag("add_food_${item.id}")
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        if (state.foodCart.isNotEmpty()) {
            val count = state.foodCart.sumOf { it.quantity }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = UberGreen.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "В корзине: $count поз. • Нажмите «${strings.orderRide}» ниже для оформления",
                    color = UberGreen,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
