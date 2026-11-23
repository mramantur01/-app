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
    BackHandler { onDismiss() }
    val context = LocalContext.current

    var nameInput by remember(state.profile.displayName) { mutableStateOf(state.profile.displayName) }
    var homeInput by remember(state.profile.homeAddress) { mutableStateOf(state.profile.homeAddress) }
    var workInput by remember(state.profile.workAddress) { mutableStateOf(state.profile.workAddress) }
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
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_profile_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Close profile")
                    }
                    Text(strings.profile, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                }
                FilledTonalButton(
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
                                    text = if (state.isSignedIn) "Google Sync активен • ${state.profile.email}" else "Локальное сохранение (вход не обязателен)",
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
                                    Text("Войти через Google (облачная синхронизация)", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = onSignOut,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary),
                                modifier = Modifier.fillMaxWidth().testTag("profile_sign_out_button")
                            ) {
                                Text("Выйти из Google аккаунта")
                            }
                        }
                        if (!authError.isNullOrBlank()) {
                            Text(authError, color = UberAmber, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Editable Profile & Home/Work Addresses (Auto-saved locally + synced to Firestore)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Профиль и быстрые адреса", fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = homeInput, onValueChange = { homeInput = it }, label = { Text(strings.home) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = workInput, onValueChange = { workInput = it }, label = { Text(strings.work) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = {
                                viewModel.updateProfile {
                                    it.copy(
                                        displayName = nameInput.trim().ifBlank { it.displayName },
                                        homeAddress = homeInput.trim(),
                                        workAddress = workInput.trim()
                                    )
                                }
                            },
                            modifier = Modifier.align(Alignment.End).testTag("save_profile_addresses_button")
                        ) {
                            Text(strings.save)
                        }
                    }
                }

                // Wallet Balance, Top-Up Ledger, ELQR & Referral Program ("Пригласи друга")
                WalletAndReferralSection(
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

                // Kyrgyzstan Payment Methods (Наличные, MBank, O!Деньги, Элкарт, Visa/Mastercard)
                Text(strings.paymentMethods, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaymentMethod.entries.forEach { pm ->
                        val selected = state.profile.paymentMethod == pm.code
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateProfile { it.copy(paymentMethod = pm.code) } },
                            label = { Text("${pm.displayName} (${pm.badge})") },
                            modifier = Modifier.testTag("payment_${pm.code.lowercase()}")
                        )
                    }
                }

                // KG Plus Subscription Card (Uber One / Yandex Plus analog)
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
                                Text("299 сом / мес • аналог Uber One и Яндекс Плюс", style = MaterialTheme.typography.labelSmall, fontFamily = JetBrainsMono)
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

                // Theme (Auto / Light / Dark) & Map Style (Standard / Satellite / Night)
                ThemeAndMapAppearanceCard(
                    themeMode = state.themeMode,
                    mapLayerStyle = state.mapLayerStyle,
                    onSelectThemeMode = viewModel::setThemeMode,
                    onSelectMapLayerStyle = viewModel::setMapLayerStyle
                )

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

                // Lost & Found Support Tickets + Favorite & Blocked Drivers Management
                LostFoundAndFavoriteDriversSection(
                    tickets = state.lostItemTickets,
                    favoriteDrivers = state.favoriteDrivers,
                    blockedDrivers = state.blockedDrivers,
                    onToggleFavoriteDriver = viewModel::toggleFavoriteDriver,
                    onToggleBlockedDriver = viewModel::toggleBlockedDriver
                )

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
