package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.UberBlue
import com.example.ui.theme.UberGreen

private data class OnboardingSlide(
    val title: String,
    val subtitle: String,
    val badge: String,
    val icon: ImageVector,
    val imageRes: Int
)

@Composable
fun OnboardingOverlay(
    onComplete: () -> Unit
) {
    val slides = remember {
        listOf(
            OnboardingSlide(
                title = "KG Taxi — весь Кыргызстан в одном приложении",
                subtitle = "Такси по городу и поездки между городами Кыргызстана. Водители проверены, цена известна до поездки.",
                badge = "8 ГОРОДОВ КР",
                icon = Icons.Default.DirectionsCar,
                imageRes = R.drawable.img_uber_black_sedan_1795421299011
            ),
            OnboardingSlide(
                title = "Честная цена и живой статус",
                subtitle = "Видите цену заранее, имя водителя, машину и номер. Уведомим, когда водитель примет заказ и когда подъедет.",
                badge = "ПРОЗРАЧНО",
                icon = Icons.Default.AutoAwesome,
                imageRes = R.drawable.img_kyrgyzstan_tour_1795421299618
            ),
            OnboardingSlide(
                title = "Безопасность: PIN, SOS 112 и оплата наличными",
                subtitle = "4-значный PIN посадки, кнопка SOS (звонок 112 и SMS близким), «Поделиться поездкой» и оплата наличными водителю.",
                badge = "БЕЗОПАСНОСТЬ",
                icon = Icons.Default.Shield,
                imageRes = R.drawable.img_kg_food_hero_1795421299190
            )
        )
    }

    var page by remember { mutableIntStateOf(0) }
    val slide = slides[page]

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "KG TAXI",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp
                )
                TextButton(
                    onClick = onComplete,
                    modifier = Modifier.testTag("onboarding_skip_button")
                ) {
                    Text("Пропустить", color = Color.White.copy(alpha = 0.75f))
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    Image(
                        painter = painterResource(id = slide.imageRes),
                        contentDescription = slide.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(slide.icon, contentDescription = null, tint = UberGreen, modifier = Modifier.size(16.dp))
                            Text(slide.badge, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = slide.title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = slide.subtitle,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    slides.indices.forEach { idx ->
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (idx == page) 26.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (idx == page) UberBlue else Color.White.copy(alpha = 0.3f))
                        )
                    }
                }

                Button(
                    onClick = {
                        if (page < slides.lastIndex) page++ else onComplete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("onboarding_next_button")
                ) {
                    Text(
                        text = if (page < slides.lastIndex) "Далее" else "Начать поездки",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
