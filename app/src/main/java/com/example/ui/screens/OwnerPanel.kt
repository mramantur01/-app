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
    onSetBlocked: (String, Boolean) -> Unit
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
            // Today's numbers
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OwnerStat("Заказов сегодня", "${state.ownerStats.ridesTotal}", Modifier.weight(1f))
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

            TabRow(selectedTabIndex = tab, containerColor = Color.White, contentColor = KgTurquoise) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Заявки (${pending.size})") }, modifier = Modifier.testTag("owner_tab_apps"))
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Водители") }, modifier = Modifier.testTag("owner_tab_drivers"))
            }
            when (tab) {
                0 -> {
                    if (state.ownerApplications.isEmpty()) EmptyNote("Заявок пока нет")
                    state.ownerApplications.forEach { app ->
                        ApplicationCard(app, onOpen = { onOpenPhotos(app.uid) })
                    }
                }
                else -> {
                    if (state.ownerDrivers.isEmpty() && state.ownerBlockedDrivers.isEmpty()) EmptyNote("Водителей пока нет")
                    (state.ownerDrivers + state.ownerBlockedDrivers).forEach { d ->
                        DriverRow(d, onSetBlocked)
                    }
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
private fun DriverRow(d: OwnerDriver, onSetBlocked: (String, Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(d.name.ifBlank { d.uid.take(8) }, fontWeight = FontWeight.Bold, color = if (d.blocked) Color.Gray else KgInk)
            Text("${d.car} • ${d.plate}", style = MaterialTheme.typography.bodySmall, color = KgInk.copy(alpha = 0.7f))
        }
        if (d.blocked) {
            TextButton(onClick = { onSetBlocked(d.uid, false) }) { Text("Разблокировать") }
        } else {
            TextButton(onClick = { onSetBlocked(d.uid, true) }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFC62828))) {
                Text("Заблокировать")
            }
        }
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
