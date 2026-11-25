package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.FeatureFlags
import com.example.data.local.LocalTripEntity
import com.example.data.model.PaymentMethod
import com.example.i18n.KgStrings
import com.example.ui.KgTaxiUiState
import com.example.ui.KgTaxiViewModel
import com.example.ui.components.ElqrPaymentDialog
import com.example.ui.components.FamilyLiveTrackingCard
import com.example.ui.components.LostFoundAndFavoriteDriversSection
import com.example.ui.components.LostFoundTicketDialog
import com.example.ui.components.ThemeAndMapAppearanceCard
import com.example.ui.components.WalletAndReferralSection
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileWalletOverlay(
    strings: KgStrings,
    state: KgTaxiUiState,
    isSigningIn: Boolean,
    authError: String?,
    viewModel: KgTaxiViewModel,
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit,
    onExportAllPdf: () -> Unit,
    onExportSingleTripPdf: (LocalTripEntity) -> Unit,
    onReopenOnboarding: () -> Unit,
    onDismiss: () -> Unit
) {
    // null = main menu (like Yandex Go); otherwise one section is open.
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler { if (section != null) section = null else onDismiss() }
    val context = LocalContext.current

    var nameInput by remember(state.profile.displayName) { mutableStateOf(state.profile.displayName) }
    var homeInput by remember(state.profile.homeAddress) { mutableStateOf(state.profile.homeAddress) }
    var workInput by remember(state.profile.workAddress) { mutableStateOf(state.profile.workAddress) }
    var phoneInput by remember(state.profile.phone) { mutableStateOf(state.profile.phone) }
    var companyInput by remember(state.profile.companyName) { mutableStateOf(state.profile.companyName) }
    var companyEmailInput by remember(state.profile.companyEmail) { mutableStateOf(state.profile.companyEmail) }
    var companyInnInput by remember(state.businessCompanyInn) { mutableStateOf(state.businessCompanyInn) }
    var showAddFamilyDialog by remember { mutableStateOf(false) }
    var showWalletQrDialog by remember { mutableStateOf(false) }
    var businessTripsOnly by remember { mutableStateOf(false) }
    var lostFoundTargetTrip by remember { mutableStateOf<LocalTripEntity?>(null) }

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
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { if (section != null) section = null else onDismiss() }, modifier = Modifier.testTag("close_profile_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                    Text(profileSectionTitle(section), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                }
                if (section == "orders") FilledTonalButton(
                    onClick = onExportAllPdf,
                    modifier = Modifier.testTag("profile_export_pdf_button")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (section == null) {
                    PassengerProfileMenu(
                        state = state,
                        onOpen = { section = it },
                        onWorkInTaxi = {
                            if (com.example.data.model.AppRole.DRIVER in state.allowedRoles) viewModel.setRole(com.example.data.model.AppRole.DRIVER)
                            else viewModel.openRegistration(asDriver = true)
                            onDismiss()
                        }
                    )
                }
                if (section == "family") ClosePeopleSection(people = state.passengerPeople, onSave = viewModel::savePassengerPeople)
                if (section == "promo") PromoSection(state = state, viewModel = viewModel)
                if (section == "safety") SafetySection(state = state, onOpenFamily = { section = "family" })
                if (section == "support") SupportSection(sending = state.supportSending, onSend = { viewModel.sendSupportMessage(it, null) })
                if (section == "info") InfoSection()
                if (section == "settings") GeneralSettingsSection(state = state, viewModel = viewModel)
                if (section == "settings") {
                // Google Cloud Account Card (Works without login + Google Sign-In for cloud sync!)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.profile.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (state.isSignedIn) "Вы вошли • ${state.profile.phone.ifBlank { state.profile.email }}" else "Войдите, чтобы заказывать такси",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                                )
                            }
                            Surface(shape = RoundedCornerShape(10.dp), color = UberGreen) {
                                Text(
                                    text = "${state.profile.bonusPoints} б.",
                                    fontFamily = JetBrainsMono,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        if (!state.isSignedIn) {
                            Button(
                                onClick = onSignInWithGoogle,
                                enabled = !isSigningIn,
                                colors = ButtonDefaults.buttonColors(containerColor = UberBlue, contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("profile_google_sign_in_button")
                            ) {
                                if (isSigningIn) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Войти через Google", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = onSignOut,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                                modifier = Modifier.fillMaxWidth().testTag("profile_sign_out_button")
                            ) {
                                Text("Выйти из аккаунта")
                            }
                        }
                        if (!authError.isNullOrBlank()) {
                            Text(authError, color = UberAmber, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                }

                if (section == "orders") {
                // My rides (real orders), support and account deletion.
                if (state.isSignedIn) {
                    AccountExtrasSection(state = state, viewModel = viewModel, onClose = onDismiss)
                }
                }

                if (section == "addresses") {
                // Editable Profile & Home/Work Addresses (Auto-saved locally + synced to Firestore)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Профиль и быстрые адреса", fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it.take(20) },
                            label = { Text("Телефон (для водителя и SOS)") },
                            placeholder = { Text("0555 123 456") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth().testTag("profile_phone_input")
                        )
                        OutlinedTextField(value = homeInput, onValueChange = { homeInput = it }, label = { Text(strings.home) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = workInput, onValueChange = { workInput = it }, label = { Text(strings.work) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = {
                                viewModel.updateProfile {
                                    it.copy(
                                        displayName = nameInput.trim().ifBlank { it.displayName },
                                        homeAddress = homeInput.trim(),
                                        workAddress = workInput.trim(),
                                        phone = phoneInput.trim()
                                    )
                                }
                            },
                            modifier = Modifier.align(Alignment.End).testTag("save_profile_addresses_button")
                        ) {
                            Text(strings.save)
                        }
                    }
                }
                }

                if (section == "promo") {
                // Wallet Balance, Top-Up Ledger, ELQR & Referral Program ("Пригласи друга")
                if (FeatureFlags.WALLET) WalletAndReferralSection(
                    walletBalanceSom = state.walletBalanceSom,
                    transactions = state.walletTransactions,
                    referralCode = state.referralCode,
                    referralClaimed = state.referralClaimed,
                    onTopUpWallet = viewModel::topUpWallet,
                    onOpenQrPay = { showWalletQrDialog = true },
                    onShareReferral = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Присоединяйся к KG Taxi! Используй мой код ${state.referralCode} и получи 250 сом на первую поездку по Кыргызстану."
                            )
                        }
                        context.startActivity(Intent.createChooser(share, "Поделиться кодом KG Taxi"))
                    },
                    onClaimReferralBonus = viewModel::claimReferralBonus
                )
                }

                if (section == "payment") {
                // Kyrgyzstan Payment Methods (Наличные, MBank, O!Деньги, Элкарт, Visa/Mastercard)
                Text(strings.paymentMethods, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaymentMethod.entries.filter { FeatureFlags.ONLINE_PAYMENTS || it == PaymentMethod.CASH }.forEach { pm ->
                        val selected = if (FeatureFlags.ONLINE_PAYMENTS) state.profile.paymentMethod == pm.code else pm == PaymentMethod.CASH
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateProfile { it.copy(paymentMethod = pm.code) } },
                            label = { Text("${pm.displayName} (${pm.badge})") },
                            modifier = Modifier.testTag("payment_${pm.code.lowercase()}")
                        )
                    }
                }
                }

                if (section == "payment") {
                // KG Plus subscription: paid, so hidden until there is a real payment provider.
                if (FeatureFlags.WALLET)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (state.profile.kgOneActive) UberGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Подписка KG Plus (KG One)",
                                    fontWeight = FontWeight.Black,
                                    color = if (state.profile.kgOneActive) UberGreen else MaterialTheme.colorScheme.onSurface
                                )
                                Text("299 сом / мес", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono)
                            }
                            Switch(
                                checked = state.profile.kgOneActive,
                                onCheckedChange = { active -> viewModel.updateProfile { it.copy(kgOneActive = active) } },
                                modifier = Modifier.testTag("kg_one_switch")
                            )
                        }
                        Text(
                            text = "• Кэшбэк 10% баллами на все поездки и еду\n• Бесплатная Priority-подача (0 с) и доставка еды 0 с\n• -50% защита от повышенного спроса + скидка 7%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                }

                if (section == "settings") {
                // Theme (Auto / Light / Dark) & Map Style (Standard / Satellite / Night)
                ThemeAndMapAppearanceCard(
                    themeMode = state.themeMode,
                    mapLayerStyle = state.mapLayerStyle,
                    onSelectThemeMode = viewModel::setThemeMode,
                    onSelectMapLayerStyle = viewModel::setMapLayerStyle
                )
                }

                if (section == "business") {
                // Business Profile (Uber for Business)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.businessProfile, fontWeight = FontWeight.Bold)
                                Text("Корпоративные чеки B2B и отчётность", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = state.profile.businessProfileActive,
                                onCheckedChange = { active -> viewModel.updateProfile { it.copy(businessProfileActive = active) } },
                                modifier = Modifier.testTag("business_profile_switch")
                            )
                        }
                        if (state.profile.businessProfileActive) {
                            OutlinedTextField(value = companyInput, onValueChange = { companyInput = it }, label = { Text("Компания") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = companyInnInput, onValueChange = { companyInnInput = it.filter(Char::isDigit).take(14) }, label = { Text("ИНН компании (КР, 14 цифр)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = companyEmailInput, onValueChange = { companyEmailInput = it }, label = { Text("Email для корпоративных чеков") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            FilledTonalButton(
                                onClick = {
                                    viewModel.updateProfile { it.copy(companyName = companyInput, companyEmail = companyEmailInput) }
                                    viewModel.setBusinessCompanyInn(companyInnInput)
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) { Text(strings.save) }
                        }
                    }
                }
                }

                if (section == "support") {
                // Lost & Found Support Tickets + Favorite & Blocked Drivers Management
                LostFoundAndFavoriteDriversSection(
                    tickets = state.lostItemTickets,
                    favoriteDrivers = state.favoriteDrivers,
                    blockedDrivers = state.blockedDrivers,
                    onToggleFavoriteDriver = viewModel::toggleFavoriteDriver,
                    onToggleBlockedDriver = viewModel::toggleBlockedDriver
                )
                }

                if (section == "family" && FeatureFlags.FAMILY_LIMITS) {
                // Upgraded Family Profile with Shared Balance & Live Ride Tracking
                FamilyLiveTrackingCard(
                    members = state.familyMembers,
                    sharedWalletSom = state.walletBalanceSom,
                    onOpenAddMember = { showAddFamilyDialog = true },
                    onRemoveMember = viewModel::removeFamilyMember,
                    onTrackMember = { member ->
                        viewModel.trackFamilyMember(member)
                        onDismiss()
                    }
                )
                }

                if (section == "addresses") {
                // Saved Places & Offline Routes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.savedPlaces, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { viewModel.addSavedPlace("Маршрут (${state.selectedCity.name})", state.dropoffAddress, "ROUTE") }) {
                        Text("+ Сохранить текущий")
                    }
                }
                state.savedPlaces.forEach { place ->
                    Surface(
                        onClick = {
                            viewModel.loadSavedPlaceToRoute(place)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().testTag("load_saved_place_${place.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (place.category.contains("ROUTE")) Icons.Default.OfflinePin else Icons.Default.Place,
                                    contentDescription = null,
                                    tint = UberBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text("${place.label} (${place.city})", fontWeight = FontWeight.SemiBold)
                                    Text(place.address, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                            }
                            IconButton(onClick = { viewModel.deleteSavedPlace(place.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete saved place")
                            }
                        }
                    }
                }
                }

                if (section == "orders") {
                // Trip History + B2B Filter + Lost & Found + Receipt Export
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.tripHistory, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FilterChip(
                        selected = businessTripsOnly,
                        onClick = { businessTripsOnly = !businessTripsOnly },
                        label = { Text("Только B2B", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("filter_b2b_trips_chip")
                    )
                }
                val visibleTrips = remember(state.trips, businessTripsOnly) {
                    val filtered = if (businessTripsOnly) state.trips.filter { it.businessTrip } else state.trips
                    filtered.take(10)
                }
                visibleTrips.forEach { trip ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${trip.tariffName} • ${trip.city}" + if (trip.businessTrip) " (B2B)" else "", fontWeight = FontWeight.Bold)
                                    Text("${trip.pickupAddress} → ${trip.dropoffAddress}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    Text(
                                        "${state.displayCurrency.formatSom(trip.totalPriceSom)} • ${trip.paymentMethod}",
                                        fontFamily = JetBrainsMono,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = UberBlue
                                    )
                                    if (trip.lostItemNote.isNotBlank()) {
                                        Text("Бюро находок: ${trip.lostItemNote}", style = MaterialTheme.typography.labelSmall, color = UberAmber)
                                    }
                                }
                                Row {
                                    IconButton(
                                        onClick = { lostFoundTargetTrip = trip },
                                        modifier = Modifier.testTag("lost_found_trip_${trip.id}")
                                    ) {
                                        Icon(Icons.Default.WorkOutline, contentDescription = "Lost item", tint = UberAmber)
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.repeatTripFromHistory(trip)
                                            onDismiss()
                                        },
                                        modifier = Modifier.testTag("repeat_trip_${trip.id}")
                                    ) {
                                        Icon(Icons.Default.Replay, contentDescription = "Repeat trip", tint = UberBlue)
                                    }
                                    IconButton(onClick = { onExportSingleTripPdf(trip) }) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export receipt PDF")
                                    }
                                }
                            }
                        }
                    }
                }
                }

                if (section == "info") {
                // Replay Interactive Onboarding Guide
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onReopenOnboarding()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("replay_onboarding_button")
                ) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Показать интерактивный гид KG Taxi")
                }
                }
            }
        }
    }

    if (showWalletQrDialog) {
        ElqrPaymentDialog(
            title = "Пополнение кошелька (ELQR)",
            amountSom = 1000,
            referenceCode = state.referralCode,
            onConfirmPaid = {
                viewModel.topUpWallet(1000, "ELQR • MBank")
                showWalletQrDialog = false
            },
            onDismiss = { showWalletQrDialog = false }
        )
    }

    if (showAddFamilyDialog) {
        var famName by remember { mutableStateOf("") }
        var famRelation by remember { mutableStateOf("Teen") }
        var famPhone by remember { mutableStateOf("+996 ") }
        var famLimit by remember { mutableStateOf("10000") }
        AlertDialog(
            onDismissRequest = { showAddFamilyDialog = false },
            title = { Text("Добавить в семейный профиль") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = famName, onValueChange = { famName = it }, label = { Text("Имя") })
                    OutlinedTextField(value = famRelation, onValueChange = { famRelation = it }, label = { Text("Роль (Teen / Семья)") })
                    OutlinedTextField(value = famPhone, onValueChange = { famPhone = it }, label = { Text("Телефон") })
                    OutlinedTextField(value = famLimit, onValueChange = { famLimit = it.filter(Char::isDigit) }, label = { Text("Лимит (сом/мес)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addFamilyMember(famName, famRelation, famPhone, famLimit.toIntOrNull() ?: 10000)
                    showAddFamilyDialog = false
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showAddFamilyDialog = false }) { Text(strings.cancel) }
            }
        )
    }

    val lfTrip = lostFoundTargetTrip
    if (lfTrip != null) {
        LostFoundTicketDialog(
            trip = lfTrip,
            onSubmitTicket = { cat, note -> viewModel.submitLostItemTicket(lfTrip, cat, note) },
            onDismiss = { lostFoundTargetTrip = null }
        )
    }
}


