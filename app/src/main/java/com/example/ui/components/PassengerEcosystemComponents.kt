package com.example.ui.components

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.LocalFamilyMemberEntity
import com.example.data.model.*
import com.example.i18n.AppLanguage
import com.example.ui.theme.*
import java.util.Locale

/**
 * Live Intercity Scheduled Departures Card (Bishkek-Osh, Cholpon-Ata, Karakol, Talas, Naryn).
 * Tapping a departure syncs its occupied/free seats directly to the 6-seat cabin visual picker.
 */
@Composable
fun IntercityLiveScheduleCard(
    route: IntercityRoute,
    trips: List<IntercityScheduledTrip>,
    selectedTripId: String?,
    onSelectTrip: (IntercityScheduledTrip) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ближайшие рейсы: ${route.fromCity} → ${route.toCity}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Surface(shape = RoundedCornerShape(8.dp), color = UberGreen.copy(alpha = 0.14f)) {
                Text(
                    text = "Live • ${trips.size} авто",
                    style = MaterialTheme.typography.labelSmall,
                    color = UberGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        trips.forEach { trip ->
            val selected = selectedTripId == trip.id
            Surface(
                onClick = { onSelectTrip(trip) },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("intercity_schedule_${trip.id}")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = trip.departureTime,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${trip.driverName} ★ ${trip.driverRating} • ${trip.carModel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (trip.freeSeatsCount <= 2) UberAmber else UberGreen
                    ) {
                        Text(
                            text = "${trip.freeSeatsCount} из ${trip.totalSeats} своб.",
                            fontFamily = JetBrainsMono,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (trip.freeSeatsCount <= 2) Color.Black else Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * AI Audio Tour Guide for Kyrgyzstan Excursions & Intercity Routes (Gemini 3.8 Flash + Android TextToSpeech).
 */
@Composable
fun AiTourGuideCard(
    language: AppLanguage,
    currentTopic: String?,
    storyText: String?,
    isLoading: Boolean,
    onRequestStory: (String) -> Unit
) {
    val context = LocalContext.current
    var ttsReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    val tts = remember(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
            }
        }
        engine
    }

    DisposableEffect(tts) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    val topics = listOf(
        "Озеро Иссык-Куль",
        "Озеро Сон-Куль",
        "Каньон Сказка",
        "Джети-Огуз",
        "Ущелье Ала-Арча",
        "Боомское ущелье",
        "Башня Бурана",
        "Перевал Тоо-Ашуу",
        "Сулайман-Тоо",
        "Арсланбоб",
        "Сары-Челек",
        "Таш-Рабат",
        "Озеро Ала-Куль",
        "Алтын-Арашан",
        "Каракол",
        "Барскоон",
        "Чолпон-Ата: петроглифы",
        "Чон-Кемин",
        "Озеро Кель-Суу"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = UberBlue.copy(alpha = 0.09f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, UberBlue.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = UberBlue, modifier = Modifier.size(18.dp))
                    Text("AI-аудиогид по Кыргызстану", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = UberBlue)
                }
                Text(language.code, style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono, color = UberBlue)
            }

            // All places visible at once, wrapping onto new lines.
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                topics.forEach { topic ->
                    FilterChip(
                        selected = currentTopic == topic,
                        onClick = {
                            tts?.stop()
                            isSpeaking = false
                            onRequestStory(topic)
                        },
                        label = { Text(topic, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("tour_guide_${topic.hashCode()}")
                    )
                }
            }

            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("AI-гид готовит рассказ...", style = MaterialTheme.typography.bodySmall)
                }
            } else if (!storyText.isNullOrBlank()) {
                Text(
                    text = storyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            if (isSpeaking) {
                                tts?.stop()
                                isSpeaking = false
                            } else if (ttsReady) {
                                val locale = when (language) {
                                    AppLanguage.EN -> Locale.US
                                    AppLanguage.RU, AppLanguage.KG -> Locale("ru", "RU")
                                }
                                tts?.language = locale
                                tts?.speak(storyText, TextToSpeech.QUEUE_FLUSH, null, "kg_tour_guide")
                                isSpeaking = true
                            }
                        },
                        modifier = Modifier.testTag("tour_guide_speak_button")
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = "Audio guide",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isSpeaking) "Стоп аудио" else "Озвучить гид")
                    }
                }
            }
        }
    }
}

/**
 * Theme Mode (Auto / Light / Dark) & Map Layer Style (Standard / Satellite / Night) Selector Card.
 */
@Composable
fun ThemeAndMapAppearanceCard(
    themeMode: AppThemeMode,
    mapLayerStyle: MapLayerStyle,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onSelectMapLayerStyle: (MapLayerStyle) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = UberBlue)
                Text("Оформление и слой карты", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Тема приложения", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onSelectThemeMode(mode) },
                            label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Стиль карты", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapLayerStyle.entries.forEach { style ->
                        FilterChip(
                            selected = mapLayerStyle == style,
                            onClick = { onSelectMapLayerStyle(style) },
                            label = { Text(style.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("map_layer_${style.name.lowercase()}")
                        )
                    }
                }
            }
        }
    }
}

/**
 * Upgraded Family Profile & Teen Live Tracking Section (Shared wallet + live member ride status).
 */
@Composable
fun FamilyLiveTrackingCard(
    members: List<LocalFamilyMemberEntity>,
    sharedWalletSom: Int,
    onOpenAddMember: () -> Unit,
    onRemoveMember: (String) -> Unit,
    onTrackMember: (LocalFamilyMemberEntity) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Семейный профиль (KG Family)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Общий баланс: $sharedWalletSom сом • Живой контроль поездок",
                        style = MaterialTheme.typography.labelSmall,
                        color = UberGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                FilledTonalButton(
                    onClick = onOpenAddMember,
                    modifier = Modifier.testTag("add_family_member_button")
                ) {
                    Text("+ Близкий")
                }
            }

            if (members.isEmpty()) {
                Text(
                    text = "Добавьте родителей или детей для оплаты с общего баланса и отслеживания их маршрута в реальном времени.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                members.forEachIndexed { idx, member ->
                    val inRide = idx == 0
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${member.name} • ${member.relation}", fontWeight = FontWeight.Bold)
                                    if (inRide) {
                                        Surface(shape = RoundedCornerShape(6.dp), color = UberGreen.copy(alpha = 0.16f)) {
                                            Text(
                                                text = "В пути • 6 мин",
                                                color = UberGreen,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Лимит: ${member.monthlyLimitSom} сом/мес • ${if (member.teenControlEnabled) "PIN + RideCheck ВКЛ" else "Стандарт"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onTrackMember(member) },
                                    modifier = Modifier.testTag("track_family_${member.id}")
                                ) {
                                    Icon(Icons.Default.MyLocation, contentDescription = "Отследить", tint = UberBlue)
                                }
                                IconButton(onClick = { onRemoveMember(member.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove member")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
