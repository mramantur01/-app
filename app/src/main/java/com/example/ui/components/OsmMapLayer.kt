package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.model.GeoPoint
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

/** One point drawn on the street map. */
data class OsmPin(val point: GeoPoint, val label: String, val color: Int, val title: String, val small: Boolean = false)

/**
 * Free real street map (OpenStreetMap) used when there is no Google Maps key.
 * Shows pins, an optional route line and handles taps. Needs internet for tiles.
 */
@Composable
fun OsmMapLayer(
    center: GeoPoint,
    centerKey: String,
    pins: List<OsmPin>,
    route: List<GeoPoint>,
    routeColor: Int,
    darkTheme: Boolean,
    fitTo: List<GeoPoint>,
    onTap: ((GeoPoint) -> Unit)?,
    modifier: Modifier = Modifier,
    zoom: Double = 13.5,
    /** Called when the user stops moving the map, with the point in the middle. */
    onCenterChanged: ((GeoPoint) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tapHandler by rememberUpdatedState(onTap)

    val mapView = remember {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = context.packageName
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 6.0
            maxZoomLevel = 19.0
            controller.setZoom(zoom)
            controller.setCenter(org.osmdroid.util.GeoPoint(center.lat, center.lng))
        }
    }
    val eventsOverlay = remember {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: org.osmdroid.util.GeoPoint): Boolean {
                val h = tapHandler ?: return false
                h(GeoPoint(p.latitude, p.longitude))
                return true
            }

            override fun longPressHelper(p: org.osmdroid.util.GeoPoint): Boolean = false
        })
    }

    val centerHandler by rememberUpdatedState(onCenterChanged)
    DisposableEffect(mapView) {
        val listener = org.osmdroid.events.DelayedMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                val c = mapView.mapCenter
                centerHandler?.invoke(GeoPoint(c.latitude, c.longitude))
                return false
            }
            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean = false
        }, 250)
        mapView.addMapListener(listener)
        onDispose { mapView.removeMapListener(listener) }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(centerKey) {
        // With a route to show, the fit below decides the view; do not jump to the city center.
        if (fitTo.size < 2) mapView.controller.animateTo(org.osmdroid.util.GeoPoint(center.lat, center.lng), zoom, 600L)
    }
    LaunchedEffect(fitTo) {
        if (fitTo.size >= 2) {
            val lats = fitTo.map { it.lat }
            val lngs = fitTo.map { it.lng }
            val margin = 0.004
            // The bottom part of the screen is covered by the order card: keep the route in the upper half.
            val span = (lats.max() - lats.min()).coerceAtLeast(0.005)
            val box = BoundingBox(lats.max() + margin, lngs.max() + margin, lats.min() - margin - span * 1.1, lngs.min() - margin)
            val pad = (64 * mapView.resources.displayMetrics.density).toInt()
            // The map may not be laid out yet: fit after the first layout, otherwise right away.
            if (mapView.width > 0 && mapView.height > 0) {
                mapView.post { runCatching { mapView.zoomToBoundingBox(box, false, pad) } }
            } else {
                mapView.addOnFirstLayoutListener { _, _, _, _, _ -> runCatching { mapView.zoomToBoundingBox(box, false, pad) } }
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { mv ->
            mv.overlays.clear()
            mv.overlays.add(eventsOverlay)
            mv.overlayManager.tilesOverlay.setColorFilter(if (darkTheme) TilesOverlay.INVERT_COLORS else null)
            if (route.size >= 2) {
                val line = Polyline(mv).apply {
                    setPoints(route.map { org.osmdroid.util.GeoPoint(it.lat, it.lng) })
                    outlinePaint.color = routeColor
                    outlinePaint.strokeWidth = 12f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    isEnabled = true
                    setOnClickListener { _, _, _ -> false }
                }
                mv.overlays.add(line)
            }
            pins.forEach { pin ->
                val m = Marker(mv).apply {
                    position = org.osmdroid.util.GeoPoint(pin.point.lat, pin.point.lng)
                    icon = pinIcon(mv.context, pin.color, pin.label, pin.small)
                    setAnchor(Marker.ANCHOR_CENTER, if (pin.small) Marker.ANCHOR_CENTER else Marker.ANCHOR_BOTTOM)
                    title = pin.title
                    setOnMarkerClickListener { marker, _ -> marker.showInfoWindow(); true }
                }
                mv.overlays.add(m)
            }
            mv.overlays.add(CopyrightOverlay(mv.context))
            mv.invalidate()
        }
    )
}

/** Round pin with a letter (A, B, 1, 2…) or a small dot for cars. */
private fun pinIcon(context: Context, color: Int, label: String, small: Boolean): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = ((if (small) 22 else 36) * density).toInt()
    val tail = if (small) 0 else (10 * density).toInt()
    val bmp = Bitmap.createBitmap(size, size + tail, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    if (!small) {
        paint.color = color
        val path = android.graphics.Path().apply {
            moveTo(r - r * 0.45f, r + r * 0.6f)
            lineTo(r, (size + tail).toFloat())
            lineTo(r + r * 0.45f, r + r * 0.6f)
            close()
        }
        c.drawPath(path, paint)
    }
    paint.color = android.graphics.Color.WHITE
    c.drawCircle(r, r, r, paint)
    paint.color = color
    c.drawCircle(r, r, r - 3 * density, paint)
    if (label.isNotEmpty()) {
        paint.color = android.graphics.Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = r * (if (label.length > 1) 0.8f else 1.0f)
        val y = r - (paint.descent() + paint.ascent()) / 2
        c.drawText(label, r, y, paint)
    }
    return BitmapDrawable(context.resources, bmp)
}
