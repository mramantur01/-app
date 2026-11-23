package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.EmergencySosAlert
import com.example.data.model.KgCity
import com.example.data.model.NearbyDriverMarker
import com.example.ui.theme.*

@Composable
fun DispatcherFleetMapAndSosPanel(
    city: KgCity,
    drivers: List<NearbyDriverMarker>,
    sosAlerts: List<EmergencySosAlert>,
    darkTheme: Boolean,
    onSimulateSos: () -> Unit,
    onResolveSos: (String) -> Unit
) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Live Fleet Radar Map Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Карта автопарка (${city.name})", fontWeight = FontWeight.Bold)
                        Text("${drivers.size + 18} бортов онлайн • Green EV выделены зелёным", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(
                        onClick = onSimulateSos,
                        modifier = Modifier.testTag("dispatcher_test_sos_button")
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = UberRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Тест SOS", color = UberRed)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (darkTheme) Color(0xFF12161E) else Color(0xFFE9EEF4))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val gridColor = if (darkTheme) Color(0xFF232A36) else Color(0xFFD5DDE7)
                        for (i in 1..6) {
                            drawLine(gridColor, Offset(w * i / 7f, 0f), Offset(w * i / 7f, h), strokeWidth = 2f)
                        }
                        for (j in 1..4) {
                            drawLine(gridColor, Offset(0f, h * j / 5f), Offset(w, h * j / 5f), strokeWidth = 2f)
                        }
                        // Draw surge rings
                        city.surgeZones.forEachIndexed { idx, _ ->
                            val cx = w * (0.28f + idx * 0.24f).coerceAtMost(0.84f)
                            val cy = h * (0.35f + (idx % 2) * 0.3f)
                            drawCircle(UberAmber.copy(alpha = 0.2f), radius = 34f, center = Offset(cx, cy))
                        }
                        // Draw live fleet dots
                        val spanLat = 0.06
                        val spanLng = 0.08
                        drivers.forEach { d ->
                            val x = (((d.point.lng - (city.center.lng - spanLng / 2)) / spanLng).coerceIn(0.08, 0.92) * w).toFloat()
                            val y = ((1.0 - ((d.point.lat - (city.center.lat - spanLat / 2)) / spanLat).coerceIn(0.1, 0.9)) * h).toFloat()
                            drawCircle(if (d.isEv) UberGreen else UberBlue, radius = 9f, center = Offset(x, y))
                            drawCircle(Color.White, radius = 3.5f, center = Offset(x, y))
                        }
                        // Highlight active SOS beacon if unresolved
                        if (sosAlerts.any { !it.resolved }) {
                            drawCircle(UberRed.copy(alpha = 0.3f), radius = 26f, center = Offset(w * 0.52f, h * 0.48f))
                            drawCircle(UberRed, radius = 11f, center = Offset(w * 0.52f, h * 0.48f))
                        }
                    }
                }
            }
        }

        // Emergency SOS Monitoring Panel
        val activeSos = sosAlerts.filterNot { it.resolved }
        if (activeSos.isNotEmpty()) {
            activeSos.forEach { alert ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = UberRed.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth().testTag("dispatcher_sos_card_${alert.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Emergency, contentDescription = null, tint = UberRed)
                            Text("ТРЕВОГА SOS • ${alert.city} (${alert.timeLabel})", fontWeight = FontWeight.ExtraBold, color = UberRed)
                        }
                        Text("Пассажир: ${alert.passengerName} • Водитель: ${alert.driverName}", fontWeight = FontWeight.SemiBold)
                        Text("${alert.carInfo} • ${alert.locationAddress}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:102")))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = UberRed, contentColor = Color.White),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Вызвать 102")
                            }
                            FilledTonalButton(
                                onClick = { onResolveSos(alert.id) },
                                modifier = Modifier.weight(1f).testTag("resolve_sos_${alert.id}")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Снять SOS")
                            }
                        }
                    }
                }
            }
        }
    }
}
