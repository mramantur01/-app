package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.firebase.OwnerApplication
import com.example.data.firebase.OwnerDriver
import com.example.data.firebase.RegPhoto
import com.example.data.model.CarCatalog
import com.example.ui.KgTaxiUiState
import com.example.ui.theme.KgInk
import com.example.ui.theme.KgTurquoise
import com.example.ui.theme.KgTurquoiseLight

/**
 * Owner panel (role "admin"): today's numbers, driver applications with document and car photos
 * (approve / reject), and the list of drivers (block / unblock).
 */
@Composable
fun OwnerPanel(
    state: KgTaxiUiState,
    onOpenPhotos: (String) -> Unit,
    onClosePhotos: () -> Unit,
    onApprove: (OwnerApplication) -> Unit,
    onReject: (String, String) -> Unit,
    onSetBlocked: (String, Boolean) -> Unit,
    onSaveConfig: (com.example.data.firebase.AppConfig) -> Unit = {},
    onResolveTicket: (String) -> Unit = {},
    onSetStatsDays: (Int) -> Unit = {},
    onExportCsv: () -> Unit = {},
    onLoadBalances: () -> Unit = {},
    onAddPayment: (String, Int) -> Unit = { _, _ -> }
) {
    var tab by remember { mutableIntStateOf(0) }
    val pending = state.ownerApplications.filter { it.status == "pending" }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().testTag("owner_panel")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Панель владельца", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = KgInk)
            // Period: today / 7 days / 30 days
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(1 to "Сегодня", 7 to "7 дней", 30 to "30 дней").forEach { (d, label) ->
                    FilterChip(
                        selected = state.ownerStatsDays == d,
                        onClick = { onSetStatsDays(d) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = KgTurquoise, selectedLabelColor = Color.White)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OwnerStat("Заказов", "${state.ownerStats.ridesTotal}", Modifier.weight(1f))
                OwnerStat("Выполнено", "${state.ownerStats.ridesDone}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OwnerStat("Выручка", "${state.ownerStats.revenueSom} сом", Modifier.weight(1f))
                val commission = state.ownerStats.revenueSom * state.ownerBankConfig.commissionPercent / 100
                OwnerStat("Комиссия ${state.ownerBankConfig.commissionPercent}%", "$commission сом", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OwnerStat("Водителей", "${state.ownerDrivers.size}", Modifier.weight(1f))
                OwnerStat("Отмен", "${state.ownerStats.ridesCancelled}", Modifier.weight(1f))
            }
            if (state.ownerStats.topDrivers.isNotEmpty()) {
                Text("Лучшие водители", fontWeight = FontWeight.Bold, color = KgInk)
                state.ownerStats.topDrivers.forEachIndexed { i, (name, trips, revenue) ->
                    Row {
                        Text("${i + 1}. $name", color = KgInk, modifier = Modifier.weight(1f))
                        Text("$trips поезд. • $revenue сом", color = KgInk.copy(alpha = 0.7f))
                    }
                }
            }
            if (state.ownerStats.cancelReasons.isNotEmpty()) {
                Text("Причины отмен", fontWeight = FontWeight.Bold, color = KgInk)
                state.ownerStats.cancelReasons.take(6).forEach { (reason, n) ->
                    Row {
                        Text(reason, color = KgInk, modifier = Modifier.weight(1f))
                        Text("$n", color = KgInk.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                    }
                }
            }
            OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth().testTag("owner_export_csv")) {
                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Выгрузить в таблицу (Excel)")
            }

            val openTickets = state.supportTickets.count { it.status == "open" }
            ScrollableTabRow(selectedTabIndex = tab, containerColor = Color.White, contentColor = KgTurquoise, edgePadding = 0.dp) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Заявки (${pending.size})") }, modifier = Modifier.testTag("owner_tab_apps"))
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Водители") }, modifier = Modifier.testTag("owner_tab_drivers"))
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Цены") }, modifier = Modifier.testTag("owner_tab_prices"))
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Поддержка ($openTickets)") }, modifier = Modifier.testTag("owner_tab_support"))
            }
            when (tab) {
                0 -> {
                    if (state.ownerApplications.isEmpty()) EmptyNote("Заявок пока нет")
                    state.ownerApplications.forEach { app ->
                        ApplicationCard(app, onOpen = { onOpenPhotos(app.uid) })
                    }
                }
                1 -> {
                    LaunchedEffect(state.ownerDrivers.size) { onLoadBalances() }
                    if (state.ownerDrivers.isEmpty() && state.ownerBlockedDrivers.isEmpty()) EmptyNote("Водителей пока нет")
                    val totalDebt = state.ownerDriverBalances.values.sumOf { it.debtSom.coerceAtLeast(0) }
                    if (totalDebt > 0) Text("Всего долг водителей: $totalDebt сом", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                    (state.ownerDrivers + state.ownerBlockedDrivers).forEach { d ->
                        DriverRow(d, onSetBlocked, state.ownerDriverBalances[d.uid], onAddPayment)
                    }
                }
                2 -> PricesEditor(state, onSaveConfig)
                else -> {
                    if (state.supportTickets.isEmpty()) EmptyNote("Обращений пока нет")
                    state.supportTickets.forEach { t -> TicketRow(t, onResolveTicket) }
                }
            }
        }
    }

    val openUid = state.ownerPhotosUid
    val openApp = state.ownerApplications.firstOrNull { it.uid == openUid }
    if (openApp != null) {
        ApplicationDialog(
            app = openApp,
            photos = state.ownerPhotos,
            onApprove = { onApprove(openApp); onClosePhotos() },
            onReject = { reason -> onReject(openApp.uid, reason); onClosePhotos() },
            onDismiss = onClosePhotos
        )
    }
}

/** Tariff prices, commission and the first-ride promo. Saved to config/app, applied for everyone at once. */
@Composable
private fun PricesEditor(state: KgTaxiUiState, onSave: (com.example.data.firebase.AppConfig) -> Unit) {
    val cfg = state.appConfig
    val ids = com.example.data.model.KgGeoCatalog.taxiTariffs.map { it.id }.filter { it in com.example.FeatureFlags.ENABLED_TARIFF_IDS }
    val defaults = com.example.data.model.KgGeoCatalog.taxiTariffs.associateBy { it.id }
    val base = remember(cfg) { mutableStateMapOf<String, String>().apply { ids.forEach { put(it, (cfg.tariffs[it]?.baseSom ?: defaults[it]?.baseSom ?: 0).toString()) } } }
    val perKm = remember(cfg) { mutableStateMapOf<String, String>().apply { ids.forEach { put(it, (cfg.tariffs[it]?.perKmSom ?: defaults[it]?.perKmSom ?: 0).toString()) } } }
    var commission by remember(cfg) { mutableStateOf((cfg.commissionPercent ?: state.ownerBankConfig.commissionPercent).toString()) }
    var promoCode by remember(cfg) { mutableStateOf(cfg.promoCode) }
    var promoPercent by remember(cfg) { mutableStateOf(cfg.promoPercent.takeIf { it > 0 }?.toString() ?: "20") }
    var promoActive by remember(cfg) { mutableStateOf(cfg.promoActive) }
    val routes = com.example.data.model.KgGeoCatalog.intercityRoutes
    val seat = remember(cfg) { mutableStateMapOf<String, String>().apply { routes.forEach { put(it.id, (cfg.intercity[it.id] ?: it.seatPriceSom).toString()) } } }
    var referral by remember(cfg) { mutableStateOf(cfg.referralPercent.toString()) }
    var goalTrips by remember(cfg) { mutableStateOf(cfg.goalTrips.toString()) }
    var goalBonus by remember(cfg) { mutableStateOf(cfg.goalBonusSom.toString()) }
    var debtLimit by remember(cfg) { mutableStateOf(cfg.debtLimitSom.toString()) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Цены тарифов (сом)", fontWeight = FontWeight.Bold, color = KgInk)
        ids.forEach { id ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(CarCatalog.tariffLabel(id), modifier = Modifier.width(84.dp), color = KgInk)
                NumField(base[id].orEmpty(), { base[id] = it }, "Посадка", Modifier.weight(1f))
                NumField(perKm[id].orEmpty(), { perKm[id] = it }, "За км", Modifier.weight(1f))
            }
        }
        NumField(commission, { commission = it }, "Комиссия сервиса, %", Modifier.fillMaxWidth())
        HorizontalDivider()
        Text("Межгород: цена за место (сом)", fontWeight = FontWeight.Bold, color = KgInk)
        routes.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${r.fromCity} → ${r.toCity}", modifier = Modifier.weight(1.4f), color = KgInk)
                NumField(seat[r.id].orEmpty(), { seat[r.id] = it }, "За место", Modifier.weight(1f))
            }
        }
        HorizontalDivider()
        NumField(referral, { referral = it }, "«Пригласи друга»: скидка другу, %", Modifier.fillMaxWidth())
        HorizontalDivider()
        Text("Водители: цель дня и долг", fontWeight = FontWeight.Bold, color = KgInk)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(goalTrips, { goalTrips = it }, "Поездок за день", Modifier.weight(1f))
            NumField(goalBonus, { goalBonus = it }, "Бонус, сом", Modifier.weight(1f))
        }
        NumField(debtLimit, { debtLimit = it }, "Макс. долг для выхода на линию, сом (0 — без лимита)", Modifier.fillMaxWidth())
        HorizontalDivider()
        Text("Промокод на первую поездку", fontWeight = FontWeight.Bold, color = KgInk)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = promoCode,
                onValueChange = { promoCode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(20) },
                label = { Text("Код") },
                singleLine = true,
                modifier = Modifier.weight(1.4f)
            )
            NumField(promoPercent, { promoPercent = it }, "Скидка %", Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Промокод включён", modifier = Modifier.weight(1f), color = KgInk)
            Switch(checked = promoActive, onCheckedChange = { promoActive = it })
        }
        Button(
            onClick = {
                onSave(
                    com.example.data.firebase.AppConfig(
                        commissionPercent = commission.toIntOrNull() ?: 14,
                        tariffs = ids.associateWith { id ->
                            com.example.data.firebase.TariffPrice(base[id]?.toIntOrNull() ?: 0, perKm[id]?.toIntOrNull() ?: 0)
                        },
                        promoCode = promoCode,
                        promoPercent = promoPercent.toIntOrNull() ?: 0,
                        promoActive = promoActive && promoCode.isNotBlank(),
                        intercity = routes.associate { it.id to (seat[it.id]?.toIntOrNull() ?: it.seatPriceSom) },
                        referralPercent = referral.toIntOrNull() ?: 20,
                        goalTrips = goalTrips.toIntOrNull() ?: 0,
                        goalBonusSom = goalBonus.toIntOrNull() ?: 0,
                        debtLimitSom = debtLimit.toIntOrNull() ?: 0
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = KgTurquoise),
            modifier = Modifier.fillMaxWidth().testTag("owner_save_prices")
        ) { Text("Сохранить для всех") }
        Text(
            "Новые цены сразу видят все пассажиры, обновлять приложение не нужно.",
            style = MaterialTheme.typography.bodySmall,
            color = KgInk.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun NumField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun TicketRow(t: com.example.data.firebase.SupportTicket, onResolve: (String) -> Unit) {
    val context = LocalContext.current
    val date = remember(t.createdAtMillis) {
        if (t.createdAtMillis > 0) java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.US).format(java.util.Date(t.createdAtMillis)) else ""
    }
    Surface(shape = RoundedCornerShape(14.dp), color = if (t.status == "open") Color(0xFFFFF8E1) else Color(0xFFF7FAFA), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(t.name, fontWeight = FontWeight.Bold, color = KgInk, modifier = Modifier.weight(1f))
                Text(date, style = MaterialTheme.typography.labelMedium, color = KgInk.copy(alpha = 0.6f))
            }
            if (t.rideId != null) Text("Жалоба на поездку", style = MaterialTheme.typography.labelMedium, color = Color(0xFFC62828))
            Text(t.text, color = KgInk)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (t.phone.isNotBlank()) {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${t.phone}")))
                        }
                    }) { Text("Позвонить ${t.phone}") }
                }
                if (t.status == "open") {
                    TextButton(onClick = { onResolve(t.id) }) { Text("Решено") }
                } else {
                    Text("Решено", color = Color(0xFF2E7D32), modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun OwnerStat(label: String, value: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = KgTurquoiseLight, modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = KgInk.copy(alpha = 0.7f))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = KgInk)
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, color = KgInk.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 8.dp))
}

