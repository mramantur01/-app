package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.firebase.DriverRegistration
import com.example.data.firebase.RegPhoto
import com.example.data.firebase.RegistrationInfo
import com.example.data.model.CarCatalog
import com.example.data.model.KgGeoCatalog
import com.example.ui.theme.KgInk
import com.example.ui.theme.KgTurquoise
import com.example.ui.theme.KgTurquoiseBg
import com.example.ui.theme.KgTurquoiseLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private enum class RegStep { CHOICE, PASSENGER, DRIVER_DOCS, DRIVER_CAR, DRIVER_PHOTOS, DRIVER_STATUS }

/**
 * Registration after the first Google sign-in: "Кто вы?" → passenger (passport) or
 * driver (passport + licence → car from the list → photo check of the car).
 * Tariffs for the car are chosen automatically; the owner approves the driver.
 */
@Composable
fun RegistrationScreen(
    registration: RegistrationInfo?,
    startAsDriver: Boolean,
    sending: Boolean,
    defaultName: String,
    defaultPhone: String,
    defaultCity: String,
    onRegisterPassenger: (name: String, phone: String, passport: String, photo: Uri) -> Unit,
    onRegisterDriver: (DriverRegistration, Map<RegPhoto, Uri>) -> Unit,
    onClose: () -> Unit
) {
    val driverStatus = registration?.driverStatus
    var step by rememberSaveable {
        mutableStateOf(
            when {
                startAsDriver && (driverStatus == "pending" || driverStatus == "approved") -> RegStep.DRIVER_STATUS
                startAsDriver -> RegStep.DRIVER_DOCS
                else -> RegStep.CHOICE
            }
        )
    }

    // Shared form state (kept across steps).
    var fullName by rememberSaveable { mutableStateOf(defaultName.takeIf { it != "Пассажир" && it != "Гость" }.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(defaultPhone) }
    var passport by rememberSaveable { mutableStateOf("") }
    var license by rememberSaveable { mutableStateOf("") }
    var experience by rememberSaveable { mutableStateOf("") }
    var payPhone by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf(defaultCity) }
    var make by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    var color by rememberSaveable { mutableStateOf("") }
    var plate by rememberSaveable { mutableStateOf("") }
    val photos = remember { mutableStateMapOf<RegPhoto, Uri>() }

    val back: () -> Unit = {
        step = when (step) {
            RegStep.PASSENGER, RegStep.DRIVER_DOCS -> if (startAsDriver) { onClose(); step } else RegStep.CHOICE
            RegStep.DRIVER_CAR -> RegStep.DRIVER_DOCS
            RegStep.DRIVER_PHOTOS -> RegStep.DRIVER_CAR
            RegStep.CHOICE, RegStep.DRIVER_STATUS -> { onClose(); step }
        }
    }
    BackHandler(enabled = !sending) { back() }

    Surface(color = KgTurquoiseBg, modifier = Modifier.fillMaxSize().testTag("registration_screen")) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = back, enabled = !sending) {
                    Icon(
                        if (step == RegStep.CHOICE || step == RegStep.DRIVER_STATUS) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = KgInk
                    )
                }
                Text(
                    when (step) {
                        RegStep.CHOICE -> "Регистрация"
                        RegStep.PASSENGER -> "Регистрация пассажира"
                        RegStep.DRIVER_DOCS -> "Водитель • 1 из 3: документы"
                        RegStep.DRIVER_CAR -> "Водитель • 2 из 3: автомобиль"
                        RegStep.DRIVER_PHOTOS -> "Водитель • 3 из 3: фотоконтроль"
                        RegStep.DRIVER_STATUS -> "Заявка водителя"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = KgInk
                )
            }
            if (step in listOf(RegStep.DRIVER_DOCS, RegStep.DRIVER_CAR, RegStep.DRIVER_PHOTOS)) {
                val progress = when (step) { RegStep.DRIVER_DOCS -> 1f / 3; RegStep.DRIVER_CAR -> 2f / 3; else -> 1f }
                LinearProgressIndicator(
                    progress = { progress },
                    color = KgTurquoise,
                    trackColor = KgTurquoiseLight,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (step) {
                    RegStep.CHOICE -> ChoiceStep(
                        onPassenger = { step = RegStep.PASSENGER },
                        onDriver = { step = RegStep.DRIVER_DOCS }
                    )

                    RegStep.PASSENGER -> {
                        Text(
                            "Для безопасности поездок нужен паспорт. Его видит только администрация KG Taxi.",
                            color = KgInk.copy(alpha = 0.75f)
                        )
                        RegField(fullName, { fullName = it }, "ФИО", "reg_full_name")
                        RegField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "Телефон", "reg_phone", KeyboardType.Phone)
                        RegField(passport, { passport = it.uppercase().take(20) }, "Номер паспорта (ID-карты)", "reg_passport")
                        PhotoSlot(RegPhoto.PASSPORT, photos[RegPhoto.PASSPORT]) { photos[RegPhoto.PASSPORT] = it }
                    }

                    RegStep.DRIVER_DOCS -> {
                        Text(
                            "Нужны паспорт и водительское удостоверение (ВУ). Документы видит только администрация.",
                            color = KgInk.copy(alpha = 0.75f)
                        )
                        RegField(fullName, { fullName = it }, "ФИО (как в паспорте)", "reg_full_name")
                        RegField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "Телефон", "reg_phone", KeyboardType.Phone)
                        Text("Город работы", fontWeight = FontWeight.SemiBold, color = KgInk)
                        ChipRow(KgGeoCatalog.cities.map { it.name }, city) { city = it }
                        RegField(passport, { passport = it.uppercase().take(20) }, "Номер паспорта (ID-карты)", "reg_passport")
                        PhotoSlot(RegPhoto.PASSPORT, photos[RegPhoto.PASSPORT]) { photos[RegPhoto.PASSPORT] = it }
                        RegField(license, { license = it.uppercase().take(30) }, "Номер водительского удостоверения (ВУ)", "reg_license")
                        PhotoSlot(RegPhoto.LICENSE, photos[RegPhoto.LICENSE]) { photos[RegPhoto.LICENSE] = it }
                        RegField(experience, { experience = it.filter { c -> c.isDigit() }.take(2) }, "Стаж вождения, лет", "reg_experience", KeyboardType.Number)
                        RegField(payPhone, { payPhone = it.filter { c -> c.isDigit() || c == '+' } }, "Номер для переводов (MBank / O!Деньги), необязательно", "reg_pay_phone", KeyboardType.Phone)
                    }

                    RegStep.DRIVER_CAR -> {
                        Text("Марка", fontWeight = FontWeight.SemiBold, color = KgInk)
                        ChipFlow(CarCatalog.makes.map { it.name }, make) { make = it; model = "" }
                        if (make.isNotBlank()) {
                            Text("Модель", fontWeight = FontWeight.SemiBold, color = KgInk)
                            ChipFlow(CarCatalog.modelsOf(make).map { it.name }, model) { model = it }
                        }
                        Text("Год выпуска", fontWeight = FontWeight.SemiBold, color = KgInk)
                        ChipRow(CarCatalog.years.map { it.toString() }, year?.toString().orEmpty()) { year = it.toInt() }
                        Text("Цвет", fontWeight = FontWeight.SemiBold, color = KgInk)
                        ChipFlow(CarCatalog.colors, color) { color = it }
                        RegField(plate, { plate = it.uppercase().take(12) }, "Госномер (например 01KG123ABC)", "reg_plate")
                        val y = year
                        if (make.isNotBlank() && model.isNotBlank() && y != null) {
                            val tariffs = CarCatalog.tariffsFor(make, model, y)
                            TariffResultCard(tariffs)
                        }
                    }

                    RegStep.DRIVER_PHOTOS -> {
                        Text(
                            "Сфотографируйте автомобиль со всех сторон при хорошем свете. Номер должен быть виден спереди и сзади.",
                            color = KgInk.copy(alpha = 0.75f)
                        )
                        RegPhoto.CAR.forEach { kind ->
                            PhotoSlot(kind, photos[kind]) { photos[kind] = it }
                        }
                    }

                    RegStep.DRIVER_STATUS -> DriverStatusStep(driverStatus, registration?.rejectReason) {
                        step = RegStep.DRIVER_DOCS
                    }
                }
            }

            // Bottom button
            val canContinue = when (step) {
                RegStep.CHOICE, RegStep.DRIVER_STATUS -> false
                RegStep.PASSENGER -> fullName.trim().length >= 3 && phone.count { it.isDigit() } >= 9 &&
                    passport.length >= 6 && photos[RegPhoto.PASSPORT] != null
                RegStep.DRIVER_DOCS -> fullName.trim().length >= 3 && phone.count { it.isDigit() } >= 9 &&
                    passport.length >= 6 && license.length >= 6 && experience.isNotBlank() &&
                    photos[RegPhoto.PASSPORT] != null && photos[RegPhoto.LICENSE] != null
                RegStep.DRIVER_CAR -> make.isNotBlank() && model.isNotBlank() && year != null && color.isNotBlank() &&
                    plate.length >= 5 && CarCatalog.tariffsFor(make, model, year ?: 0).isNotEmpty()
                RegStep.DRIVER_PHOTOS -> RegPhoto.CAR.all { photos[it] != null }
            }
            if (step != RegStep.CHOICE && step != RegStep.DRIVER_STATUS) {
                Surface(color = Color.White, shadowElevation = 8.dp) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        if (!canContinue && !sending) {
                            Text(
                                when (step) {
                                    RegStep.DRIVER_PHOTOS -> "Нужны все 5 фото"
                                    RegStep.DRIVER_CAR -> "Выберите марку, модель, год, цвет и введите номер"
                                    else -> "Заполните все поля и добавьте фото документов"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = KgInk.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Button(
                            onClick = {
                                when (step) {
                                    RegStep.PASSENGER -> onRegisterPassenger(fullName, phone, passport, photos.getValue(RegPhoto.PASSPORT))
                                    RegStep.DRIVER_DOCS -> step = RegStep.DRIVER_CAR
                                    RegStep.DRIVER_CAR -> step = RegStep.DRIVER_PHOTOS
                                    RegStep.DRIVER_PHOTOS -> onRegisterDriver(
                                        DriverRegistration(
                                            fullName = fullName,
                                            phone = phone,
                                            city = city,
                                            passportNumber = passport,
                                            licenseNumber = license,
                                            experienceYears = experience.toIntOrNull() ?: 0,
                                            payPhone = payPhone,
                                            carMake = make,
                                            carModel = model,
                                            carYear = year ?: 0,
                                            carColor = color,
                                            plate = plate,
                                            tariffs = CarCatalog.tariffsFor(make, model, year ?: 0)
                                        ),
                                        photos.toMap()
                                    )
                                    else -> Unit
                                }
                            },
                            enabled = canContinue && !sending,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = KgTurquoise, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("reg_continue")
                        ) {
                            if (sending) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Отправляем…")
                            } else {
                                Text(
                                    when (step) {
                                        RegStep.PASSENGER -> "Завершить регистрацию"
                                        RegStep.DRIVER_PHOTOS -> "Отправить на проверку"
                                        else -> "Далее"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceStep(onPassenger: () -> Unit, onDriver: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Text(
        "Кто вы?",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,
        color = KgInk,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        "Выберите, как будете пользоваться KG Taxi",
        color = KgInk.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    RoleCard(
        icon = Icons.Default.Person,
        title = "Пассажир",
        subtitle = "Заказываю такси и межгород.\nНужен только паспорт.",
        filled = true,
        tag = "reg_choice_passenger",
        onClick = onPassenger
    )
    RoleCard(
        icon = Icons.Default.DirectionsCar,
        title = "Водитель",
        subtitle = "Зарабатываю на своём авто.\nНужны паспорт, ВУ и фото машины.",
        filled = false,
        tag = "reg_choice_driver",
        onClick = onDriver
    )
}

@Composable
private fun RoleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    filled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (filled) KgTurquoise else Color.White,
        border = if (filled) null else BorderStroke(2.dp, KgTurquoise),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (filled) Color.White.copy(alpha = 0.22f) else KgTurquoiseLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (filled) Color.White else KgTurquoise, modifier = Modifier.size(36.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (filled) Color.White else KgInk
                )
                Text(subtitle, color = if (filled) Color.White.copy(alpha = 0.9f) else KgInk.copy(alpha = 0.7f))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = if (filled) Color.White else KgTurquoise)
        }
    }
}

@Composable
private fun RegField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    tag: String,
    keyboard: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = KgTurquoise,
            focusedLabelColor = KgTurquoise,
            cursorColor = KgTurquoise,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
}

@Composable
private fun ChipRow(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                label = { Text(item) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KgTurquoise,
                    selectedLabelColor = Color.White,
                    containerColor = Color.White
                )
            )
        }
    }
}

