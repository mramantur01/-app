package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.data.local.LocalProfileEntity
import com.example.data.local.LocalTripEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportExporter {

    fun writeTripsReportPdf(
        context: Context,
        uri: Uri,
        profile: LocalProfileEntity,
        trips: List<LocalTripEntity>,
        singleTrip: LocalTripEntity? = null
    ): Result<Unit> {
        return try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val headerBg = Paint().apply { color = Color.BLACK }
            val whiteTitle = Paint().apply {
                color = Color.WHITE
                textSize = 22f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            val whiteSub = Paint().apply {
                color = Color.LTGRAY
                textSize = 11f
                isAntiAlias = true
            }
            val headingPaint = Paint().apply {
                color = Color.BLACK
                textSize = 14f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            val bodyPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 11f
                isAntiAlias = true
            }
            val monoBold = Paint().apply {
                color = Color.BLACK
                textSize = 12f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                isAntiAlias = true
            }
            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }

            // Stark Uber-style black header
            canvas.drawRect(0f, 0f, 595f, 105f, headerBg)
            canvas.drawText("KG TAXI", 36f, 48f, whiteTitle)
            val docTitle = if (singleTrip != null) "ОФИЦИАЛЬНЫЙ ЭЛЕКТРОННЫЙ ЧЕК" else "ОТЧЁТ ПО ПОЕЗДКАМ И РАСХОДАМ"
            canvas.drawText(docTitle, 36f, 70f, whiteSub)
            val dateFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            canvas.drawText("Сформировано: ${dateFmt.format(Date())}", 36f, 88f, whiteSub)

            var y = 135f
            canvas.drawText("Пассажир: ${profile.displayName}", 36f, y, headingPaint)
            y += 18f
            canvas.drawText("Телефон: ${profile.phone}   |   Город: ${profile.preferredCity}", 36f, y, bodyPaint)
            if (profile.businessProfileActive) {
                y += 16f
                canvas.drawText("Корпоративный профиль B2B: ${profile.companyName} (${profile.companyEmail})", 36f, y, bodyPaint)
            }
            y += 16f
            canvas.drawLine(36f, y, 559f, y, linePaint)
            y += 24f

            val targetList = if (singleTrip != null) listOf(singleTrip) else trips.take(15)
            val totalSum = targetList.sumOf { it.totalPriceSom + it.tipSom }
            val totalKm = targetList.sumOf { it.distanceKm }

            canvas.drawText("Сводка: поездок — ${targetList.size}, дистанция — ${String.format(Locale.US, "%.1f", totalKm)} км, итого — $totalSum сом", 36f, y, monoBold)
            y += 28f

            targetList.forEachIndexed { index, trip ->
                if (y > 760f) return@forEachIndexed
                canvas.drawText(
                    "${index + 1}. [${trip.serviceType}] ${trip.tariffName} • ${trip.city} • ${dateFmt.format(Date(trip.createdAtMillis))}",
                    36f,
                    y,
                    headingPaint
                )
                y += 16f
                canvas.drawText("Откуда: ${trip.pickupAddress.take(72)}", 44f, y, bodyPaint)
                y += 14f
                canvas.drawText("Куда: ${trip.dropoffAddress.take(72)}", 44f, y, bodyPaint)
                y += 14f
                val driverInfo = if (trip.driverName.isNotBlank()) "Водитель: ${trip.driverName} (${trip.driverCar})" else "Сервис KG Taxi"
                canvas.drawText("$driverInfo  |  PIN: ${trip.safetyPin}", 44f, y, bodyPaint)
                y += 15f
                canvas.drawText(
                    "Оплата: ${trip.paymentMethod}  |  Дистанция: ${String.format(Locale.US, "%.1f", trip.distanceKm)} км  |  Сумма: ${trip.totalPriceSom} сом (+чаевые ${trip.tipSom} сом)",
                    44f,
                    y,
                    monoBold
                )
                y += 12f
                canvas.drawLine(36f, y, 559f, y, linePaint)
                y += 20f
            }

            canvas.drawText("KG Taxi Kyrgyzstan • Поддержка 24/7 • Безопасность SOS 112", 36f, 818f, bodyPaint)
            document.finishPage(page)

            context.contentResolver.openOutputStream(uri)?.use { out ->
                document.writeTo(out)
            } ?: error("Не удалось открыть поток для сохранения PDF")

            document.close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
