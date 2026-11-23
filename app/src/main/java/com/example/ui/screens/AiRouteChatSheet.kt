package com.example.ui.screens

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AiChatMessage
import com.example.data.model.KgGeoCatalog
import com.example.i18n.KgStrings
import com.example.ui.theme.UberBlue

@Composable
fun AiRouteChatOverlay(
    strings: KgStrings,
    messages: List<AiChatMessage>,
    isLoading: Boolean,
    onSendPrompt: (String) -> Unit,
    onApplySuggestedPlace: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    var input by remember { mutableStateOf("") }

    val quickPrompts = listOf(
        "Спланируй маршрут Бишкек → Иссык-Куль",
        "Сколько ехать до Аэропорта Манас (FRU)?",
        "Лучшая экскурсия в Ала-Арчу на 4 часа",
        "Где вкусно поесть плов в Бишкеке?"
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
                .imePadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_ai_chat_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Close AI chat")
                    }
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = UberBlue)
                    Column {
                        Text(strings.aiAssistant, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Планирование маршрутов по Кыргызстану", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider()

            // Messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (msg.fromUser) Alignment.End else Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 310.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = if (msg.fromUser) 18.dp else 4.dp,
                                        bottomEnd = if (msg.fromUser) 4.dp else 18.dp
                                    )
                                )
                                .background(
                                    if (msg.fromUser) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = if (msg.isStreaming && msg.text.isBlank()) "Думаю над маршрутом..." else msg.text,
                                color = if (msg.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (!msg.fromUser && !msg.suggestedDestination.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            AssistChip(
                                onClick = {
                                    onApplySuggestedPlace(msg.suggestedDestination)
                                    onDismiss()
                                },
                                label = { Text("Построить маршрут: ${msg.suggestedDestination}") }
                            )
                        }
                    }
                }
            }

            // Starter Prompts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickPrompts.forEach { q ->
                    SuggestionChip(
                        onClick = { onSendPrompt(q) },
                        label = { Text(q, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            // Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(strings.aiHint) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).testTag("ai_chat_input")
                )
                FilledIconButton(
                    onClick = {
                        if (input.isNotBlank()) {
                            onSendPrompt(input)
                            input = ""
                        }
                    },
                    enabled = !isLoading && input.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = UberBlue, contentColor = Color.White),
                    modifier = Modifier.size(50.dp).testTag("ai_chat_send_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}