/** Family and trusted contacts: quick "order for them" and sending them your trips. */
@Composable
private fun ClosePeopleSection(people: List<com.example.util.ClosePerson>, onSave: (List<com.example.util.ClosePerson>) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().testTag("close_people_section")) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Близкие и семья", fontWeight = FontWeight.Bold)
            Text(
                "Можно быстро заказать такси для них. Если включить «Получает мои поездки», в поездке появится кнопка — отправить им маршрут и машину.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            people.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name.ifBlank { "Без имени" }, fontWeight = FontWeight.SemiBold)
                        Text(p.phone, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(checked = p.shareTrips, onCheckedChange = { on -> onSave(people.mapIndexed { j, q -> if (j == i) q.copy(shareTrips = on) else q }) })
                        Text("Получает поездки", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { onSave(people.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Default.Delete, contentDescription = "Удалить") }
                }
            }
            if (people.size < com.example.util.PassengerPeople.MAX) {
                OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().testTag("close_people_add")) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Добавить близкого")
                }
            }
        }
    }
    if (adding) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("+996 ") }
        var share by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Близкий человек") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Имя (мама, сын…)") }, singleLine = true)
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(20) },
                        label = { Text("Телефон") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Получает мои поездки", modifier = Modifier.weight(1f))
                        Switch(checked = share, onCheckedChange = { share = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSave(people + com.example.util.ClosePerson(name.trim(), phone.trim(), share)); adding = false },
                    enabled = phone.count { it.isDigit() } >= 9
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Отмена") } }
        )
    }
}