/** Chips that wrap onto new lines: every option is visible without side scrolling. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                label = { Text(item) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KgTurquoise,
                    selectedLabelColor = Color.White,
                    containerColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun TariffResultCard(tariffs: List<String>) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (tariffs.isEmpty()) Color(0xFFFFEBEE) else KgTurquoiseLight,
        modifier = Modifier.fillMaxWidth().testTag("reg_tariff_result")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (tariffs.isEmpty()) {
                Text("Авто не подходит", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                Text("Принимаем машины от ${CarCatalog.MIN_YEAR} года выпуска.", color = KgInk)
            } else {
                Text("Ваше авто подходит для тарифов:", fontWeight = FontWeight.Bold, color = KgInk)
                Text(
                    tariffs.joinToString(" • ") { CarCatalog.tariffLabel(it) },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = KgTurquoise
                )
                Text(
                    "Тариф выбран автоматически по модели и году. Комфорт — с ${CarCatalog.COMFORT_MIN_YEAR} года.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KgInk.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun DriverStatusStep(status: String?, rejectReason: String?, onReapply: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    val (icon, title, text) = when (status) {
        "approved" -> Triple(Icons.Default.CheckCircle, "Вы водитель KG Taxi", "Откройте вкладку «Водитель» и нажмите GO, чтобы выйти на линию.")
        "rejected" -> Triple(Icons.Default.Cancel, "Заявка отклонена", rejectReason ?: "Документы не прошли проверку.")
        else -> Triple(Icons.Default.HourglassTop, "Заявка на проверке", "Администрация проверит документы и фото авто. Мы сообщим, когда откроется экран водителя.")
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = KgTurquoise, modifier = Modifier.size(72.dp))
    }
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = KgInk,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text(text, color = KgInk.copy(alpha = 0.75f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (status == "rejected") {
        Button(
            onClick = onReapply,
            colors = ButtonDefaults.buttonColors(containerColor = KgTurquoise),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Подать заново") }
    }
}

/** One photo: preview, "Сфотографировать" (camera) and "Из галереи". */
@Composable
private fun PhotoSlot(kind: RegPhoto, uri: Uri?, onPicked: (Uri) -> Unit) {
    val context = LocalContext.current
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pendingUri
        if (ok && u != null) onPicked(u)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) onPicked(picked)
    }
    var preview by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        preview = uri?.let { u ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                    context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (uri != null) KgTurquoise else Color(0xFFD5E3E5)),
        modifier = Modifier.fillMaxWidth().testTag("reg_photo_${kind.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)).background(KgTurquoiseLight),
                contentAlignment = Alignment.Center
            ) {
                val img = preview
                if (img != null) {
                    Image(img, contentDescription = kind.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = KgTurquoise)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (uri != null) Icon(Icons.Default.CheckCircle, contentDescription = "Готово", tint = KgTurquoise, modifier = Modifier.size(18.dp))
                    Text(kind.title, fontWeight = FontWeight.SemiBold, color = KgInk)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val dir = File(context.cacheDir, "reg_photos").apply { mkdirs() }
                            val file = File(dir, "${kind.id}_${System.currentTimeMillis()}.jpg")
                            val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            pendingUri = u
                            runCatching { camera.launch(u) }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = KgTurquoiseLight, contentColor = KgInk),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (uri == null) "Снять" else "Переснять")
                    }
                    TextButton(
                        onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        colors = ButtonDefaults.textButtonColors(contentColor = KgTurquoise)
                    ) { Text("Из галереи") }
                }
            }
        }
    }
}
