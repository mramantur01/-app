package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CityWeatherInfo
import com.example.data.model.DisplayCurrency
import com.example.data.model.KgMountainPass
import com.example.ui.theme.*

@Composable
fun CityWeatherAndCurrencyStrip(
    weather: CityWeatherInfo,
    currency: DisplayCurrency,
    businessModeActive: Boolean,
    onSelectCurrency: (DisplayCurrency) -> Unit,
    onToggleBusinessMode: () -> Unit,
    onRefreshWeather: () -> Unit,
    modifier: Modifier = Modifier
) {
    val aqiColor = when {
        weather.aqiUs <= 50 -> UberGreen
        weather.aqiUs <= 100 -> UberBlue
        else -> UberAmber
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Live Open-Meteo Weather + AQI Pill
        Surface(
            onClick = onRefreshWeather,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.testTag("weather_aqi_strip")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(14.dp), tint = UberAmber)
                Text(
                    text = "${if (weather.tempC > 0) "+" else ""}${weather.tempC}°C ${weather.conditionLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text("•", style = MaterialTheme.typography.labelSmall)
                Text(
                    text = "AQI ${weather.aqiUs}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    color = aqiColor
                )
            }
        }

        // Personal / Business Quick Switcher
        FilterChip(
            selected = businessModeActive,
            onClick = onToggleBusinessMode,
            label = {
                Text(
                    text = if (businessModeActive) "Бизнес B2B" else "Личный",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (businessModeActive) Icons.Default.BusinessCenter else Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            },
            modifier = Modifier.height(30.dp).testTag("home_business_mode_chip")
        )

        // Tourist Multi-Currency Quick Selector (KGS / USD / RUB / KZT / EUR)
        DisplayCurrency.entries.forEach { cur ->
            val sel = cur == currency
            FilterChip(
                selected = sel,
                onClick = { onSelectCurrency(cur) },
                label = {
                    Text(
                        text = cur.code,
                        fontFamily = JetBrainsMono,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium
                    )
                },
                modifier = Modifier.height(30.dp).testTag("currency_${cur.code.lowercase()}_chip")
            )
        }
    }
}

@Composable
fun MountainPassesStatusCard(
    passes: List<KgMountainPass>,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Terrain, contentDescription = null, tint = UberBlue, modifier = Modifier.size(18.dp))
                Text(
                    text = "Статус горных перевалов и трасс КР",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            passes.forEach { pass ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pass.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(pass.advisory, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (pass.isCaution) UberAmber.copy(alpha = 0.22f) else UberGreen.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = pass.statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pass.isCaution) Color(0xFF9A5B00) else UberGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "${if (pass.tempC > 0) "+" else ""}${pass.tempC}°C",
                                fontFamily = JetBrainsMono,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceOrderAssistantDialog(
    isParsing: Boolean,
    lastResultSummary: String?,
    onLaunchSystemMic: () -> Unit,
    onSubmitVoicePhrase: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var phraseInput by remember { mutableStateOf("") }
    val samplePhrases = listOf(
        "С ЦУМа в аэропорт Манас на Комфорте с кондиционером",
        "От площади Ала-Тоо до Asia Mall на Green EV, тихая поездка",
        "В Ошский базар на Минивэне, нужна помощь с багажом",
        "От Dordoi Plaza до дома, тариф Бизнес, командировка"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = UberBlue)
                Text("Голосовой заказ (ИИ)", fontWeight = FontWeight.ExtraBold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Скажите маршрут, тариф и пожелания одной фразой — Gemini AI автоматически заполнит заказ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onLaunchSystemMic,
                    colors = ButtonDefaults.buttonColors(containerColor = UberBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("voice_start_mic_button")
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Нажать и говорить", fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = phraseInput,
                    onValueChange = { phraseInput = it },
                    label = { Text("Или введите фразу вручную") },
                    placeholder = { Text("С ЦУМа в аэропорт Манас на Комфорте...") },
                    modifier = Modifier.fillMaxWidth().testTag("voice_phrase_input")
                )

                Text("Примеры быстрых команд:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                samplePhrases.forEachIndexed { idx, sample ->
                    Surface(
                        onClick = { phraseInput = sample },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().testTag("voice_sample_$idx")
                    ) {
                        Text(
                            text = "«$sample»",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }

                if (isParsing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("ИИ разбирает заказ...", style = MaterialTheme.typography.labelMedium, color = UberBlue)
                    }
                } else if (!lastResultSummary.isNullOrBlank()) {
                    Surface(shape = RoundedCornerShape(10.dp), color = UberGreen.copy(alpha = 0.14f)) {
                        Text(
                            text = lastResultSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = UberGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (phraseInput.isNotBlank()) {
                        onSubmitVoicePhrase(phraseInput.trim())
                        onDismiss()
                    }
                },
                enabled = phraseInput.isNotBlank() && !isParsing,
                modifier = Modifier.testTag("voice_apply_ai_button")
            ) {
                Text("Применить ИИ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

/**
 * Full-screen high-contrast airport Meet & Greet electronic sign for Driver Cabin.
 */
@Composable
fun MeetAndGreetPlacardDialog(
    guestName: String,
    flightLabel: String?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).testTag("close_meet_greet_placard")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close placard", tint = Color.White, modifier = Modifier.size(30.dp))
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = UberBlue) {
                    Text(
                        text = "KG TAXI • VIP MEET & GREET",
                        color = Color.White,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Text(
                    text = guestName.ifBlank { "VIP GUEST" }.uppercase(),
                    color = Color.White,
                    fontSize = 42.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                if (!flightLabel.isNullOrBlank()) {
                    Text(
                        text = "✈ $flightLabel",
                        color = UberGreen,
                        fontFamily = JetBrainsMono,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "45 мин бесплатного ожидания • Аэропорт Манас / Ош",
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