private fun profileSectionTitle(section: String?): String = when (section) {
    "orders" -> "Заказы"
    "support" -> "Поддержка"
    "addresses" -> "Адреса"
    "settings" -> "Настройки"
    "promo" -> "Скидки и промокод"
    "payment" -> "Способы оплаты"
    "business" -> "Бизнес-аккаунт"
    "family" -> "Близкие и семья"
    "safety" -> "Безопасность"
    "info" -> "Информация"
    else -> "Профиль"
}

/** Main profile screen: photo, name, phone, 4 round buttons and a list (like Yandex Go). */
@Composable
private fun PassengerProfileMenu(state: KgTaxiUiState, onOpen: (String) -> Unit, onWorkInTaxi: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().testTag("profile_menu")) {
        val name = state.profile.displayName.ifBlank { "Пассажир" }
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = KgTurquoiseLight, modifier = Modifier.size(92.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = KgTurquoiseDark)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(state.profile.phone.ifBlank { "Телефон не указан" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val myStars = state.passengerRides.mapNotNull { it.passengerRating }
        Text(
            if (myStars.isEmpty()) "Мой рейтинг: появится после первых оценок" else "Мой рейтинг ★ ${String.format(java.util.Locale.US, "%.2f", myStars.average())} (${myStars.size})",
            style = MaterialTheme.typography.labelLarge, color = KgTurquoiseDark, modifier = Modifier.testTag("profile_my_rating")
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ProfileRoundButton(Icons.Default.Schedule, "Заказы") { onOpen("orders") }
            ProfileRoundButton(Icons.Default.SupportAgent, "Поддержка") { onOpen("support") }
            ProfileRoundButton(Icons.Default.Place, "Адреса") { onOpen("addresses") }
            ProfileRoundButton(Icons.Default.Settings, "Настройки") { onOpen("settings") }
        }
        Spacer(Modifier.height(18.dp))
        ProfileMenuGroup {
            ProfileMenuItem(Icons.Default.CardGiftcard, "Скидки и промокод", "Ввести промокод, пригласить друга") { onOpen("promo") }
            ProfileMenuItem(Icons.Default.Payments, "Способы оплаты", "Наличные или QR") { onOpen("payment") }
        }
        Spacer(Modifier.height(12.dp))
        ProfileMenuGroup {
            ProfileMenuItem(Icons.Default.DirectionsCar, "Работа в такси", if (com.example.data.model.AppRole.DRIVER in state.allowedRoles) "Перейти в режим водителя" else "Стать водителем KG Taxi", onWorkInTaxi)
            ProfileMenuItem(Icons.Default.BusinessCenter, "Бизнес-аккаунт", "Поездки на компанию и чеки") { onOpen("business") }
            ProfileMenuItem(Icons.Default.FamilyRestroom, "Близкие и семья", if (state.passengerPeople.isEmpty()) "Заказывать для близких" else "${state.passengerPeople.size} чел.") { onOpen("family") }
            ProfileMenuItem(Icons.Default.Shield, "Безопасность", "SOS, PIN-код, поездка близким") { onOpen("safety") }
        }
        Spacer(Modifier.height(12.dp))
        ProfileMenuGroup {
            ProfileMenuItem(Icons.Default.Info, "Информация", "О приложении, правила") { onOpen("info") }
        }
    }
}

@Composable
private fun ProfileRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(82.dp)) {
        Surface(onClick = onClick, shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(58.dp).testTag("profile_btn_$label")) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, tint = KgInk, modifier = Modifier.size(26.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProfileMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
private fun ProfileMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth().testTag("profile_item_$title")) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = KgInk, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Promo code and "invite a friend" code. */
@Composable
private fun PromoSection(state: KgTaxiUiState, viewModel: KgTaxiViewModel) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Промокод", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = state.promoCodeInput,
            onValueChange = { viewModel.setPromoInput(it.uppercase().take(20)) },
            label = { Text("Введите промокод") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("profile_promo_input")
        )
        Button(onClick = viewModel::applyPromoCode, enabled = state.promoCodeInput.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Применить") }
        Text("Скидка применится к следующему заказу.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val code = viewModel.myInviteCode()
        if (code != null) {
            HorizontalDivider()
            Text("Пригласите друга", fontWeight = FontWeight.Bold)
            Text("Друг получит скидку ${state.appConfig.referralPercent}% на первую поездку по вашему коду.", style = MaterialTheme.typography.bodySmall)
            Text(code, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = KgTurquoiseDark)
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Мой код в KG Taxi: $code — скидка на первую поездку. Скачать: https://mramantur-site.vercel.app/")
                }
                context.startActivity(Intent.createChooser(send, "Поделиться кодом"))
            }, modifier = Modifier.fillMaxWidth()) { Text("Поделиться кодом") }
        }
    }
}

