package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.FOOD_DELIVERY_FEE_SOM
import com.example.ui.KgTaxiUiState
import com.example.ui.KgTaxiViewModel
import com.example.ui.theme.KgInk
import com.example.ui.theme.KgTurquoise

private data class FoodKind(val id: String, val title: String, val icon: ImageVector, val hint: String)

private val FOOD_KINDS = listOf(
    FoodKind("Из кафе", "Из кафе", Icons.Default.Restaurant, "Лагман 2 порции, самса 4 шт, чай"),
    FoodKind("Продукты", "Продукты", Icons.Default.ShoppingCart, "Хлеб, молоко 2 л, яйца 10 шт, сахар 1 кг"),
    FoodKind("Аптека", "Аптека", Icons.Default.LocalPharmacy, "Парацетамол, пластырь, витамин C")
)

/**
 * "Еда и продукты": the customer writes what to buy, a courier (a driver who takes deliveries)
 * buys it and brings it; the customer pays for the goods and the delivery on arrival.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodOrderScreen(state: KgTaxiUiState, viewModel: KgTaxiViewModel, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(FOOD_KINDS.first()) }
    var items by remember { mutableStateOf("") }
    var shop by remember { mutableStateOf("") }
    var goods by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var itemsError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize().testTag("food_screen"), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") }
                    Text("Еда и продукты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = KgInk)
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Курьер купит по вашему списку и привезёт. За покупки и доставку платите курьеру при получении.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FOOD_KINDS.forEach { k ->
                            FilterChip(
                                selected = kind == k,
                                onClick = { kind = k },
                                label = { Text(k.title) },
                                leadingIcon = { Icon(k.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                modifier = Modifier.testTag("food_kind_${k.id}")
                            )
                        }
                    }
                    OutlinedTextField(
                        value = items,
                        onValueChange = { items = it.take(220); itemsError = false },
                        label = { Text("Что купить") },
                        placeholder = { Text(kind.hint) },
                        isError = itemsError,
                        supportingText = { if (itemsError) Text("Напишите, что купить") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("food_items")
                    )
                    OutlinedTextField(
                        value = shop,
                        onValueChange = { shop = it.take(60) },
                        label = { Text("Где купить (необязательно)") },
                        placeholder = { Text("Любой магазин рядом") },
                        leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("food_shop")
                    )
                    OutlinedTextField(
                        value = goods,
                        onValueChange = { v -> goods = v.filter { it.isDigit() }.take(6) },
                        label = { Text("Примерная сумма покупок, сом") },
                        leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("food_goods")
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(60) },
                        label = { Text("Комментарий курьеру") },
                        placeholder = { Text("Подъезд 2, позвонить у двери") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Surface(shape = RoundedCornerShape(14.dp), color = KgTurquoise.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFC62828))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Куда доставить", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(state.pickupAddress.ifBlank { "Определяем адрес…" }, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    FoodAdSlot()
                }
                val goodsSom = goods.toIntOrNull() ?: 0
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text("Доставка", modifier = Modifier.weight(1f))
                            Text("$FOOD_DELIVERY_FEE_SOM сом", fontWeight = FontWeight.Bold)
                        }
                        if (goodsSom > 0) Row(Modifier.fillMaxWidth()) {
                            Text("Покупки примерно", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("≈ $goodsSom сом", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                if (items.isBlank()) { itemsError = true; return@Button }
                                viewModel.requestFoodOrder(kind.id, items, shop, goodsSom, note)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = KgTurquoise, contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("food_order_button")
                        ) { Text("Заказать доставку • $FOOD_DELIVERY_FEE_SOM сом", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

/** Place for a partner offer on the food page; the format is chosen later. */
@Composable
private fun FoodAdSlot() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().height(64.dp).testTag("food_ad_slot")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("Здесь будет предложение партнёра", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
