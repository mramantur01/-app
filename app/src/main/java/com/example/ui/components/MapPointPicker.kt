package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.GeoPoint
import com.example.ui.theme.KgInk
import com.example.ui.theme.KgTurquoise

/**
 * Full-screen map with a pin in the middle: move the map under the pin and press "Готово".
 * Used for "Откуда" and "Куда" now that the order screen has no map.
 */
@Composable
fun MapPointPickerDialog(
    title: String,
    start: GeoPoint,
    pinColor: Color,
    onPicked: (GeoPoint) -> Unit,
    onDismiss: () -> Unit
) {
    var center by remember { mutableStateOf(start) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.White, modifier = Modifier.fillMaxSize().testTag("map_point_picker")) {
            Box(modifier = Modifier.fillMaxSize()) {
                OsmMapLayer(
                    center = start,
                    centerKey = "picker",
                    pins = emptyList(),
                    route = emptyList(),
                    routeColor = 0xFF0097A7.toInt(),
                    darkTheme = false,
                    fitTo = emptyList(),
                    onTap = null,
                    zoom = 16.0,
                    onCenterChanged = { center = it },
                    modifier = Modifier.fillMaxSize()
                )
                // Fixed pin in the middle of the screen (its tip points at the center).
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = pinColor,
                    modifier = Modifier.align(Alignment.Center).size(52.dp).offset(y = (-26).dp)
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp).fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, fontWeight = FontWeight.Bold, color = KgInk)
                            Text("Двигайте карту, чтобы булавка стояла на нужном месте", style = MaterialTheme.typography.bodySmall, color = KgInk.copy(alpha = 0.7f))
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = KgInk) }
                    }
                }
                Button(
                    onClick = { onPicked(center) },
                    colors = ButtonDefaults.buttonColors(containerColor = KgTurquoise, contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 72.dp).fillMaxWidth().height(56.dp).testTag("map_point_done")
                ) { Text(com.example.i18n.t("Готово", "Даяр"), fontWeight = FontWeight.Bold) }
            }
        }
    }
}