/** Safety: SOS, PIN code, sending the trip to close people. */
@Composable
private fun SafetySection(state: KgTaxiUiState, onOpenFamily: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Icons.Default.VerifiedUser to "Все водители проверены: паспорт, права, справка о несудимости, ОСАГО и фотоконтроль машины каждые 30 дней.",
            Icons.Default.Pin to "PIN-код в поездке помогает пассажиру и водителю убедиться, что они нашли друг друга.",
            Icons.Default.Sos to "Кнопка SOS в поездке — быстрый звонок 112 и сигнал диспетчеру.",
            Icons.Default.Share to "«Поделиться» в поездке — отправьте маршрут и машину кому угодно."
        ).forEach { (icon, text) ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null, tint = KgTurquoiseDark, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider()
        Text("Доверенные контакты", fontWeight = FontWeight.Bold)
        val sharing = state.passengerPeople.filter { it.shareTrips }
        Text(
            if (sharing.isEmpty()) "Добавьте близких — в поездке появится кнопка «Отправить поездку близким»."
            else "Получают ваши поездки: ${sharing.joinToString { it.name.ifBlank { it.phone } }}",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(onClick = onOpenFamily, modifier = Modifier.fillMaxWidth()) { Text("Близкие и семья") }
    }
}

/** Message to support (answers come to the owner panel). */
@Composable
private fun SupportSection(sending: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Напишите нам: поможем с поездкой, оплатой или забытыми вещами. Ответим в ближайшее время.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(value = text, onValueChange = { text = it.take(1000) }, label = { Text("Ваш вопрос") }, minLines = 4, modifier = Modifier.fillMaxWidth().testTag("profile_support_text"))
        Button(onClick = { onSend(text); text = "" }, enabled = text.isNotBlank() && !sending, modifier = Modifier.fillMaxWidth()) { Text("Отправить") }
        Text("Жалоба на конкретную поездку — в «Заказы» → «Пожаловаться».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** About the app. */
@Composable
private fun InfoSection() {
    val context = LocalContext.current
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("KG Taxi • версия ${com.example.BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
        Text("Такси и межгород по Кыргызстану.", style = MaterialTheme.typography.bodySmall)
        ProfileMenuGroup {
            ProfileMenuItem(Icons.Default.Policy, "Политика конфиденциальности", null) { open("https://mramantur-site.vercel.app/privacy.html") }
            ProfileMenuItem(Icons.Default.Language, "Сайт и последняя версия", null) { open("https://mramantur-site.vercel.app/") }
        }
    }
}


/** Language, sounds and notifications. */
@Composable
private fun GeneralSettingsSection(state: KgTaxiUiState, viewModel: KgTaxiViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE) }
    var promoNotes by remember { mutableStateOf(prefs.getBoolean("promo_notifications", true)) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().testTag("general_settings")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Язык", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::cycleLanguage) { Text(if (com.example.i18n.Lang.kg) "Кыргызча" else "Русский") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Звуки в приложении", modifier = Modifier.weight(1f))
                Switch(checked = state.soundEnabled, onCheckedChange = { viewModel.toggleSound() })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Акции и скидки")
                    Text("Уведомления о промокодах", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = promoNotes, onCheckedChange = { promoNotes = it; prefs.edit().putBoolean("promo_notifications", it).apply() })
            }
            TextButton(onClick = {
                runCatching {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }) { Text("Настройки уведомлений телефона") }
        }
    }
}
