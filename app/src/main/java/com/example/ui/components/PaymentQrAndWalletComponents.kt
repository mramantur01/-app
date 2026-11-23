package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WalletTransaction
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.UberBlue
import com.example.ui.theme.UberGreen
import kotlin.math.abs

/**
 * Deterministic ELQR / MBank / O!Деньги payment QR dialog rendered cleanly on Compose Canvas.
 */
@Composable
fun ElqrPaymentDialog(
    title: String,
    amountSom: Int,
    referenceCode: String,
    onConfirmPaid: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedBank by remember { mutableStateOf("ELQR • MBank") }
    val payload = "ELQR://kgtaxi.kg/pay?ref=$referenceCode&amt=$amountSom&cur=KGS&bank=$selectedBank"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.QrCode2, contentDescription = null, tint = UberBlue)
                Text(title, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ELQR • MBank", "O!Деньги", "Элкарт").forEach { bank ->
                        FilterChip(
                            selected = selectedBank == bank,
                            onClick = { selectedBank = bank },
                            label = { Text(bank, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Crisp QR Code Box (White quiet zone for universal scanner contrast)
                Box(
                    modifier = Modifier
                        .size(196.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    DeterministicQrCanvas(payload = payload, modifier = Modifier.fillMaxSize())
                }

                Text(
                    text = "$amountSom сом",
                    fontFamily = JetBrainsMono,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = UberGreen
                )
                Text(
                    text = "Отсканируйте через $selectedBank • Счёт #$referenceCode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmPaid,
                colors = ButtonDefaults.buttonColors(containerColor = UberGreen, contentColor = Color.White),
                modifier = Modifier.testTag("confirm_elqr_paid_button")
            ) {
                Text("Оплачено по QR")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
fun DeterministicQrCanvas(payload: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val grid = 21
        val cellW = size.width / grid
        val cellH = size.height / grid
        val seed = payload.hashCode()

        fun isFinder(r: Int, c: Int): Boolean {
            val topLeft = r < 7 && c < 7
            val topRight = r < 7 && c >= grid - 7
            val bottomLeft = r >= grid - 7 && c < 7
            return topLeft || topRight || bottomLeft
        }

        fun drawFinder(startR: Int, startC: Int) {
            for (r in 0..6) {
                for (c in 0..6) {
                    val onBorder = r == 0 || r == 6 || c == 0 || c == 6
                    val onCenter = r in 2..4 && c in 2..4
                    if (onBorder || onCenter) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset((startC + c) * cellW, (startR + r) * cellH),
                            size = Size(cellW + 0.5f, cellH + 0.5f)
                        )
                    }
                }
            }
        }

        drawFinder(0, 0)
        drawFinder(0, grid - 7)
        drawFinder(grid - 7, 0)

        for (r in 0 until grid) {
            for (c in 0 until grid) {
                if (isFinder(r, c)) continue
                val h = abs((seed * 31 + r * 97 + c * 53 + payload[(r * grid + c) % payload.length].code))
                if (h % 2 == 0) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(c * cellW, r * cellH),
                        size = Size(cellW + 0.5f, cellH + 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun WalletAndReferralSection(
    walletBalanceSom: Int,
    transactions: List<WalletTransaction>,
    referralCode: String,
    referralClaimed: Boolean,
    onTopUpWallet: (Int, String) -> Unit,
    onOpenQrPay: () -> Unit,
    onShareReferral: () -> Unit,
    onClaimReferralBonus: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // KG Wallet Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = UberBlue)
                        Column {
                            Text("Кошелёк KG Taxi", fontWeight = FontWeight.Bold)
                            Text("MBank • O!Деньги • Элкарт", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        text = "$walletBalanceSom сом",
                        fontFamily = JetBrainsMono,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = UberGreen
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(500 to "MBank", 1000 to "O!Деньги", 2500 to "Элкарт").forEach { (amt, method) ->
                        FilledTonalButton(
                            onClick = { onTopUpWallet(amt, method) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).testTag("topup_${amt}_button")
                        ) {
                            Text("+${amt} с", fontFamily = JetBrainsMono, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenQrPay,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("wallet_open_elqr_button")
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = "ELQR", modifier = Modifier.size(18.dp))
                    }
                }

                transactions.take(4).forEach { tx ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tx.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text("${tx.method} • ${tx.timeLabel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = "${if (tx.amountSom >= 0) "+" else ""}${tx.amountSom}с",
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            color = if (tx.amountSom >= 0) UberGreen else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Referral Program Card ("Пригласи друга")
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = UberBlue.copy(alpha = 0.11f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = UberBlue)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Пригласи друга • +250 сом каждому", fontWeight = FontWeight.Bold, color = UberBlue)
                        Text("Ваш персональный код: $referralCode", fontFamily = JetBrainsMono, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onShareReferral,
                        colors = ButtonDefaults.buttonColors(containerColor = UberBlue, contentColor = Color.White),
                        modifier = Modifier.weight(1f).testTag("share_referral_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Поделиться")
                    }
                    OutlinedButton(
                        onClick = onClaimReferralBonus,
                        enabled = !referralClaimed,
                        modifier = Modifier.weight(1f).testTag("claim_referral_bonus_button")
                    ) {
                        Text(if (referralClaimed) "Бонус получен" else "+250 б. тест")
                    }
                }
            }
        }
    }
}
