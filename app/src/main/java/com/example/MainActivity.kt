package com.example

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.firebase.*
import com.example.data.local.LocalTripEntity
import com.example.data.model.AppRole
import com.example.data.model.AppThemeMode
import com.example.data.model.KgGeoCatalog
import com.example.i18n.stringsFor
import com.example.ui.KgTaxiViewModel
import com.example.ui.KgTaxiViewModelFactory
import com.example.ui.components.DriverChatOverlay
import com.example.ui.components.OnboardingOverlay
import com.example.ui.components.PostTripReceiptDialog
import com.example.ui.components.SignInDialog
import com.example.ui.components.TopHeaderBar
import com.example.ui.screens.*
import com.example.ui.theme.KgTaxiTheme
import com.example.util.LocationAndVoiceHelper
import com.example.util.PdfReportExporter
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1 (MANDATORY): Configure Firebase App Check with launch intent BEFORE UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            KgTaxiAppRoot()
        }
    }
}

@Composable
fun KgTaxiAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: KgTaxiViewModel = viewModel(
        factory = remember(context) { KgTaxiViewModelFactory(context) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = remember(state.language) { stringsFor(state.language) }

    val authUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    var showProfile by remember { mutableStateOf(false) }
    var showAiChat by remember { mutableStateOf(false) }
    var showSignInDialog by remember { mutableStateOf(false) }
    var showDriverChat by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var pendingPdfSingleTrip by remember { mutableStateOf<LocalTripEntity?>(null) }

    // Silent auto-sign-in attempt on startup (single GetGoogleIdOption per request)
    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { /* AuthStateListener handles sync */ },
            onUnauthenticated = { /* Guest local-first mode remains seamlessly usable */ },
            scope = scope
        )
    }

    // Bind / unbind Firestore cloud sync cleanly to FirebaseAuth state
    LaunchedEffect(authUser?.uid) {
        val u = authUser
        if (u != null && u.uid.isNotBlank()) {
            viewModel.onUserSignedIn(u.uid, u.displayName, u.email, u.phoneNumber)
        } else {
            viewModel.onUserSignedOut()
        }
    }

    // Runtime Location & Notifications Permission Launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val locGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locGranted) {
            viewModel.locateMyGps()
        } else {
            viewModel.locateMyGps() // Uses curated Kyrgyzstan city fallback gracefully
        }
    }

    // Voice Search & Driving Voice Control Launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.handleVoiceCommand(spoken)
            }
        }
    }

    // Zero-Permission SAF PDF Export Launcher
    val pdfCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            val res = PdfReportExporter.writeTripsReportPdf(
                context = context,
                uri = uri,
                profile = state.profile,
                trips = state.trips,
                singleTrip = pendingPdfSingleTrip
            )
            pendingPdfSingleTrip = null
            if (res.isSuccess) {
                viewModel.updateProfile { it } // Keeps UI fresh
            }
        }
    }

    val triggerGoogleSignIn: () -> Unit = {
        isSigningIn = true
        authError = null
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isSigningIn = false
                authError = null
            },
            onAuthError = { msg ->
                isSigningIn = false
                authError = msg
            },
            scope = scope,
            onAuthCancelled = {
                isSigningIn = false
            }
        )
    }

    val requestLocationWithPermission: () -> Unit = {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionsLauncher.launch(perms.toTypedArray())
    }

    val launchVoice: () -> Unit = launchVoice@{
        if (!FeatureFlags.VOICE_COMMANDS) {
            viewModel.showComingSoon("Голосовые команды")
            return@launchVoice
        }
        try {
            val intent = LocationAndVoiceHelper.buildVoiceRecognizerIntent(
                langCode = state.language.code,
                prompt = strings.voiceSearch
            )
            voiceLauncher.launch(intent)
        } catch (_: Exception) {
            viewModel.handleVoiceCommand("Аэропорт Манас")
        }
    }

    val effectiveDarkTheme = when (state.themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    // Newer screens read the language from here (RU / KG).
    com.example.i18n.Lang.kg = state.language == com.example.i18n.AppLanguage.KG
    KgTaxiTheme(darkTheme = effectiveDarkTheme) {
        // Paint the theme background: otherwise the dark window shows through in light mode.
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // While the driver is on the line the screen is for driving only: no role tabs or settings.
                val driverOnLine = state.role == AppRole.DRIVER && (state.driverOnline || state.driverAcceptedOrder != null)
                if (!driverOnLine) {
                TopHeaderBar(
                    strings = strings,
                    role = state.role,
                    language = state.language,
                    darkTheme = effectiveDarkTheme,
                    soundEnabled = state.soundEnabled,
                    offlineMode = state.offlineMode,
                    isSignedIn = state.isSignedIn,
                    userName = state.profile.displayName,
                    isSigningIn = isSigningIn,
                    onSelectRole = viewModel::setRole,
                    onCycleLanguage = viewModel::cycleLanguage,
                    onToggleSound = viewModel::toggleSound,
                    onToggleTheme = viewModel::toggleDarkTheme,
                    onToggleOffline = viewModel::toggleOfflineMode,
                    onOpenProfile = { showProfile = true },
                    onClickSignIn = { showSignInDialog = true }
                )
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (state.role) {
                        AppRole.PASSENGER -> PassengerHomeScreen(
                            strings = strings,
                            state = state,
                            viewModel = viewModel,
                            onRequestGps = requestLocationWithPermission,
                            onRequestVoice = launchVoice,
                            onOpenAiChat = {
                                if (FeatureFlags.AI_ASSISTANT) showAiChat = true
                                else viewModel.showComingSoon("ИИ-помощник")
                            },
                            onOpenDriverChat = { showDriverChat = true },
                            onOpenMyRides = { showProfile = true }
                        )
                        AppRole.DRIVER -> DriverCabinScreen(
                            strings = strings,
                            state = state,
                            viewModel = viewModel,
                            onRequestVoiceCommand = launchVoice
                        )
                        AppRole.DISPATCHER -> DispatcherDashboardScreen(
                            strings = strings,
                            state = state,
                            onExportPdfReport = {
                                pendingPdfSingleTrip = null
                                pdfCreateLauncher.launch("KG_Taxi_Dispatcher_Report.pdf")
                            },
                            onSendPromoAlert = viewModel::triggerPromoNotification,
                            onSendTrafficAlert = viewModel::triggerTrafficAlertNotification,
                            onAssignDriver = viewModel::dispatchOrderToDriver,
                            onSimulateSos = {},
                            onResolveSos = viewModel::resolveSosAlert,
                            onSaveOwnerBankConfig = viewModel::saveOwnerBankConfig,
                            onTriggerOwnerPayout = viewModel::triggerOwnerManualPayout,
                            onRunTestOwnerOrderSplit = viewModel::runTestOwnerOrderSplit,
                            onOpenApplicationPhotos = viewModel::loadApplicationPhotos,
                            onCloseApplicationPhotos = viewModel::closeApplicationPhotos,
                            onApproveApplication = viewModel::approveApplication,
                            onRejectApplication = viewModel::rejectApplication,
                            onSetDriverBlocked = viewModel::setDriverBlocked,
                            onSaveAppConfig = viewModel::saveAppConfig,
                            onResolveTicket = viewModel::resolveSupportTicket,
                            onSetStatsDays = viewModel::setOwnerStatsDays,
                            onExportCsv = viewModel::exportOwnerCsv,
                            onLoadBalances = viewModel::loadOwnerDriverBalances,
                            onAddPayment = viewModel::addOwnerDriverPayment
                        )
                    }
                }
            }

            // Floating Status Toast Banner
            val bannerText = state.statusBanner
            if (!bannerText.isNullOrBlank()) {
                LaunchedEffect(bannerText) {
                    kotlinx.coroutines.delay(3500)
                    viewModel.clearBanner()
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 72.dp)
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = bannerText,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            // Full-Screen Profile & Wallet Overlay (immediately accessible even without login)
            if (showProfile) {
                ProfileWalletOverlay(
                    strings = strings,
                    state = state,
                    isSigningIn = isSigningIn,
                    authError = authError,
                    viewModel = viewModel,
                    onSignInWithGoogle = { showSignInDialog = true },
                    onSignOut = {
                        signOutGoogle(
                            credentialManager = credentialManager,
                            scope = scope,
                            onBeforeSignOut = { viewModel.stopCloudObservers() },
                            onSignOutComplete = { viewModel.onUserSignedOut() }
                        )
                    },
                    onExportAllPdf = {
                        pendingPdfSingleTrip = null
                        pdfCreateLauncher.launch("KG_Taxi_Trips_Report.pdf")
                    },
                    onExportSingleTripPdf = { trip ->
                        pendingPdfSingleTrip = trip
                        pdfCreateLauncher.launch("KG_Taxi_Receipt_${trip.id}.pdf")
                    },
                    onReopenOnboarding = viewModel::reopenOnboarding,
                    onDismiss = { showProfile = false }
                )
            }

            // Full-Screen In-Trip Driver Chat + AI Auto-Translation (RU / KG / EN)
            val activeRideForChat = state.activeRide
            if (showDriverChat && activeRideForChat != null) {
                DriverChatOverlay(
                    driverName = activeRideForChat.driverName,
                    driverCar = activeRideForChat.driverCar,
                    messages = state.driverChatMessages,
                    translateTargetLang = state.chatTranslateTargetLang,
                    isTranslating = state.chatTranslating,
                    onSelectTranslateLang = viewModel::setChatTranslateLang,
                    onTranslateAll = viewModel::translateAllDriverChat,
                    onSendMessage = viewModel::sendDriverChatMessage,
                    onDismiss = { showDriverChat = false }
                )
            }

            if (showSignInDialog) {
                SignInDialog(
                    onSignInWithGoogle = triggerGoogleSignIn,
                    onDismiss = { showSignInDialog = false }
                )
            }

            if (state.driverApplicationOpen) {
                com.example.ui.components.DriverApplicationDialog(
                    status = state.driverApplicationStatus,
                    sending = state.driverApplicationSending,
                    defaultName = state.profile.displayName,
                    defaultPhone = state.profile.phone,
                    defaultCity = state.selectedCity.name,
                    onSubmit = viewModel::submitDriverApplication,
                    onDismiss = viewModel::closeDriverApplication
                )
            }

            // Full-Screen AI Route Planner Overlay (Gemini 3.8 Flash via Firebase AI Logic)
            if (showAiChat) {
                AiRouteChatOverlay(
                    strings = strings,
                    messages = state.aiMessages,
                    isLoading = state.aiLoading,
                    onSendPrompt = viewModel::sendAiMessage,
                    onApplySuggestedPlace = { placeName ->
                        val found = KgGeoCatalog.cities.flatMap { it.landmarks }
                            .firstOrNull { it.name.equals(placeName, ignoreCase = true) }
                        if (found != null) {
                            viewModel.selectCity(KgGeoCatalog.cityByName(found.city))
                            viewModel.selectDestinationPlace(found)
                        }
                    },
                    onDismiss = { showAiChat = false }
                )
            }

            // Post-Trip Rating, Tips, Official Receipt PDF, ELQR, Favorite/Block Driver & Lost Item Flow Dialog
            val receipt = state.postTripReceipt
            if (receipt != null) {
                PostTripReceiptDialog(
                    strings = strings,
                    trip = receipt,
                    displayCurrency = state.displayCurrency,
                    companyName = state.profile.companyName,
                    companyInn = state.businessCompanyInn,
                    isFavoriteDriver = receipt.driverName in state.favoriteDrivers,
                    isBlockedDriver = receipt.driverName in state.blockedDrivers,
                    onToggleFavoriteDriver = viewModel::toggleFavoriteDriver,
                    onToggleBlockedDriver = viewModel::toggleBlockedDriver,
                    onExportPdf = { tripToExport ->
                        pendingPdfSingleTrip = tripToExport
                        pdfCreateLauncher.launch("KG_Taxi_Receipt_${tripToExport.id}.pdf")
                    },
                    onConfirmElqrPaid = viewModel::markElqrPaymentDone,
                    onSubmitReview = { rating, tip, lostNote ->
                        viewModel.submitPostTripReview(receipt, rating, tip, lostNote)
                    },
                    onDismiss = viewModel::dismissPostTripReceipt
                )
            }

            // First-Launch Interactive Onboarding Guide
            if (state.showOnboarding) {
                OnboardingOverlay(onComplete = viewModel::completeOnboarding)
            }

            // New version on the website (installs outside Google Play).
            state.updateAvailable?.let { upd ->
                AlertDialog(
                    onDismissRequest = viewModel::dismissUpdate,
                    title = { Text("Доступна новая версия ${upd.versionName}") },
                    text = { Text(upd.notes.ifBlank { "Исправления и новые функции." } + " Скачайте и установите поверх текущей версии — ваши данные сохранятся.") },
                    confirmButton = {
                        Button(onClick = {
                            runCatching {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(upd.url)))
                            }
                            viewModel.dismissUpdate()
                        }) { Text("Скачать") }
                    },
                    dismissButton = { TextButton(onClick = viewModel::dismissUpdate) { Text("Позже") } }
                )
            }

            // Registration: "Кто вы?" passenger / driver, documents, car and photo check.
            if (state.registrationOpen && state.isSignedIn) {
                com.example.ui.screens.RegistrationScreen(
                    registration = state.registration,
                    startAsDriver = state.registrationStartAsDriver,
                    sending = state.registrationSending,
                    defaultName = state.profile.displayName,
                    defaultPhone = state.profile.phone,
                    defaultCity = state.selectedCity.name,
                    onRegisterPassenger = viewModel::registerPassenger,
                    onRegisterDriver = viewModel::registerDriver,
                    onClose = viewModel::closeRegistration
                )
            }
        }
    }
}