private fun statusLabel(status: String) = when (status) {
    "approved" -> "Одобрен"
    "rejected" -> "Отклонён"
    else -> "На проверке"
}

@Composable
private fun ApplicationCard(app: OwnerApplication, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF7FAFA),
        modifier = Modifier.fillMaxWidth().testTag("owner_app_${app.uid}")
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(app.fullName, fontWeight = FontWeight.Bold, color = KgInk)
                Text("${app.car} • ${app.plate}", style = MaterialTheme.typography.bodySmall, color = KgInk.copy(alpha = 0.75f))
                Text(
                    "${app.city} • тарифы: ${app.tariffs.joinToString { CarCatalog.tariffLabel(it) }.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = KgInk.copy(alpha = 0.75f)
                )
            }
            val color = when (app.status) { "approved" -> Color(0xFF2E7D32); "rejected" -> Color(0xFFC62828); else -> KgTurquoise }
            Text(statusLabel(app.status), color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DriverRow(
    d: OwnerDriver,
    onSetBlocked: (String, Boolean) -> Unit,
    balance: com.example.data.model.DriverBalance? = null,
    onAddPayment: (String, Int) -> Unit = { _, _ -> }
) {
    var payOpen by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    if (payOpen) {
        AlertDialog(
            onDismissRequest = { payOpen = false },
            title = { Text("Оплата от водителя") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${d.name} • долг ${balance?.debtSom ?: 0} сом")
                    NumField(amount, { amount = it }, "Получено, сом", Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = { onAddPayment(d.uid, amount.toIntOrNull() ?: 0); amount = ""; payOpen = false }, enabled = (amount.toIntOrNull() ?: 0) > 0) { Text("Записать") }
            },
            dismissButton = { TextButton(onClick = { payOpen = false }) { Text("Отмена") } }
        )
    }
    Column {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(d.name.ifBlank { d.uid.take(8) }, fontWeight = FontWeight.Bold, color = if (d.blocked) Color.Gray else KgInk)
            Text("${d.car} • ${d.plate}", style = MaterialTheme.typography.bodySmall, color = KgInk.copy(alpha = 0.7f))
            if (balance != null) Text(
                if (balance.debtSom > 0) "Долг: ${balance.debtSom} сом" else "Долга нет",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (balance.debtSom > 0) Color(0xFFC62828) else KgTurquoise
            )
        }
        if (d.blocked) {
            TextButton(onClick = { onSetBlocked(d.uid, false) }) { Text("Разблокировать") }
        } else {
            TextButton(onClick = { onSetBlocked(d.uid, true) }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFC62828))) {
                Text("Заблокировать")
            }
        }
    }
    if (balance != null && balance.debtSom > 0) TextButton(onClick = { payOpen = true }, modifier = Modifier.testTag("owner_driver_pay_${d.uid}")) { Text("Принять оплату") }
    }
}

