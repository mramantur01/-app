package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.model.KgSettlements
import com.example.data.model.SettlementType
import com.example.ui.theme.KgTurquoise
import com.example.ui.theme.KgTurquoiseDark

/** Read-only chip with the city detected by GPS; tap opens the list of regions and cities. */
@Composable
fun DetectedCityChip(cityName: String, modifier: Modifier = Modifier, onMap: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { open = true },
        label = { Text(cityName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp), tint = KgTurquoiseDark) },
        colors = if (onMap) AssistChipDefaults.assistChipColors(containerColor = androidx.compose.ui.graphics.Color.White) else AssistChipDefaults.assistChipColors(),
        elevation = if (onMap) AssistChipDefaults.assistChipElevation(elevation = 4.dp) else null,
        modifier = modifier.testTag("detected_city_chip")
    )
    if (open) RegionsSheet(currentCity = cityName, onDismiss = { open = false })
}

/** All regions, districts, cities and urban-type settlements of Kyrgyzstan (view only). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionsSheet(currentCity: String, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var openRegion by remember { mutableStateOf<String?>(KgSettlements.byName(currentCity)?.region) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text("Регионы и города", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Surface(shape = RoundedCornerShape(14.dp), color = KgTurquoise.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = KgTurquoiseDark)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Вы здесь: $currentCity", fontWeight = FontWeight.Bold)
                        Text(
                            "Город определяется автоматически по геолокации телефона — заказы и водители рядом с вами.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Поиск: город, район, село") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).testTag("regions_search")
            )
            val q = query.trim()
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                if (q.isNotEmpty()) {
                    val found = KgSettlements.all.filter {
                        it.name.contains(q, ignoreCase = true) || it.district.contains(q, ignoreCase = true) || it.region.contains(q, ignoreCase = true)
                    }
                    if (found.isEmpty()) item { Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(found) { s -> SettlementRow(s.name, typeLabel(s.type, s.districtCenter), "${s.region}${if (s.district.isNotBlank()) " • ${s.district}" else ""}", s.name == currentCity) }
                } else {
                    items(KgSettlements.regions) { r ->
                        val expanded = openRegion == r.name
                        val inRegion = KgSettlements.all.filter { it.region == r.name }
                        Column {
                            Surface(onClick = { openRegion = if (expanded) null else r.name }, color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(r.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Центр: ${r.center}${if (r.districts.isNotEmpty()) " • районов: ${r.districts.size}" else ""} • пунктов: ${inRegion.size}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                                }
                            }
                            if (expanded) {
                                if (r.districts.isNotEmpty()) {
                                    Text("Районы", style = MaterialTheme.typography.labelLarge, color = KgTurquoiseDark, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                                    r.districts.forEach { d ->
                                        Text("• ${d.name} — центр ${d.center}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
                                    }
                                }
                                Text("Города и посёлки", style = MaterialTheme.typography.labelLarge, color = KgTurquoiseDark, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
                                inRegion.sortedBy { it.type.ordinal }.forEach { s ->
                                    SettlementRow(s.name, typeLabel(s.type, s.districtCenter), s.district, s.name == currentCity)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

private fun typeLabel(t: SettlementType, dc: Boolean): String =
    if (dc && t != SettlementType.DISTRICT_CENTER) "${t.label}, районный центр" else t.label

@Composable
private fun SettlementRow(name: String, type: String, place: String, current: Boolean) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (current) Icons.Default.MyLocation else Icons.Default.Place,
            contentDescription = null,
            tint = if (current) KgTurquoiseDark else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(if (current) "$name — вы здесь" else name, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
            Text(listOf(type, place).filter { it.isNotBlank() }.joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


/** Regions → districts and settlements, each region opens and closes (for the passenger home page). */
@Composable
fun RegionsBrowser(currentCity: String) {
    var openRegion by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().testTag("regions_browser")) {
        Text(
            "Вы здесь: $currentCity — город определяется по геолокации.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        KgSettlements.regions.forEach { r ->
            val expanded = openRegion == r.name
            val inRegion = KgSettlements.all.filter { it.region == r.name }
            Surface(onClick = { openRegion = if (expanded) null else r.name }, color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Центр: ${r.center}${if (r.districts.isNotEmpty()) " • районов: ${r.districts.size}" else ""} • пунктов: ${inRegion.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "Свернуть" else "Развернуть")
                }
            }
            if (expanded) {
                if (r.districts.isNotEmpty()) {
                    Text("Районы", style = MaterialTheme.typography.labelLarge, color = KgTurquoiseDark, modifier = Modifier.padding(start = 8.dp))
                    r.districts.forEach { d ->
                        Text("• ${d.name} — центр ${d.center}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
                    }
                }
                Text("Города и посёлки", style = MaterialTheme.typography.labelLarge, color = KgTurquoiseDark, modifier = Modifier.padding(start = 8.dp, top = 8.dp))
                inRegion.sortedBy { it.type.ordinal }.forEach { st ->
                    SettlementRow(st.name, typeLabel(st.type, st.districtCenter), st.district, st.name == currentCity)
                }
            }
            HorizontalDivider()
        }
    }
}
