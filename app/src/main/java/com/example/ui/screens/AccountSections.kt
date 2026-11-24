package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.firebase.RideStatus
import com.example.data.firebase.SharedRide
import com.example.ui.KgTaxiUiState
import com.example.ui.KgTaxiViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Profile: "Мои поездки" (real orders) with repeat and complaint, support message, delete account. */
@Composable
fun AccountExtrasSection(state: KgTaxiUiState, viewModel: KgTaxiViewModel, onClose: () -> Unit) {
    var supportRide by remember { mutableStateOf<SharedRide?>(null) }
    var showSupport by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().testTag("my_rides_section")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Мои поездки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val rides = state.passengerRides
            if (rides.isEmpty()) {
                Text("Здесь появятся ваши заказы.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            (if (showAll) rides else rides.take(5)).forEach { r ->
                RideRow(
                    ride = r,
                    onRepeat = { viewModel.repeatRide(r); onClose() },
                    onComplain = { supportRide = r; showSupport = true }
                )
            }
            if (rides.size > 5 && !showAll) {
                TextButton(onClick = { showAll = true }) { Text("Показать все (${rides.size})") }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { supportRide = null; showSupport = true },
            modifier = Modifier.weight(1f).testTag("support_button")
        ) {
            Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Поддержка")
        }
        OutlinedButton(
            onClick = { showDelete = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC62828)),
            modifier = Modifier.weight(1f).testTag("delete_account_button")
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Удалить аккаунт", maxLines = 1)
        }
    }

    if (showSupport) {
        SupportDialog(
            ride = supportRide,
            sending = state.supportSending,
            onSend = { text -> viewModel.sendSupportMessage(text, supportRide?.id); showSupport = false },
            onDismiss = { showSupport = false }
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { if (!state.accountDeleting) showDelete = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC62828)) },
            title = { Text("Удалить аккаунт навсегда?") },
            text = {
                Text(
                    "Удалим профиль, телефон, адреса, регистрацию, фото документов и заявку водителя. " +
                        "Это нельзя отменить. Записи о выполненных поездках остаются у второй стороны поездки."
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAccount() },
                    enabled = !state.accountDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier.testTag("confirm_delete_account")
                ) { Text(if (state.accountDeleting) "Удаляем…" else "Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }, enabled = !state.accountDeleting) { Text("Отмена") }
            }
        )
    }
}

private fun statusText(s: String) = when (s) {
    RideStatus.COMPLETED -> "Выполнен"
    RideStatus.CANCELLED -> "Отменён"
    RideStatus.BOOKED -> "Предзаказ принят"
    RideStatus.SEARCHING -> "Ищем водителя"
    else -> "В пути"
}

@Composable
private fun RideRow(ride: SharedRide, onRepeat: () -> Unit, onComplain: () -> Unit) {
    val date = remember(ride.createdAtMillis) {
        if (ride.createdAtMillis > 0) SimpleDateFormat("dd.MM HH:mm", Locale.US).format(Date(ride.createdAtMillis)) else ""
    }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("${ride.priceSom} сом", fontWeight = FontWeight.Bold)
            }
            Text(
                "${ride.pickupAddress} → ${ride.dropoffAddress}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(statusText(ride.status), ride.driverName?.takeIf { it.isNotBlank() }, ride.driverCar?.takeIf { it.isNotBlank() })
                    .joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = if (ride.status == RideStatus.CANCELLED) Color(0xFFC62828) else MaterialTheme.colorScheme.primary
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

@Composable
private fun SupportDialog(ride: SharedRide?, sending: Boolean, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (ride != null) "Жалоба на поездку" else "Написать в поддержку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ride != null) {
                    Text("${ride.pickupAddress} → ${ride.dropoffAddress}", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(1000) },
                    label = { Text("Опишите, что случилось") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("support_text")
                )
                Text(
                    "Ответим в ближайшее время. Если опасно — звоните 112.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSend(text) }, enabled = text.isNotBlank() && !sending, modifier = Modifier.testTag("support_send")) {
                Text("Отправить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
