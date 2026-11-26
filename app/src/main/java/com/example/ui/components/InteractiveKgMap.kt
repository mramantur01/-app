package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.model.*
import com.example.i18n.KgStrings
import com.example.ui.theme.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import java.util.Locale

@Composable
fun InteractiveKgMap(
    strings: KgStrings,
    city: KgCity,
    pickupPoint: GeoPoint,
    dropoffPoint: GeoPoint,
    waypoints: List<KgPlace>,
    routePolyline: List<GeoPoint>,
    routeFromOsrm: Boolean,
    distanceKm: Double,
    durationMin: Int,
    nearbyDrivers: List<NearbyDriverMarker>,
    activeRide: ActiveRideState?,
    showSurgeHeatmap: Boolean,
    showTrafficOverlay: Boolean,
    mapPickModeIsDropoff: Boolean,
    darkTheme: Boolean,
    offlineMode: Boolean,
    mapLayerStyle: MapLayerStyle = MapLayerStyle.STANDARD,
    onSelectCity: (KgCity) -> Unit,
    onTapMapPoint: (GeoPoint) -> Unit,
    onTogglePickTarget: () -> Unit,
    onLocateGps: () -> Unit,
    onToggleSurge: () -> Unit,
    onToggleTraffic: () -> Unit,
    onCycleMapLayer: () -> Unit = {},
    onVoiceSearch: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasValidMapsKey = remember {
        BuildConfig.MAPS_API_KEY.isNotBlank() &&
            !BuildConfig.MAPS_API_KEY.contains("YOUR_GOOGLE_MAPS_API_KEY")
    }
    var forceVectorMap by remember { mutableStateOf(!hasValidMapsKey) }
    val useGoogleMaps = hasValidMapsKey && !offlineMode && !forceVectorMap

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
    ) {
        if (useGoogleMaps) {
            RealGoogleMapsLayer(
                city = city,
                pickupPoint = pickupPoint,
                dropoffPoint = dropoffPoint,
                waypoints = waypoints,
                routePolyline = routePolyline,
                nearbyDrivers = nearbyDrivers,
                activeRide = activeRide,
                showSurgeHeatmap = showSurgeHeatmap,
                showTrafficOverlay = showTrafficOverlay,
                mapLayerStyle = mapLayerStyle,
                onTapMapPoint = onTapMapPoint
            )
        } else if (!offlineMode) {
            // Free real street map (OpenStreetMap) when there is no Google Maps key.
            val route = if (routePolyline.size >= 2) routePolyline else listOf(pickupPoint, dropoffPoint)
            val pins = buildList {
                nearbyDrivers.forEach { d -> add(OsmPin(d.point, "", 0xFF3B82F6.toInt(), "KG Taxi", small = true)) }
                waypoints.forEachIndexed { i, wp -> add(OsmPin(wp.point, "${i + 1}", 0xFFFF9500.toInt(), wp.name)) }
                add(OsmPin(pickupPoint, "A", 0xFF0097A7.toInt(), "Подача"))
                add(OsmPin(dropoffPoint, "B", 0xFF1F2D30.toInt(), "Куда"))
                activeRide?.driverPoint?.let { add(OsmPin(it, "", 0xFF05944F.toInt(), activeRide.driverName, small = true)) }
            }
            OsmMapLayer(
                center = city.center,
                centerKey = city.name,
                pins = pins,
                route = route,
                routeColor = 0xFF0097A7.toInt(),
                darkTheme = darkTheme || mapLayerStyle == MapLayerStyle.NIGHT,
                fitTo = remember(pickupPoint, dropoffPoint) { listOf(pickupPoint, dropoffPoint) },
                onTap = onTapMapPoint,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            InteractiveVectorKgMapCanvas(
                city = city,
                pickupPoint = pickupPoint,
                dropoffPoint = dropoffPoint,
                waypoints = waypoints,
                routePolyline = routePolyline,
                nearbyDrivers = nearbyDrivers,
                activeRide = activeRide,
                showSurgeHeatmap = showSurgeHeatmap,
                showTrafficOverlay = showTrafficOverlay,
                darkTheme = darkTheme || mapLayerStyle == MapLayerStyle.NIGHT,
                satelliteMode = mapLayerStyle == MapLayerStyle.SATELLITE,
                onTapMapPoint = onTapMapPoint
            )
        }

        // City buttons: not during a ride (the map only shows the trip then).
        if (false) Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KgGeoCatalog.cities.forEach { c ->
                val selected = c.name == city.name
                Surface(
                    onClick = { onSelectCity(c) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 3.dp,
                    modifier = Modifier.testTag("city_chip_${c.nameEn.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = c.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Route Distance & ETA Floating Badge (Bottom Left)
        if (distanceKm > 0.0) Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Route,
                    contentDescription = "Route",
                    tint = UberBlue,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f", distanceKm)} ${strings.kmUnit}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontFamily = JetBrainsMono
                )
                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                )
                Text(
                    text = "$durationMin ${strings.minUnit}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontFamily = JetBrainsMono
                )
                // Technical route-source badge: only in demo builds.
                if (routeFromOsrm && com.example.FeatureFlags.DEMO_EXTRAS) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = UberGreen.copy(alpha = 0.24f)
                    ) {
                        Text(
                            text = "OSRM",
                            style = MaterialTheme.typography.labelSmall,
                            color = UberGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                if (city.effectiveSurge > 1.0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = UberAmber
                    ) {
                        Text(
                            text = "x${city.surgeMultiplier}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Right Floating Action Column: Traffic, Surge Heatmap, Voice Search, AI Route Planner, GPS
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Traffic and demand layers are drawn, not real data yet: hidden with the demo extras.
            if (com.example.FeatureFlags.DEMO_EXTRAS) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Live Traffic Layer Toggle
                Surface(
                    onClick = onToggleTraffic,
                    shape = RoundedCornerShape(14.dp),
                    color = if (showTrafficOverlay) UberGreen else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    modifier = Modifier.testTag("traffic_overlay_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Traffic,
                            contentDescription = "Пробки",
                            tint = if (showTrafficOverlay) Color.White else UberGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Пробки",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (showTrafficOverlay) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Surge Heatmap Toggle
                Surface(
                    onClick = onToggleSurge,
                    shape = RoundedCornerShape(14.dp),
                    color = if (showSurgeHeatmap) UberAmber else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                    modifier = Modifier.testTag("surge_heatmap_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Whatshot,
                            contentDescription = strings.surgeHeatmap,
                            tint = if (showSurgeHeatmap) Color.Black else UberRed,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = strings.surgeHeatmap,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (showSurgeHeatmap) Color.Black else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // AI Route Assistant Floating Pill
            if (com.example.FeatureFlags.AI_ASSISTANT) Surface(
                onClick = onOpenAiAssistant,
                shape = RoundedCornerShape(14.dp),
                color = UberBlue,
                shadowElevation = 4.dp,
                modifier = Modifier.testTag("ai_assistant_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = strings.aiAssistant,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "AI",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Voice Search Button
                if (com.example.FeatureFlags.VOICE_COMMANDS) SmallFloatingActionButton(
                    onClick = onVoiceSearch,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                    modifier = Modifier.testTag("voice_search_button")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = strings.voiceSearch, modifier = Modifier.size(20.dp))
                }

                // GPS "My Location" Button
                SmallFloatingActionButton(
                    onClick = onLocateGps,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.testTag("gps_locate_button")
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = strings.myLocation, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Point A / B pick toggle and map style: not during a ride.
        if (activeRide == null) Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 10.dp, end = 10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onTogglePickTarget,
                shape = RoundedCornerShape(10.dp),
                color = if (mapPickModeIsDropoff) MaterialTheme.colorScheme.surface.copy(alpha = 0.92f) else UberBlue,
                shadowElevation = 2.dp,
                modifier = Modifier.testTag("map_pick_target_toggle")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (mapPickModeIsDropoff) Icons.Default.PinDrop else Icons.Default.TripOrigin,
                        contentDescription = null,
                        tint = if (mapPickModeIsDropoff) UberBlue else Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (mapPickModeIsDropoff) "Тап: Точка Б" else "Тап: Точка А",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (mapPickModeIsDropoff) MaterialTheme.colorScheme.onSurface else Color.White
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onCycleMapLayer,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 2.dp,
                    modifier = Modifier.testTag("map_layer_cycle_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = "Map layer", tint = UberBlue, modifier = Modifier.size(13.dp))
                        Text(
                            text = mapLayerStyle.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    modifier = Modifier.clickable { if (hasValidMapsKey) forceVectorMap = !forceVectorMap }
                ) {
                    Text(
                        text = if (useGoogleMaps) "Google Maps" else if (offlineMode) "Офлайн КР" else "Карта КР",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RealGoogleMapsLayer(
    city: KgCity,
    pickupPoint: GeoPoint,
    dropoffPoint: GeoPoint,
    waypoints: List<KgPlace>,
    routePolyline: List<GeoPoint>,
    nearbyDrivers: List<NearbyDriverMarker>,
    activeRide: ActiveRideState?,
    showSurgeHeatmap: Boolean,
    showTrafficOverlay: Boolean,
    mapLayerStyle: MapLayerStyle,
    onTapMapPoint: (GeoPoint) -> Unit
) {
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(city.center.lat, city.center.lng), 12.8f)
    }
    LaunchedEffect(city.name) {
        cameraState.position = CameraPosition.fromLatLngZoom(LatLng(city.center.lat, city.center.lng), 12.8f)
    }

    val polyPoints = remember(routePolyline, pickupPoint, dropoffPoint) {
        if (routePolyline.size >= 2) {
            routePolyline.map { LatLng(it.lat, it.lng) }
        } else {
            listOf(
                LatLng(pickupPoint.lat, pickupPoint.lng),
                LatLng((pickupPoint.lat + dropoffPoint.lat) / 2.0 + 0.002, (pickupPoint.lng + dropoffPoint.lng) / 2.0 - 0.002),
                LatLng(dropoffPoint.lat, dropoffPoint.lng)
            )
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraState,
        properties = MapProperties(
            isTrafficEnabled = showTrafficOverlay,
            mapType = if (mapLayerStyle == MapLayerStyle.SATELLITE) MapType.HYBRID else MapType.NORMAL
        ),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
        onMapClick = { latLng -> onTapMapPoint(GeoPoint(latLng.latitude, latLng.longitude)) }
    ) {
        Marker(
            state = MarkerState(position = LatLng(pickupPoint.lat, pickupPoint.lng)),
            title = "Подача (A)"
        )
        waypoints.forEachIndexed { idx, wp ->
            Marker(
                state = MarkerState(position = LatLng(wp.point.lat, wp.point.lng)),
                title = "Остановка ${idx + 1}: ${wp.name}"
            )
        }
        Marker(
            state = MarkerState(position = LatLng(dropoffPoint.lat, dropoffPoint.lng)),
            title = "Назначение (B)"
        )
        Polyline(
            points = polyPoints,
            color = UberBlue,
            width = 11f
        )
        if (showSurgeHeatmap) {
            city.activeSurgeZones.forEach { zone ->
                Circle(
                    center = LatLng(zone.center.lat, zone.center.lng),
                    radius = zone.radiusKm * 500.0,
                    fillColor = UberRed.copy(alpha = 0.24f),
                    strokeColor = UberAmber,
                    strokeWidth = 2f
                )
            }
        }
        nearbyDrivers.forEach { d ->
            Marker(
                state = MarkerState(position = LatLng(d.point.lat, d.point.lng)),
                title = if (d.isEv) "Green EV" else "KG Taxi"
            )
        }
        if (activeRide != null && polyPoints.size >= 2) {
            // Real ride: live GPS position of the driver. Demo ride: position along the route.
            val pos = activeRide.driverPoint?.let { LatLng(it.lat, it.lng) }
                ?: interpolateLatLng(polyPoints, activeRide.progress.coerceIn(0f, 1f))
            Marker(
                state = MarkerState(position = pos),
                title = activeRide.driverName
            )
        }
    }
}

private fun interpolateLatLng(points: List<LatLng>, fraction: Float): LatLng {
    if (points.size < 2) return points.first()
    val scaled = fraction.coerceIn(0f, 1f) * (points.size - 1)
    val idx = scaled.toInt().coerceIn(0, points.size - 2)
    val localT = scaled - idx
    val a = points[idx]
    val b = points[idx + 1]
    return LatLng(a.latitude + (b.latitude - a.latitude) * localT, a.longitude + (b.longitude - a.longitude) * localT)
}

@Composable
private fun InteractiveVectorKgMapCanvas(
    city: KgCity,
    pickupPoint: GeoPoint,
    dropoffPoint: GeoPoint,
    waypoints: List<KgPlace>,
    routePolyline: List<GeoPoint>,
    nearbyDrivers: List<NearbyDriverMarker>,
    activeRide: ActiveRideState?,
    showSurgeHeatmap: Boolean,
    showTrafficOverlay: Boolean,
    darkTheme: Boolean,
    satelliteMode: Boolean = false,
    onTapMapPoint: (GeoPoint) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mapAnim")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )
    val drift by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift"
    )

    val mapBg = when {
        satelliteMode -> Color(0xFF1A2A22)
        darkTheme -> Color(0xFF12151C)
        else -> Color(0xFFEDF1F5)
    }
    val parkColor = when {
        satelliteMode -> Color(0xFF23422E)
        darkTheme -> Color(0xFF182921)
        else -> Color(0xFFD5EBD9)
    }
    val roadMajor = when {
        satelliteMode -> Color(0xFF47574E)
        darkTheme -> Color(0xFF282F3D)
        else -> Color(0xFFFFFFFF)
    }
    val roadMinor = when {
        satelliteMode -> Color(0xFF2E3D34)
        darkTheme -> Color(0xFF1E2430)
        else -> Color(0xFFE0E6ED)
    }
    val waterColor = when {
        satelliteMode -> Color(0xFF123652)
        darkTheme -> Color(0xFF15283C)
        else -> Color(0xFFC5DFF8)
    }
    val pinPrimary = if (darkTheme || satelliteMode) Color.White else Color.Black

    // Bounding box around current city (+/- 0.045 deg)
    val spanLat = 0.075
    val spanLng = 0.095
    val minLat = city.center.lat - spanLat / 2.0
    val minLng = city.center.lng - spanLng / 2.0

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(mapBg)
            .pointerInput(city.name) {
                detectTapGestures { offset ->
                    val normX = (offset.x / size.width).coerceIn(0.05f, 0.95f)
                    val normY = (1f - offset.y / size.height).coerceIn(0.05f, 0.95f)
                    val tappedLat = minLat + normY * spanLat
                    val tappedLng = minLng + normX * spanLng
                    onTapMapPoint(GeoPoint(tappedLat, tappedLng))
                }
            }
            .testTag("interactive_vector_map")
    ) {
        val w = size.width
        val h = size.height

        fun toScreen(p: GeoPoint): Offset {
            val x = (((p.lng - minLng) / spanLng).coerceIn(0.06, 0.94) * w).toFloat()
            val y = ((1.0 - ((p.lat - minLat) / spanLat).coerceIn(0.08, 0.92)) * h).toFloat()
            return Offset(x, y)
        }

        // Stylized Tian Shan mountain / park & river accents
        drawCircle(color = parkColor, radius = w * 0.22f, center = Offset(w * 0.16f, h * 0.22f))
        drawCircle(color = parkColor, radius = w * 0.18f, center = Offset(w * 0.84f, h * 0.78f))
        drawLine(
            color = waterColor,
            start = Offset(0f, h * 0.72f),
            end = Offset(w, h * 0.58f),
            strokeWidth = 14f,
            cap = StrokeCap.Round
        )

        // Urban street grid (clean Uber minimalist map tiles)
        for (i in 1..9) {
            val x = w * (i / 10f)
            drawLine(
                color = if (i % 3 == 0) roadMajor else roadMinor,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = if (i % 3 == 0) 8f else 3f
            )
        }
        for (j in 1..6) {
            val y = h * (j / 7f)
            drawLine(
                color = if (j % 2 == 0) roadMajor else roadMinor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = if (j % 2 == 0) 9f else 3f
            )
        }
        // Diagonal boulevard (Chuy / Manas feel)
        drawLine(
            color = roadMajor,
            start = Offset(0f, h * 0.34f),
            end = Offset(w, h * 0.44f),
            strokeWidth = 12f
        )

        // Surge Heatmap Overlay
        if (showSurgeHeatmap) {
            city.activeSurgeZones.forEach { zone ->
                val c = toScreen(zone.center)
                val r = (w * 0.16f) * zone.multiplier.toFloat()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            UberRed.copy(alpha = 0.48f),
                            UberAmber.copy(alpha = 0.26f),
                            Color.Transparent
                        ),
                        center = c,
                        radius = r
                    ),
                    radius = r,
                    center = c
                )
            }
        }

        // Optional Real-Time Traffic Overlay along city avenues
        if (showTrafficOverlay) {
            drawLine(color = UberGreen.copy(alpha = 0.78f), start = Offset(w * 0.08f, h * 0.35f), end = Offset(w * 0.42f, h * 0.39f), strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(color = UberAmber.copy(alpha = 0.85f), start = Offset(w * 0.42f, h * 0.39f), end = Offset(w * 0.68f, h * 0.42f), strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(color = UberRed.copy(alpha = 0.85f), start = Offset(w * 0.68f, h * 0.42f), end = Offset(w * 0.92f, h * 0.44f), strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(color = UberGreen.copy(alpha = 0.72f), start = Offset(w * 0.3f, h * 0.1f), end = Offset(w * 0.3f, h * 0.85f), strokeWidth = 5f, cap = StrokeCap.Round)
            drawLine(color = UberAmber.copy(alpha = 0.78f), start = Offset(w * 0.6f, h * 0.18f), end = Offset(w * 0.6f, h * 0.78f), strokeWidth = 5f, cap = StrokeCap.Round)
        }

        val pA = toScreen(pickupPoint)
        val pB = toScreen(dropoffPoint)
        val screenPolyline: List<Offset> = if (routePolyline.size >= 2) {
            routePolyline.map { toScreen(it) }
        } else {
            listOf(
                pA,
                Offset((pA.x + pB.x) / 2f + 24f, (pA.y + pB.y) / 2f - 28f),
                pB
            )
        }

        // Route polyline shadow + Uber Blue road path (OSRM or fallback)
        val routePath = Path().apply {
            moveTo(screenPolyline.first().x, screenPolyline.first().y)
            for (i in 1 until screenPolyline.size) {
                lineTo(screenPolyline[i].x, screenPolyline[i].y)
            }
        }
        drawPath(
            path = routePath,
            color = UberBlue.copy(alpha = 0.22f),
            style = Stroke(width = 16f, cap = StrokeCap.Round)
        )
        drawPath(
            path = routePath,
            color = UberBlue,
            style = Stroke(width = 7f, cap = StrokeCap.Round)
        )

        // Nearby Drivers (subtle animated drift)
        nearbyDrivers.forEachIndexed { idx, drv ->
            val base = toScreen(drv.point)
            val dx = drift * (if (idx % 2 == 0) 9f else -7f)
            val dy = drift * (if (idx % 3 == 0) -6f else 8f)
            val pos = Offset(base.x + dx, base.y + dy)
            drawCircle(
                color = if (drv.isEv) UberGreen else pinPrimary,
                radius = 9f,
                center = pos
            )
            drawCircle(
                color = if (darkTheme) Color.Black else Color.White,
                radius = 4f,
                center = pos
            )
        }

        // Active Ride Moving Vehicle along actual road polyline segments
        if (activeRide != null && screenPolyline.size >= 2) {
            val t = activeRide.progress.coerceIn(0f, 1f)
            val scaled = t * (screenPolyline.size - 1)
            val idx = scaled.toInt().coerceIn(0, screenPolyline.size - 2)
            val segT = scaled - idx
            val s0 = screenPolyline[idx]
            val s1 = screenPolyline[idx + 1]
            val carPos = activeRide.driverPoint?.let { toScreen(it) }
                ?: Offset(s0.x + (s1.x - s0.x) * segT, s0.y + (s1.y - s0.y) * segT)
            drawCircle(
                color = UberBlue.copy(alpha = 0.28f * (1f - pulse)),
                radius = 18f + 26f * pulse,
                center = carPos
            )
            drawCircle(color = UberBlack, radius = 14f, center = carPos)
            drawCircle(color = Color.White, radius = 11f, center = carPos)
            drawCircle(color = UberBlue, radius = 6f, center = carPos)
        }

        // Pickup Pin A (Uber Circle with pulse)
        drawCircle(
            color = UberBlue.copy(alpha = 0.22f * (1f - pulse)),
            radius = 14f + 22f * pulse,
            center = pA
        )
        drawCircle(color = pinPrimary, radius = 12f, center = pA)
        drawCircle(color = if (darkTheme) Color.Black else Color.White, radius = 5f, center = pA)

        // Waypoint Stop Pins (Amber Circles)
        waypoints.forEach { wp ->
            val wpPos = toScreen(wp.point)
            drawCircle(color = UberAmber, radius = 11f, center = wpPos)
            drawCircle(color = Color.Black, radius = 4.5f, center = wpPos)
        }

        // Dropoff Pin B (Uber Square)
        drawRect(
            color = pinPrimary,
            topLeft = Offset(pB.x - 11f, pB.y - 11f),
            size = androidx.compose.ui.geometry.Size(22f, 22f)
        )
        drawRect(
            color = if (darkTheme) Color.Black else Color.White,
            topLeft = Offset(pB.x - 4.5f, pB.y - 4.5f),
            size = androidx.compose.ui.geometry.Size(9f, 9f)
        )
    }
}
