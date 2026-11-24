package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    modifier: Modifier = Modifier
) {
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
                onClick = onOpenMyRides
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

        Text("Недавние", fontWeight = FontWeight.Bold, color = KgInk)
        RecentRow(
            icon = Icons.Default.Home,
            title = "Дом",
            subtitle = state.profile.homeAddress.ifBlank { "Укажите адрес в профиле" },
            onClick = { viewModel.goToSavedAddress("Дом", state.profile.homeAddress) }
        )
        RecentRow(
            icon = Icons.Default.Work,
            title = "Работа",
            subtitle = state.profile.workAddress.ifBlank { "Укажите адрес в профиле" },
            onClick = { viewModel.goToSavedAddress("Работа", state.profile.workAddress) }
        )
        recent.forEach { r ->
            RecentRow(
                icon = Icons.Default.Schedule,
                title = r.dropoffAddress,
                subtitle = r.pickupAddress.let { "из: $it" },
                onClick = { viewModel.goToRecentDestination(r) }
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
