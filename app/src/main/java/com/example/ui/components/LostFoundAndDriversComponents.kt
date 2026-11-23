package com.example.ui.components

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.LocalTripEntity
import com.example.data.model.LostItemTicket
import com.example.ui.theme.*

@Composable
fun LostFoundTicketDialog(
    trip: LocalTripEntity,
    onSubmitTicket: (category: String, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    val categories = listOf("Телефон", "Ключи", "Сумка / Рюкзак", "Документы", "Наушники", "Другое")
    var selectedCategory by remember { mutableStateOf(categories.first()) }
    var note by remember { mutableStateOf(trip.lostItemNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.WorkOutline, contentDescription = null, tint = UberBlue)
                Text("Бюро находок (Lost & Found)", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Поездка с водителем ${trip.driverName} (${trip.driverCar})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Описание вещи (цвет, где лежала)") },
                    placeholder = { Text("Например: чёрный iPhone на заднем сиденье") },
                    modifier = Modifier.fillMaxWidth().testTag("lost_found_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmitTicket(selectedCategory, note.ifBlank { selectedCategory })
                    onDismiss()
                },
                modifier = Modifier.testTag("submit_lost_found_ticket_button")
            ) {
                Text("Отправить водителю")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun LostFoundAndFavoriteDriversSection(
    tickets: List<LostItemTicket>,
    favoriteDrivers: Set<String>,
    blockedDrivers: Set<String>,
    onToggleFavoriteDriver: (String) -> Unit,
    onToggleBlockedDriver: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Favorite & Blocked Drivers Management
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = UberGreen, modifier = Modifier.size(18.dp))
                    Text("Избранные и заблокированные водители", fontWeight = FontWeight.Bold)
                }
                if (favoriteDrivers.isEmpty()) {
                    Text(
                        "Ставьте ♥ водителю после поездки, чтобы получать приоритетную подачу.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        favoriteDrivers.forEach { name ->
                            InputChip(
                                selected = true,
                                onClick = { onToggleFavoriteDriver(name) },
                                label = { Text("★ $name") },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove favorite", modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }
                if (blockedDrivers.isNotEmpty()) {
                    Text("Исключены из подачи:", style = MaterialTheme.typography.labelSmall, color = UberRed)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        blockedDrivers.forEach { name ->
                            InputChip(
                                selected = false,
                                onClick = { onToggleBlockedDriver(name) },
                                label = { Text("✕ $name", color = UberRed) },
                                trailingIcon = { Icon(Icons.Default.Undo, contentDescription = "Unblock", modifier = Modifier.size(14.dp)) }
                            )
                        }
                    }
                }
            }
        }

        // Lost & Found Support Tickets
        if (tickets.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = UberBlue.copy(alpha = 0.09f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.SupportAgent, contentDescription = null, tint = UberBlue, modifier = Modifier.size(18.dp))
                        Text("Заявки Бюро находок (${tickets.size})", fontWeight = FontWeight.Bold, color = UberBlue)
                    }
                    tickets.forEach { t ->
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${t.itemCategory} • ${t.driverName}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(t.note, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                                Surface(shape = RoundedCornerShape(8.dp), color = UberGreen.copy(alpha = 0.16f)) {
                                    Text(
                                        text = t.status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = UberGreen,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
