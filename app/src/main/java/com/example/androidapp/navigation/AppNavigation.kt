package com.example.androidapp.navigation
import android.net.Uri
import com.example.androidapp.pages.guidance.EmergencyGuidancePage
import com.example.androidapp.pages.guidance.AdminEmergencyGuidancePage
import com.example.androidapp.pages.guidance.EmergencyGuidanceViewModel
import com.example.androidapp.pages.emergency.EmergencySendScreen
import com.example.androidapp.pages.emergency.EmergencyInboxScreen
import com.example.androidapp.pages.emergency.EmergencySetupScreen
import com.example.androidapp.pages.admin.AdminAccountsPage
import com.example.androidapp.pages.admin.AdminAccountsViewModel
import com.example.androidapp.pages.admin.AdminRegionalResourcesPage
import com.example.androidapp.pages.admin.AdminRegionalResourcesViewModel
import com.example.androidapp.pages.admin.AdminDashboardPage
import com.example.androidapp.pages.admin.AdminDashboardViewModel
import com.example.androidapp.pages.resources.ResourcesScreen
import com.example.androidapp.pages.documents.DocumentSharingPage
import com.example.androidapp.pages.voicesafety.VoiceSafetySettingsScreen
import com.example.androidapp.security.voice.VoiceSafetyViewModel
import androidx.compose.ui.Modifier
import com.example.androidapp.security.PrivacyInactivityTimer
import com.example.androidapp.security.trackPrivacyInteraction
import com.example.androidapp.security.LocalPrivacyExternalInteraction
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.example.androidapp.security.LocalPrivacyFocusGuard
import com.example.androidapp.security.PrivacyFocusGuard
import androidx.activity.ComponentActivity
import android.widget.Toast
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.androidapp.security.PrivacyLevel
import com.example.androidapp.pages.weather.WeatherContactSupportScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.example.androidapp.data.AuthRepository
import com.example.androidapp.data.UserRepository
import com.example.androidapp.pages.accountsettings.AccountSettingsPage
import com.example.androidapp.pages.accountsettings.AccountSettingsViewModel
import com.example.androidapp.pages.accountsetup.AccountCreatedStep
import com.example.androidapp.pages.accountsetup.AccountLandingStep
import com.example.androidapp.pages.accountsetup.AccountSetupViewModel
import com.example.androidapp.pages.accountsetup.AccountTypeStep
import com.example.androidapp.pages.accountsetup.DocumentsStep
import com.example.androidapp.pages.accountsetup.PersonalInformationStep
import com.example.androidapp.pages.accountsetup.TrustedContactsStep
import com.example.androidapp.pages.dashboardpage.DashboardPage
import com.example.androidapp.pages.incominglocationsharing.IncomingLocationSharingScreen
import com.example.androidapp.pages.locationsharing.LocationSharingScreen
import com.example.androidapp.pages.login.LoginScreen
import com.example.androidapp.pages.login.LoginViewModel
import com.example.androidapp.pages.contacts.ContactsPage
import com.example.androidapp.pages.contacts.ContactsViewModel
import com.example.androidapp.pages.quickexit.ExitButtonViewModel
import com.example.androidapp.pages.weather.PasscodeScreenWithViewModel
import com.example.androidapp.pages.weather.PasscodeSetupScreen
import com.example.androidapp.pages.weather.PasscodeSetupViewModel
import com.example.androidapp.pages.weather.WeatherAppViewModel
import com.example.androidapp.repository.LocalPasswordRepository
import com.example.androidapp.repository.SettingsRepository
import com.example.androidapp.security.PrivacyRoutePolicy
import com.example.androidapp.security.PrivacyShieldManager
import com.example.androidapp.pages.weather.WeatherScreen
import kotlinx.coroutines.launch
import com.example.androidapp.pages.notes.NotesPage
import com.example.androidapp.pages.notes.NotesViewModel
import com.example.androidapp.pages.supportdashboard.SupportDashboardPage
import com.example.androidapp.pages.supportlinking.SupportLinkingPage
import com.example.androidapp.pages.supportlinking.SupportLinkingViewModel
import com.example.androidapp.pages.supportlinking.IncomingSupportRequestsPage
import com.example.androidapp.pages.supportlinking.IncomingSupportRequestsViewModel
import com.example.androidapp.pages.documents.DocumentsPage
import com.example.androidapp.pages.documents.DocumentsViewModel
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.zIndex
import com.example.androidapp.pages.quickexit.QuickExitButton
import androidx.compose.ui.unit.dp
import com.example.androidapp.pages.messagingpage.MessagingRoot
import com.example.androidapp.data.StaffMessagingLogic
import com.example.androidapp.pages.staffmessaging.StaffInboxPage
import com.example.androidapp.pages.staffmessaging.StaffInboxViewModel
import com.example.androidapp.pages.staffmessaging.StaffThreadPage
import com.example.androidapp.pages.staffmessaging.StaffThreadViewModel
@Composable
fun AppNavigation(inactivityTimer: PrivacyInactivityTimer? = null) {

    val mainNavController =
        rememberNavController()

    val context =
        LocalContext.current

    val currentBackStackEntry by
    mainNavController
        .currentBackStackEntryAsState()

    val currentRoute =
        currentBackStackEntry
            ?.destination
            ?.route

    val privacyLevel =
        PrivacyRoutePolicy.levelFor(
            currentRoute
        )

    val activity = context as ComponentActivity

    // Activity scope keeps a voice session alive across navigation destinations.
    val voiceSafety: VoiceSafetyViewModel = viewModel(viewModelStoreOwner = activity)

    DisposableEffect(activity, voiceSafety) {
        fun syncVoiceForeground() {
            voiceSafety.setForeground(
                activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            )
        }
        val observer = LifecycleEventObserver { _, _ -> syncVoiceForeground() }
        activity.lifecycle.addObserver(observer)
        syncVoiceForeground()
        onDispose {
            activity.lifecycle.removeObserver(observer)
            voiceSafety.setForeground(false)
        }
    }


    DisposableEffect(
        activity,
        currentBackStackEntry,
        privacyLevel
    ) {
        val entry = currentBackStackEntry

        fun updateScreenProtection() {
            val publicScreenIsReady =
                privacyLevel == PrivacyLevel.PUBLIC &&
                        activity.lifecycle.currentState.isAtLeast(
                            Lifecycle.State.RESUMED
                        ) &&
                        entry?.lifecycle?.currentState
                            ?.isAtLeast(Lifecycle.State.RESUMED) == true

            if (publicScreenIsReady) {
                PrivacyShieldManager.disable(activity)
            } else {
                PrivacyShieldManager.enable(activity)
            }
        }

        val observer = LifecycleEventObserver { _, _ ->
            updateScreenProtection()
        }

        activity.lifecycle.addObserver(observer)
        entry?.lifecycle?.addObserver(observer)

        updateScreenProtection()

        onDispose {
            activity.lifecycle.removeObserver(observer)
            entry?.lifecycle?.removeObserver(observer)
        }
    }


    // ---------------------------------------------------------
    // CENTRAL PRIVACY EXIT
    // ---------------------------------------------------------

    var forceWeatherCover by rememberSaveable {
        mutableStateOf(false)
    }

    val performQuickExit: () -> Unit = {
        inactivityTimer?.setMonitoring(false)

        // Keep the cover visible even when Disguise is disabled.
        forceWeatherCover = true

        mainNavController.navigate("weather") {
            popUpTo(mainNavController.graph.id) {
                inclusive = true
            }

            launchSingleTop = true
        }
    }

    // ---------------------------------------------------------
    // ADMIN BACK NAVIGATION
    // ---------------------------------------------------------

    // Admin tool screens always return to the admin hub. Pop back to it
    // when it is on the stack; otherwise open it in place of the tool screen.
    val returnToAdminDashboard: (fromRoute: String) -> Unit = { fromRoute ->
        val poppedToDashboard = mainNavController.popBackStack(
            route = "adminDashboard",
            inclusive = false
        )

        if (!poppedToDashboard) {
            mainNavController.navigate("adminDashboard") {
                popUpTo(fromRoute) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    // ---------------------------------------------------------
    // BACKGROUND PRIVACY PROTECTION
    // ---------------------------------------------------------

    var documentPickerActive by rememberSaveable {
        mutableStateOf(false)
    }

    var permissionRequestActive by rememberSaveable {
        mutableStateOf(false)
    }

    val privacySettingsRepository = remember(activity) {
        SettingsRepository(activity.applicationContext)
    }

    val shouldExitForFocusLoss by rememberUpdatedState(
        newValue = {
            val route = mainNavController.currentDestination?.route
            privacySettingsRepository.isExtremePrivacyEnabled() &&
                    route != null &&
                    PrivacyRoutePolicy.levelFor(route) != PrivacyLevel.PUBLIC &&
                    !documentPickerActive && !permissionRequestActive &&
                    !activity.isChangingConfigurations && !activity.isFinishing
        }
    )

    val latestQuickExit by rememberUpdatedState(performQuickExit)

    val focusGuard = remember(activity) {
        PrivacyFocusGuard(
            shouldExit = { shouldExitForFocusLoss() },
            onExit = { latestQuickExit() }
        )
    }

    DisposableEffect(focusGuard, activity) {
        val release = focusGuard.registerWindow(activity.window.decorView)
        onDispose {
            release()
            focusGuard.dispose()
        }
    }

    // Monitor only sensitive foreground routes. Each resumption starts a fresh period.
    val refreshInactivityTimer: () -> Unit = {
        val route = mainNavController.currentDestination?.route
        val enabled = route != null &&
                PrivacyRoutePolicy.levelFor(route) != PrivacyLevel.PUBLIC &&
                activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                !documentPickerActive && !permissionRequestActive
        inactivityTimer?.setMonitoring(enabled)
    }

    val onDocumentPickerActiveChange: (Boolean) -> Unit = { active ->
        if (!active) focusGuard.awaitExpectedFocusReturn()
        documentPickerActive = active
        refreshInactivityTimer()
    }

    val onPermissionRequestActiveChange: (Boolean) -> Unit = { active ->
        if (!active) focusGuard.awaitExpectedFocusReturn()
        permissionRequestActive = active
        refreshInactivityTimer()
    }

    val handleInactivityTimeout by rememberUpdatedState(
        newValue = {
            val route = mainNavController.currentDestination?.route
            if (route != null &&
                PrivacyRoutePolicy.levelFor(route) != PrivacyLevel.PUBLIC &&
                activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                !documentPickerActive && !permissionRequestActive
            ) {
                performQuickExit()
            }
        }
    )

    DisposableEffect(inactivityTimer) {
        inactivityTimer?.onTimeout = { handleInactivityTimeout() }
        onDispose {
            inactivityTimer?.setMonitoring(false)
            inactivityTimer?.onTimeout = null
        }
    }

    DisposableEffect(
        activity,
        inactivityTimer,
        currentRoute,
        documentPickerActive,
        permissionRequestActive
    ) {
        val observer = LifecycleEventObserver { _, _ ->
            refreshInactivityTimer()
        }
        activity.lifecycle.addObserver(observer)
        refreshInactivityTimer()
        onDispose {
            activity.lifecycle.removeObserver(observer)
            inactivityTimer?.setMonitoring(false)
        }
    }

    val handleBackgroundExit by rememberUpdatedState(
        newValue = {
            val route =
                mainNavController.currentDestination?.route

            val level =
                PrivacyRoutePolicy.levelFor(route)

            if (
                route != null &&
                level != PrivacyLevel.PUBLIC &&
                !documentPickerActive &&
                !permissionRequestActive
            ) {
                performQuickExit()
            }
        }
    )

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (
                event == Lifecycle.Event.ON_STOP &&
                !activity.isChangingConfigurations &&
                !activity.isFinishing
            ) {
                // Retain capture protection while backgrounded.
                PrivacyShieldManager.enable(activity)

                handleBackgroundExit()
            }
        }

        activity.lifecycle.addObserver(observer)

        onDispose {
            activity.lifecycle.removeObserver(observer)
        }
    }

    CompositionLocalProvider(
        LocalPrivacyFocusGuard provides focusGuard,
        LocalPrivacyExternalInteraction provides onDocumentPickerActiveChange
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            NavHost(
                modifier = Modifier
                    .fillMaxSize()
                    .trackPrivacyInteraction(),
                navController =
                    mainNavController,

                startDestination = "weather",
                // Do not fade sensitive content over the cover during a privacy exit.
                enterTransition = {
                    if (targetState.destination.route == "weather") EnterTransition.None
                    else fadeIn()
                },
                exitTransition = {
                    if (targetState.destination.route == "weather") ExitTransition.None
                    else fadeOut()
                },
                popEnterTransition = {
                    if (targetState.destination.route == "weather") EnterTransition.None
                    else fadeIn()
                },
                popExitTransition = {
                    if (targetState.destination.route == "weather") ExitTransition.None
                    else fadeOut()
                }
            ) {        // ---------------------------------------------------------
// WEATHER DISGUISE
// ---------------------------------------------------------

                composable("weather") {

                    val appContext =
                        LocalContext.current.applicationContext

                    val settingsRepo =
                        remember {
                            SettingsRepository(
                                appContext
                            )
                        }

                    val disguiseEnabled =
                        settingsRepo
                            .getSettings()
                            .disguiseEnabled


                    // Extreme Privacy keeps the cover visible on a fresh launch,
                    // even when the normal Disguise setting is disabled.
                    val extremePrivacyEnabled =
                        settingsRepo.isExtremePrivacyEnabled()

                    val showWeatherCover =
                        disguiseEnabled || forceWeatherCover || extremePrivacyEnabled

                    LaunchedEffect(showWeatherCover) {

                        if (!showWeatherCover) {
                            val hasPasscode =
                                LocalPasswordRepository(
                                    appContext
                                ).hasPassword()

                            val signedIn =
                                AuthRepository()
                                    .currentUser != null


                            val destination =
                                if (
                                    hasPasscode &&
                                    signedIn
                                ) {

                                    "roleHome"

                                } else if (
                                    signedIn
                                ) {

                                    "createPasscode"

                                } else {

                                    "accountSetup"
                                }


                            mainNavController
                                .navigate(
                                    destination
                                ) {

                                    popUpTo(
                                        "weather"
                                    ) {
                                        inclusive =
                                            true
                                    }
                                }
                        }
                    }


                    if (showWeatherCover) {

                    WeatherScreen(
                        onVoiceDuress = { voiceSafety.startDuress() },
                        onVoiceCancel = { voiceSafety.cancelDuress() },
                        onVoiceAccess = {
                            if (mainNavController.currentDestination?.route == "weather") {
                                mainNavController.navigate("passcode") {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onContactUsClick = {

                                mainNavController
                                    .navigate(
                                        "weatherContact"
                                    )
                            }
                        )
                    }
                }


// ---------------------------------------------------------
// WEATHER CONTACT SUPPORT
// ---------------------------------------------------------

        composable(
            "weatherContact"
        ) { contactEntry ->
            val weatherEntry = remember(contactEntry, mainNavController) {
                mainNavController.getBackStackEntry("weather")
            }
            val weatherModel: com.example.androidapp.pages.weather.WeatherDataViewModel =
                viewModel(viewModelStoreOwner = weatherEntry)

            WeatherContactSupportScreen(
                weatherModel = weatherModel,
                onBackClick = {

                            mainNavController
                                .popBackStack()
                        },

                        onSecretAccess = {

                            mainNavController
                                .navigate(
                                    "passcode"
                                )
                        }
                    )
                }


                // ---------------------------------------------------------
                // LOCATION SHARING
                // ---------------------------------------------------------

                composable("locationSharing") {
                    LocationSharingScreen(
                        onBackClick = {
                            if (!mainNavController.popBackStack()) {
                                mainNavController.navigate("roleHome") {
                                    popUpTo("locationSharing") {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        },
                        onPermissionRequestActiveChange =
                            onPermissionRequestActiveChange
                    )
                }

                composable("incomingLocationSharing") {
                    IncomingLocationSharingScreen(
                        onBackClick = {
                            if (!mainNavController.popBackStack()) {
                                mainNavController.navigate("roleHome") {
                                    popUpTo("incomingLocationSharing") {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }
                // ---------------------------------------------------------
                // PASSCODE
                // ---------------------------------------------------------

                composable("passcode") {

                    val appContext =
                        LocalContext.current.applicationContext

                    val passcodeViewModel: WeatherAppViewModel =
                        viewModel(
                            factory = viewModelFactory {
                                initializer {
                                    WeatherAppViewModel(
                                        LocalPasswordRepository(
                                            appContext
                                        )
                                    )
                                }
                            }
                        )

                    val isUnlocked by
                    passcodeViewModel
                        .isUnlocked
                        .collectAsState()

                    val needsPasscode by
                    passcodeViewModel
                        .needsPasscode
                        .collectAsState()

                    LaunchedEffect(
                        isUnlocked,
                        needsPasscode
                    ) {

                        val signedIn =
                            AuthRepository().currentUser != null

                        val destination =
                            when {

                                isUnlocked -> {
                                    if (signedIn) {
                                        "roleHome"
                                    } else {
                                        "accountSetup"
                                    }
                                }

                                needsPasscode == false -> {
                                    if (signedIn) {
                                        "createPasscode"
                                    } else {
                                        "accountSetup"
                                    }
                                }

                                else -> null
                            }

                        if (destination != null) {

                            mainNavController.navigate(destination) {

                                popUpTo("passcode") {
                                    inclusive = true
                                }
                            }
                        }
                    }

                    if (needsPasscode == true) {

                        PasscodeScreenWithViewModel(
                            viewModel = passcodeViewModel
                        )
                    }
                }


                // ---------------------------------------------------------
                // CREATE PASSCODE
                // ---------------------------------------------------------

                composable("createPasscode") {

                    val appContext =
                        LocalContext.current.applicationContext

                    val setupViewModel: PasscodeSetupViewModel =
                        viewModel(
                            factory = viewModelFactory {
                                initializer {
                                    PasscodeSetupViewModel(
                                        LocalPasswordRepository(
                                            appContext
                                        )
                                    )
                                }
                            }
                        )

                    val isDone by
                    setupViewModel
                        .isDone
                        .collectAsState()

                    LaunchedEffect(isDone) {

                        if (isDone) {

                            mainNavController.navigate(
                                "roleHome"
                            ) {

                                popUpTo(
                                    "createPasscode"
                                ) {
                                    inclusive = true
                                }
                            }
                        }
                    }

                    PasscodeSetupScreen(
                        viewModel = setupViewModel
                    )
                }


                // ---------------------------------------------------------
                // POST AUTH
                // ---------------------------------------------------------

                composable("roleHome") {

                    var profile by remember {
                        mutableStateOf<com.example.androidapp.data.UserProfile?>(null)
                    }

                    var error by remember {
                        mutableStateOf<String?>(null)
                    }

                    LaunchedEffect(Unit) {
                        val uid = AuthRepository().currentUser?.uid

                        if (uid == null) {
                            mainNavController.navigate("login") {
                                popUpTo("roleHome") { inclusive = true }
                            }
                            return@LaunchedEffect
                        }

                        UserRepository().getProfile(uid) { result ->
                            result.onSuccess { loadedProfile ->
                                if (loadedProfile == null) {
                                    error = "Account profile not found."
                                } else if (loadedProfile.accountStatus != "active") {
                                    error = "This account is not active."
                                } else {
                                    profile = loadedProfile
                                }
                            }.onFailure {
                                error = "Could not load your account."
                            }
                        }
                    }

                    LaunchedEffect(profile) {
                        val loadedProfile = profile ?: return@LaunchedEffect

                        val destination = when (loadedProfile.role) {
                            "admin" -> "adminDashboard"
                            "support" -> "supportDashboard"
                            "user" -> "dashboard"
                            else -> {
                                error = "Unsupported account role."
                                return@LaunchedEffect
                            }
                        }

                        mainNavController.navigate(destination) {
                            popUpTo("roleHome") { inclusive = true }
                            launchSingleTop = true
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (error != null) {
                            Text(error!!)
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                }

                composable("postAuth") {

                    val appContext =
                        LocalContext.current.applicationContext

                    LaunchedEffect(Unit) {

                        val hasPasscode =
                            LocalPasswordRepository(
                                appContext
                            ).hasPassword()

                        val destination =
                            if (hasPasscode) {
                                "roleHome"
                            } else {
                                "createPasscode"
                            }

                        mainNavController.navigate(destination) {

                            popUpTo("postAuth") {
                                inclusive = true
                            }
                        }
                    }
                }


                // ---------------------------------------------------------
                // DASHBOARD
                // ---------------------------------------------------------

                composable("supportLinking") {
                    val supportLinkingViewModel: SupportLinkingViewModel = viewModel()

                    SupportLinkingPage(
                        viewModel = supportLinkingViewModel,
                        onBackClick = {
                            if (!mainNavController.popBackStack()) {
                                mainNavController.navigate("supportDashboard") {
                                    popUpTo("supportLinking") {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        },
                        onQuickExit = {
                            performQuickExit()
                        }
                    )
                }

                composable("incomingSupportRequests") {

                    val requestsViewModel: IncomingSupportRequestsViewModel =
                        viewModel()

                    IncomingSupportRequestsPage(
                        viewModel = requestsViewModel,
                        onBackClick = {
                            mainNavController.popBackStack()
                        },
                        onQuickExit = {
                            performQuickExit()
                        }
                    )
                }

                composable("adminAccounts") {
                    val accountsViewModel: AdminAccountsViewModel = viewModel()

                    AdminAccountsPage(
                        viewModel = accountsViewModel,
                        onBack = {
                            returnToAdminDashboard("adminAccounts")
                        }
                    )
                }

                composable("adminRegionalResources") {
                    val resourcesViewModel: AdminRegionalResourcesViewModel = viewModel()

                    AdminRegionalResourcesPage(
                        viewModel = resourcesViewModel,
                        onBack = {
                            returnToAdminDashboard("adminRegionalResources")
                        }
                    )
                }

                composable("emergencyGuidance") {
                    val guidanceViewModel: EmergencyGuidanceViewModel = viewModel()

                    EmergencyGuidancePage(
                        viewModel = guidanceViewModel,
                        onBack = {
                            if (!mainNavController.popBackStack()) {
                                mainNavController.navigate("roleHome") {
                                    popUpTo("emergencyGuidance") {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }


                composable("adminEmergencyGuidance") {
                    val guidanceViewModel: EmergencyGuidanceViewModel = viewModel()

                    AdminEmergencyGuidancePage(
                        viewModel = guidanceViewModel,
                        onBack = {
                            returnToAdminDashboard("adminEmergencyGuidance")
                        }
                    )
                }

                composable("adminDashboard") {
                    val adminViewModel: AdminDashboardViewModel = viewModel()
                    val staffBadge: StaffInboxViewModel = viewModel()
                    val adminScope = rememberCoroutineScope()
                    val appContext = LocalContext.current.applicationContext

                    var signingOut by remember {
                        mutableStateOf(false)
                    }

                    // Live unread badge while the dashboard is on screen.
                    DisposableEffect(staffBadge) {
                        staffBadge.start(StaffMessagingLogic.ADMIN)
                        onDispose { staffBadge.stop() }
                    }

                    AdminDashboardPage(
                        state = adminViewModel.state,
                        emergencyState = adminViewModel.emergencyState,
                        onRetryEmergency = {
                            if (!signingOut) {
                                adminViewModel.refreshEmergency()
                            }
                        },
                        staffUnread = StaffMessagingLogic.totalUnread(
                            staffBadge.state.threads,
                            StaffMessagingLogic.ADMIN
                        ),
                        onStaffMessages = {
                            mainNavController.navigate("adminStaffMessages") {
                                launchSingleTop = true
                            }
                        },
                        onManageGuidance = {
                            mainNavController.navigate("adminEmergencyGuidance") {
                                launchSingleTop = true
                            }
                        },
                        onManageResources = {
                            mainNavController.navigate("adminRegionalResources") {
                                launchSingleTop = true
                            }
                        },
                        onManageAccounts = {
                            mainNavController.navigate("adminAccounts") {
                                launchSingleTop = true
                            }
                        },
                        onRefresh = {
                            if (!signingOut) {
                                adminViewModel.refresh()
                            }
                        },
                        onLogout = {
                            if (!signingOut) {
                                signingOut = true

                                adminScope.launch {
                                    try {
                                        // Match the existing account-settings sign-out.
                                        LocalPasswordRepository(appContext).clearPassword()

                                        appContext
                                            .getSharedPreferences(
                                                "app_settings",
                                                Context.MODE_PRIVATE
                                            )
                                            .edit()
                                            .clear()
                                            .apply()
                                    } finally {
                                        AuthRepository().logout()

                                        // Return to WeatherNow and clear navigation history.
                                        performQuickExit()
                                    }
                                }
                            }
                        }
                    )
                }

                composable("supportDashboard") {

                    var userName by remember {
                        mutableStateOf("Support User")
                    }

                    val staffBadge: StaffInboxViewModel = viewModel()

                    DisposableEffect(staffBadge) {
                        staffBadge.start(StaffMessagingLogic.SUPPORT)
                        onDispose { staffBadge.stop() }
                    }

                    LaunchedEffect(Unit) {
                        val uid = AuthRepository().currentUser?.uid
                            ?: return@LaunchedEffect

                        UserRepository().getProfile(uid) { result ->
                            result.onSuccess { profile ->
                                if (profile != null) {
                                    userName = profile.displayName
                                        .ifBlank { "Support User" }
                                }
                            }
                        }
                    }


                    SupportDashboardPage(
                        userName = userName,
                        onGuidanceClick = {
                            mainNavController.navigate("emergencyGuidance") {
                                launchSingleTop = true
                            }
                        },

                        onSettingsClick = {
                            mainNavController.navigate("accountSettings")
                        },

                        onQuickExit = {
                            performQuickExit()
                        },

                        onLinkedUsersClick = {
                            mainNavController.navigate("supportLinking")
                        },

                        onMessagesClick = {
                            mainNavController.navigate("messages") {
                                launchSingleTop = true
                            }
                        },

                        onDocumentsClick = {
                            mainNavController.navigate("documents") {
                                launchSingleTop = true
                            }
                        },

                        staffUnread = StaffMessagingLogic.totalUnread(
                            staffBadge.state.threads,
                            StaffMessagingLogic.SUPPORT
                        ),

                        onStaffMessagesClick = {
                            mainNavController.navigate("supportStaffThread") {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                // ---------------------------------------------------------
                // STAFF MESSAGING (admin <-> support)
                // Listeners start when a screen appears and stop when it leaves.
                // ---------------------------------------------------------

                composable("adminStaffMessages") {
                    val inbox: StaffInboxViewModel = viewModel()

                    DisposableEffect(inbox) {
                        inbox.start(StaffMessagingLogic.ADMIN)
                        onDispose { inbox.stop() }
                    }

                    StaffInboxPage(
                        state = inbox.state,
                        onBack = { returnToAdminDashboard("adminStaffMessages") },
                        onQueryChange = inbox::onQueryChange,
                        onOpenThread = { supportUid, supportName ->
                            inbox.closePicker()
                            // Header name until the thread exists and carries it.
                            mainNavController.currentBackStackEntry
                                ?.savedStateHandle
                                ?.set("staffSupportName", supportName)
                            mainNavController.navigate(
                                "adminStaffThread/${Uri.encode(supportUid)}"
                            ) {
                                launchSingleTop = true
                            }
                        },
                        onNewMessage = inbox::openPicker,
                        onClosePicker = inbox::closePicker,
                        onRetry = {
                            inbox.stop()
                            inbox.start(StaffMessagingLogic.ADMIN)
                        }
                    )
                }

                composable("adminStaffThread/{supportUid}") { entry ->
                    val supportUid = entry.arguments?.getString("supportUid").orEmpty()
                    val thread: StaffThreadViewModel = viewModel()

                    val passedName = remember(entry) {
                        mainNavController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.get<String>("staffSupportName")
                    }

                    DisposableEffect(thread, supportUid) {
                        thread.start(StaffMessagingLogic.ADMIN, supportUid)
                        onDispose { thread.stop() }
                    }

                    StaffThreadPage(
                        state = thread.state,
                        supportName = thread.state.thread?.supportDisplayName
                            ?.takeIf { it.isNotBlank() }
                            ?: passedName.orEmpty(),
                        onBack = {
                            val backToInbox = mainNavController.popBackStack(
                                route = "adminStaffMessages",
                                inclusive = false
                            )
                            if (!backToInbox) {
                                returnToAdminDashboard("adminStaffThread/{supportUid}")
                            }
                        },
                        onSend = thread::send,
                        onRetry = thread::retry,
                        onDiscard = thread::discard,
                        onRetryLoad = {
                            thread.stop()
                            thread.start(StaffMessagingLogic.ADMIN, supportUid)
                        }
                    )
                }

                composable("supportStaffThread") {
                    val thread: StaffThreadViewModel = viewModel()

                    DisposableEffect(thread) {
                        thread.start(StaffMessagingLogic.SUPPORT, null)
                        onDispose { thread.stop() }
                    }

                    StaffThreadPage(
                        state = thread.state,
                        supportName = "",
                        onBack = {
                            if (!mainNavController.popBackStack()) {
                                mainNavController.navigate("supportDashboard") {
                                    popUpTo("supportStaffThread") {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        },
                        onSend = thread::send,
                        onRetry = thread::retry,
                        onDiscard = thread::discard,
                        onRetryLoad = {
                            thread.stop()
                            thread.start(StaffMessagingLogic.SUPPORT, null)
                        }
                    )
                }

                composable("dashboard") {
                    var userName by remember {
                        mutableStateOf("")
                    }

                    LaunchedEffect(Unit) {
                        val uid = AuthRepository()
                            .currentUser
                            ?.uid
                            ?: return@LaunchedEffect

                        UserRepository().getProfile(uid) { result ->
                            result.onSuccess { profile ->
                                if (
                                    profile != null &&
                                    profile.displayName.isNotBlank()
                                ) {
                                    userName = profile.displayName
                                }
                            }
                        }
                    }

                    val exitButtonViewModel: ExitButtonViewModel = viewModel()

                    DashboardPage(
                        userName = userName,
                        exitButtonViewModel = exitButtonViewModel,

                        onSettingsClick = {
                            mainNavController.navigate("accountSettings")
                        },

                        onNotesClick = {
                            mainNavController.navigate("notes")
                        },

                        onMessagesClick = { mainNavController.navigate("messages") },

                        onContactsClick = {
                            mainNavController.navigate("contacts")
                        },

                        onResourcesClick = {
                            mainNavController.navigate("resources") {
                                launchSingleTop = true
                            }
                        },

                        onDocumentsClick = {
                            mainNavController.navigate("documents")
                        },

                        onLocationSharingClick = {
                            mainNavController.navigate("locationSharing")
                        },

                        onIncomingLocationSharingClick = {
                            mainNavController.navigate("incomingLocationSharing")
                        },

                        onEmergencyClick = {
                            mainNavController.navigate("emergencySetup") {
                                launchSingleTop = true
                            }
                        },

                        onQuickExit = {
                            performQuickExit()
                        }
                    )
                }

                composable("emergencySetup") {
                    EmergencySetupScreen(
                        onBack = {
                            mainNavController.popBackStack()
                        },
                        onOpenInbox = {
                            mainNavController.navigate("emergencyInbox") {
                                launchSingleTop = true
                            }
                        },
                        onOpenSend = {
                            mainNavController.navigate("emergencySend") {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable("emergencySend") {
                    EmergencySendScreen(
                        onBack = {
                            mainNavController.popBackStack()
                        }
                    )
                }

                composable("emergencyInbox") {
                    EmergencyInboxScreen(
                        onBack = {
                            mainNavController.popBackStack()
                        }
                    )
                }
// RESOURCES PAGE
                composable("resources") {
                    ResourcesScreen(
                        onBack = {
                            mainNavController.popBackStack()
                        },
                        onQuickExit = {
                            performQuickExit()
                        },
                        onGuidanceClick = {
                            mainNavController.navigate("emergencyGuidance") {
                                launchSingleTop = true
                            }
                        }
                    )
                }

// NOTES PAGE
                composable("notes") {
                    val notesViewModel: NotesViewModel = viewModel()

                    NotesPage(
                        viewModel = notesViewModel,
                        onBackClick = {
                            mainNavController.popBackStack()
                        }
                    )
                }
                // MESSAGES PAGE
                composable("messages") {
                    MessagingRoot(
                        onBackClick = {
                            mainNavController.popBackStack()
                        },
                        onQuickExit = {
                            performQuickExit()
                        }
                    )
                }
                // DOCUMENTS PAGE
                composable("documents") {
                    DocumentSharingPage(
                        onBackClick = {
                            mainNavController.popBackStack()
                        },
                        onQuickExit = {
                            performQuickExit()
                        },
                        onEarlierUploadsClick = {
                            mainNavController.navigate("earlierDocuments")
                        }
                    )
                }

                composable("earlierDocuments") {
                    val documentsViewModel: DocumentsViewModel = viewModel()

                    DocumentsPage(
                        viewModel = documentsViewModel,
                        onBackClick = {
                            mainNavController.popBackStack()
                        }
                    )
                }

                // CONTACTS PAGE

                composable("contacts") {
                    val contactsViewModel: ContactsViewModel = viewModel()

                    ContactsPage(
                        viewModel = contactsViewModel,

                        onBackButtonClick = {
                            mainNavController.popBackStack()
                        },

                        onSupportRequestsClick = {
                            mainNavController.navigate(
                                "incomingSupportRequests"
                            ) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                // LOGIN

                composable("login") {

                    val loginViewModel:
                            LoginViewModel =
                        viewModel()

                    LoginScreen(
                        viewModel =
                            loginViewModel,

                        onBackClick = {
                            mainNavController
                                .popBackStack()
                        },

                        onLoginSuccess = {

                            mainNavController.navigate(
                                "postAuth"
                            ) {

                                popUpTo("accountSetup") {
                                    inclusive = true
                                }
                            }
                        }
                    )
                }


                // ---------------------------------------------------------
                // ACCOUNT SETTINGS
                // ---------------------------------------------------------

            composable("voiceSafetySettings") {
                VoiceSafetySettingsScreen(
                    sessionStatus = voiceSafety.status,
                    sessionEventId = voiceSafety.eventId,
                    sessionBusy = voiceSafety.isBusy,
                    duressEnabled = voiceSafety.duressEnabled,
                    onDuressEnabledChange = { voiceSafety.updateDuressEnabled(it) },
                    onCancelSession = { voiceSafety.cancelDuress() },
                    onBack = {
                        mainNavController.popBackStack()
                    }
                )
            }

            composable("accountSettings") {

                    val scope =
                        rememberCoroutineScope()

                    val appContext =
                        LocalContext.current.applicationContext

                    val accountSettingsViewModel:
                            AccountSettingsViewModel =
                        viewModel(
                            factory =
                                viewModelFactory {

                                    initializer {

                                        AccountSettingsViewModel(
                                            settingsRepository =
                                                SettingsRepository(
                                                    appContext
                                                ),

                                            passwordRepository =
                                                LocalPasswordRepository(
                                                    appContext
                                                ),

                                            authRepository =
                                                AuthRepository(),

                                            userRepository =
                                                UserRepository()
                                        )
                                    }
                                }
                        )

                    val exitButtonViewModel:
                            ExitButtonViewModel =
                        viewModel()

                AccountSettingsPage(
                    onVoiceSafetyClick = {
                        mainNavController.navigate("voiceSafetySettings") {
                            launchSingleTop = true
                        }
                    },


                    viewModel = accountSettingsViewModel,


                        exitButtonViewModel =
                            exitButtonViewModel,

                        onBackButtonClick = {
                            mainNavController.navigate(
                                "roleHome"
                            )
                        },

                        onDocumentPickerActiveChange = onDocumentPickerActiveChange,

                        onUploadedDocumentsClick = {},

                        onChangePasscodeClick = {},

                        onAccountInfoClick = {},

                        onLogoutClick = {

                            scope.launch {

                                LocalPasswordRepository(
                                    appContext
                                ).clearPassword()

                                appContext
                                    .getSharedPreferences(
                                        "app_settings",
                                        Context.MODE_PRIVATE
                                    )
                                    .edit()
                                    .clear()
                                    .commit()

                                AuthRepository()
                                    .logout()

                                mainNavController.navigate(
                                    "weather"
                                ) {

                                    popUpTo(
                                        mainNavController.graph.id
                                    ) {
                                        inclusive = true
                                    }

                                    launchSingleTop = true
                                }
                            }
                        },

                        onQuickExit = {
                            performQuickExit()
                        }
                    )
                }


                // ---------------------------------------------------------
                // ACCOUNT SETUP
                // ---------------------------------------------------------

                navigation(
                    startDestination =
                        "accountSetup/landing",

                    route =
                        "accountSetup"
                ) {


                    // -----------------------------------------------------
                    // LANDING
                    // -----------------------------------------------------

                    composable(
                        "accountSetup/landing"
                    ) {

                        AccountLandingStep(
                            onLoginClick = {
                                mainNavController.navigate(
                                    "login"
                                )
                            },

                            onCreateAccountClick = {
                                mainNavController.navigate(
                                    "accountSetup/type"
                                )
                            }
                        )
                    }


                    // -----------------------------------------------------
                    // ACCOUNT TYPE
                    // -----------------------------------------------------

                    composable(
                        "accountSetup/type"
                    ) { backStackEntry ->

                        val parentEntry =
                            remember(backStackEntry) {

                                mainNavController
                                    .getBackStackEntry(
                                        "accountSetup"
                                    )
                            }

                        val setupViewModel:
                                AccountSetupViewModel =
                            viewModel(
                                parentEntry
                            )

                        AccountTypeStep(
                            selectedAccountType =
                                setupViewModel
                                    .state
                                    .accountType,

                            onAccountTypeSelected = {
                                setupViewModel
                                    .updateAccountType(it)
                            },

                            onBackClick = {
                                mainNavController
                                    .popBackStack()
                            },

                            onContinueClick = {
                                mainNavController.navigate(
                                    "accountSetup/personal"
                                )
                            }
                        )
                    }


                    // -----------------------------------------------------
                    // PERSONAL INFORMATION
                    // -----------------------------------------------------

                    composable(
                        "accountSetup/personal"
                    ) { backStackEntry ->

                        val parentEntry =
                            remember(backStackEntry) {

                                mainNavController
                                    .getBackStackEntry(
                                        "accountSetup"
                                    )
                            }

                        val setupViewModel:
                                AccountSetupViewModel =
                            viewModel(
                                parentEntry
                            )

                        PersonalInformationStep(
                            firstName =
                                setupViewModel
                                    .state
                                    .firstName,

                            onFirstNameChange = { firstName ->

                                setupViewModel
                                    .updatePersonalInformation(
                                        firstName =
                                            firstName,

                                        lastName =
                                            setupViewModel
                                                .state
                                                .lastName,

                                        email =
                                            setupViewModel
                                                .state
                                                .email,

                                        phoneNumber =
                                            setupViewModel
                                                .state
                                                .phoneNumber,

                                        address =
                                            setupViewModel
                                                .state
                                                .address,

                                        username =
                                            setupViewModel
                                                .state
                                                .username,

                                        password =
                                            setupViewModel
                                                .state
                                                .password,

                                        confirmPassword =
                                            setupViewModel
                                                .state
                                                .confirmPassword
                                    )
                            },

                            lastName =
                                setupViewModel
                                    .state
                                    .lastName,

                            onLastNameChange = { lastName ->

                                setupViewModel
                                    .updatePersonalInformation(
                                        firstName =
                                            setupViewModel
                                                .state
                                                .firstName,

                                        lastName =
                                            lastName,

                                        email =
                                            setupViewModel
                                                .state
                                                .email,

                                        phoneNumber =
                                            setupViewModel
                                                .state
                                                .phoneNumber,

                                        address =
                                            setupViewModel
                                                .state
                                                .address,

                                        username =
                                            setupViewModel
                                                .state
                                                .username,

                                        password =
                                            setupViewModel
                                                .state
                                                .password,

                                        confirmPassword =
                                            setupViewModel
                                                .state
                                                .confirmPassword
                                    )
                            },

                            email =
                                setupViewModel
                                    .state
                                    .email,

                            onEmailChange = { email ->

                                setupViewModel
                                    .updatePersonalInformation(
                                        firstName =
                                            setupViewModel
                                                .state
                                                .firstName,

                                        lastName =
                                            setupViewModel
                                                .state
                                                .lastName,

                                        email =
                                            email,

                                        phoneNumber =
                                            setupViewModel
                                                .state
                                                .phoneNumber,

                                        address =
                                            setupViewModel
                                                .state
                                                .address,

                                        username =
                                            setupViewModel
                                                .state
                                                .username,

                                        password =
                                            setupViewModel
                                                .state
                                                .password,

                                        confirmPassword =
                                            setupViewModel
                                                .state
                                                .confirmPassword
                                    )
                            },

                            phoneNumber =
                                setupViewModel
                                    .state
                                    .phoneNumber,

                            onPhoneNumberChange = { phoneNumber ->

                                setupViewModel
                                    .updatePersonalInformation(
                                        firstName =
                                            setupViewModel
                                                .state
                                                .firstName,

                                        lastName =
                                            setupViewModel
                                                .state
                                                .lastName,

                                        email =
                                            setupViewModel
                                                .state
                                                .email,

                                        phoneNumber =
                                            phoneNumber,

                                        address =
                                            setupViewModel
                                                .state
                                                .address,

                                        username =
                                            setupViewModel
                                                .state
                                                .username,

                                        password =
                                            setupViewModel
                                                .state
                                                .password,

                                        confirmPassword =
                                            setupViewModel
                                                .state
                                                .confirmPassword
                                    )
                            },

                            address =
                                setupViewModel
                                    .state
                                    .address,

                            onAddressChange = { address ->

                                setupViewModel
                                    .updatePersonalInformation(
                                        firstName =
                                            setupViewModel
                                                .state
                                                .firstName,

                                        lastName =
                                            setupViewModel
                                                .state
                                                .lastName,

                                        email =
                                            setupViewModel
                                                .state
                                                .email,

                                        phoneNumber =
                                            setupViewModel
                                                .state
                                                .phoneNumber,

                                        address =
                                            address,

                                        username =
                                            setupViewModel
                                                .state
                                                .username,

                                        password =
                                            setupViewModel
                                                .state
                                                .password,

                                        confirmPassword =
                                            setupViewModel
                                                .state
                                                .confirmPassword
                                    )
                            },

                            username =
                                setupViewModel
                                    .state
                                    .username,

                            onUsernameChange = { username ->

                                setupViewModel
                                    .updatePersonalInformation(
                                        firstName =
                                            setupViewModel
                                                .state
                                                .firstName,

                                        lastName =
                                            setupViewModel
                                                .state
                                                .lastName,

                                        email =
                                            setupViewModel
                                                .state
                                                .email,

                                        phoneNumber =
                                            setupViewModel
                                                .state
                                                .phoneNumber,

                                        address =
                                            setupViewModel
                                                .state
                                                .address,

                                        username =
                                            username,

                                        password =
                                            setupViewModel
                                                .state
                                                .password,

                                        confirmPassword =
                                            setupViewModel
                                                .state
                                                .confirmPassword
                                    )
                            },

                            password =
                                setupViewModel
                                    .state
                                    .password,

                            onPasswordChange = { password ->

                                setupViewModel
                                    .updatePersonalInformation(
                                        firstName =
                                            setupViewModel
                                                .state
                                                .firstName,

                                        lastName =
                                            setupViewModel
                                                .state
                                                .lastName,

                                        email =
                                            setupViewModel
                                                .state
                                                .email,

                                        phoneNumber =
                                            setupViewModel
                                                .state
                                                .phoneNumber,

                                        address =
                                            setupViewModel
                                                .state
                                                .address,

                                        username =
                                            setupViewModel
                                                .state
                                                .username,

                                        password =
                                            password,

                                        confirmPassword =
                                            setupViewModel
                                                .state
                                                .confirmPassword
                                    )
                            },

                            confirmPassword =
                                setupViewModel
                                    .state
                                    .confirmPassword,

                            onConfirmPasswordChange = { confirmPassword ->

                                setupViewModel
                                    .updatePersonalInformation(
                                        firstName =
                                            setupViewModel
                                                .state
                                                .firstName,

                                        lastName =
                                            setupViewModel
                                                .state
                                                .lastName,

                                        email =
                                            setupViewModel
                                                .state
                                                .email,

                                        phoneNumber =
                                            setupViewModel
                                                .state
                                                .phoneNumber,

                                        address =
                                            setupViewModel
                                                .state
                                                .address,

                                        username =
                                            setupViewModel
                                                .state
                                                .username,

                                        password =
                                            setupViewModel
                                                .state
                                                .password,

                                        confirmPassword =
                                            confirmPassword
                                    )
                            },

                            onBackClick = {
                                mainNavController
                                    .popBackStack()
                            },

                            onNextClick = {
                                mainNavController.navigate(
                                    "accountSetup/documents"
                                )
                            }
                        )
                    }


                    // -----------------------------------------------------
                    // DOCUMENTS
                    // -----------------------------------------------------

                    composable(
                        "accountSetup/documents"
                    ) { backStackEntry ->

                        val parentEntry =
                            remember(backStackEntry) {

                                mainNavController
                                    .getBackStackEntry(
                                        "accountSetup"
                                    )
                            }

                        val setupViewModel:
                                AccountSetupViewModel =
                            viewModel(
                                parentEntry
                            )

                        DocumentsStep(
                            uploadedDocuments =
                                setupViewModel.state.uploadedDocuments,

                            onAddDocument = {
                                setupViewModel.addDocument(it)
                            },

                            onRemoveDocument = {
                                setupViewModel.removeDocument(it)
                            },

                            onBackClick = {
                                mainNavController.popBackStack()
                            },

                            onNextClick = {
                                mainNavController.navigate(
                                    "accountSetup/contacts"
                                )
                            },

                            onDocumentPickerActiveChange = onDocumentPickerActiveChange
                        )
                    }


                    // -----------------------------------------------------
                    // TRUSTED CONTACTS
                    // -----------------------------------------------------

                    composable(
                        "accountSetup/contacts"
                    ) { backStackEntry ->

                        val context = LocalContext.current

                        val parentEntry =
                            remember(backStackEntry) {

                                mainNavController
                                    .getBackStackEntry(
                                        "accountSetup"
                                    )
                            }

                        val setupViewModel:
                                AccountSetupViewModel =
                            viewModel(
                                parentEntry
                            )

                        TrustedContactsStep(
                            trustedContacts =
                                setupViewModel
                                    .state
                                    .trustedContacts,

                            isSubmitting =
                                setupViewModel
                                    .isSubmitting,

                            errorMessage =
                                setupViewModel
                                    .errorMessage,

                            onAddContact = {
                                setupViewModel
                                    .addTrustedContact(it)
                            },

                            onBackClick = {
                                mainNavController
                                    .popBackStack()
                            },

                            onFinishClick = {

                                setupViewModel
                                    .submitAccount(context) {

                                        mainNavController.navigate(
                                            "accountSetup/created"
                                        )
                                    }
                            }
                        )
                    }


                    // -----------------------------------------------------
                    // ACCOUNT CREATED
                    // -----------------------------------------------------

                    composable(
                        "accountSetup/created"
                    ) { backStackEntry ->

                        val parentEntry =
                            remember(backStackEntry) {

                                mainNavController
                                    .getBackStackEntry(
                                        "accountSetup"
                                    )
                            }

                        val setupViewModel:
                                AccountSetupViewModel =
                            viewModel(
                                parentEntry
                            )

                        AccountCreatedStep(
                            onGoToLoginClick = {

                                setupViewModel
                                    .resetState()

                                mainNavController.navigate(
                                    "createPasscode"
                                ) {

                                    popUpTo(
                                        "accountSetup"
                                    ) {
                                        inclusive = true
                                    }
                                }
                            }
                        )

                    }

                }
            } // End of NavHost

            // Shared floating Quick Exit button
            // Shared floating Quick Exit button
            if (
                currentRoute != null &&
                privacyLevel != PrivacyLevel.PUBLIC
            ) {
                QuickExitButton(
                    onClick = performQuickExit,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 16.dp,
                            bottom = 24.dp
                        )
                        .zIndex(10f)
                )
            }

        } // End of Box
    } // End of CompositionLocalProvider
} // End of AppNavigation

