package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DriverChatMessage
import com.example.ui.theme.UberBlue
import com.example.ui.theme.UberGreen

@Composable
fun DriverChatOverlay(
    driverName: String,
    driverCar: String,
    messages: List<DriverChatMessage>,
    translateTargetLang: String,
    isTranslating: Boolean,
    onSelectTranslateLang: (String) -> Unit,
    onTranslateAll: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    var input by remember { mutableStateOf("") }

    val quickPhrases = listOf(
        "Выхожу через 2 мин",
        "Я у главного входа",
        "Остановитесь у шлагбаума",
        "Спасибо, уже иду!"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_driver_chat_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Close chat", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    Column {
                        Text(
                            text = driverName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = driverCar,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                        )
                    }
                }
                FilledTonalButton(
                    onClick = onTranslateAll,
                    enabled = !isTranslating,
                    modifier = Modifier.testTag("chat_ai_translate_button")
                ) {
                    Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isTranslating) "..." else "ИИ → $translateTargetLang")
                }
            }

            // Target Language Selector Strip (RU / KG / EN)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Автоперевод чата:", style = MaterialTheme.typography.labelMedium)
                listOf("RU", "KG", "EN").forEach { code ->
                    FilterChip(
                        selected = translateTargetLang == code,
                        onClick = { onSelectTranslateLang(code) },
                        label = { Text(code) },
                        modifier = Modifier.testTag("chat_lang_$code")
                    )
                }
            }

            // Messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.fromPassenger) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 290.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (msg.fromPassenger) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = msg.originalText,
                                color = if (msg.fromPassenger) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (!msg.translatedText.isNullOrBlank()) {
                                HorizontalDivider(
                                    color = if (msg.fromPassenger) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = "ИИ-перевод: ${msg.translatedText}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (msg.fromPassenger) Color(0xFFB3D4FF) else UberBlue,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = msg.timeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (msg.fromPassenger) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick Phrases Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickPhrases.forEach { phrase ->
                    AssistChip(
                        onClick = { onSendMessage(phrase) },
                        label = { Text(phrase, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Composer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Написать водителю...") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).testTag("driver_chat_input")
                )
                FilledIconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            onSendMessage(input.trim())
                            input = ""
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = UberGreen, contentColor = Color.White),
                    modifier = Modifier.size(48.dp).testTag("driver_chat_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}