@Composable
private fun ApplicationDialog(
    app: OwnerApplication,
    photos: Map<String, String>,
    onApprove: () -> Unit,
    onReject: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var rejecting by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.fullName, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Статус: ${statusLabel(app.status)}")
                Text("Телефон: ${app.phone}")
                Text("Город: ${app.city}")
                Text("Паспорт: ${app.passportNumber.ifBlank { "—" }}")
                Text("ВУ: ${app.licenseNumber} • стаж ${app.experienceYears} лет")
                Text("Авто: ${app.car} • ${app.plate}")
                Text("Тарифы: ${app.tariffs.joinToString { CarCatalog.tariffLabel(it) }.ifBlank { "—" }}")
                if (app.payPhone.isNotBlank()) Text("Для переводов: ${app.payPhone}")
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                if (photos.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = KgTurquoise)
                        Text("Загружаем фото…")
                    }
                }
                RegPhoto.entries.forEach { kind ->
                    photos[kind.id]?.let { b64 -> PhotoPreview(kind.title, b64) }
                }
                if (rejecting) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(200) },
                        label = { Text("Причина отказа") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (app.status != "approved") {
                Button(
                    onClick = { if (rejecting) onReject(reason) else onApprove() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (rejecting) Color(0xFFC62828) else KgTurquoise),
                    modifier = Modifier.testTag("owner_confirm")
                ) { Text(if (rejecting) "Отклонить" else "Одобрить") }
            }
        },
        dismissButton = {
            Row {
                if (app.status == "pending" && !rejecting) {
                    TextButton(onClick = { rejecting = true }) { Text("Отказать", color = Color(0xFFC62828)) }
                }
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        }
    )
}

@Composable
private fun PhotoPreview(title: String, base64: String) {
    val bitmap = remember(base64) {
        runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(12.dp))
            )
        } else {
            Text("Не удалось открыть фото", color = Color.Gray)
        }
    }
}
