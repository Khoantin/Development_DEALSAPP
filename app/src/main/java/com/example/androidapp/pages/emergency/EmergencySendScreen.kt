package com.example.androidapp.pages.emergency

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.example.androidapp.data.EmergencyLocationSnapshot
import com.example.androidapp.data.EmergencyPendingLocationCipher
import com.example.androidapp.security.PrivacyDialogFocus
import com.example.androidapp.security.trackPrivacyInteraction
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.androidapp.data.EmergencyAlertRepository
import com.example.androidapp.data.TrustedContact
import com.example.androidapp.data.TrustedContactRepository
import com.example.androidapp.data.toEmergencyAlertMessage
import com.example.androidapp.data.toEmergencyCancellationMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Composable
fun EmergencySendScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val contactsRepository = remember { TrustedContactRepository(db) }
    val alertRepository = remember { EmergencyAlertRepository() }

    // Store identifiers and, only while pending, an encrypted location snapshot. Never store the message.
    val preferences = remember(context) {
        context.getSharedPreferences(
            "emergency_pending_requests",
            Context.MODE_PRIVATE
        )
    }

    var activeUid by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableStateOf(0) }
    var reload by remember { mutableStateOf(0) }

    var contacts by remember {
        mutableStateOf<List<TrustedContact>>(emptyList())
    }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var ready by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showConfirm by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    var cancelPending by remember { mutableStateOf(false) }

    var requestId by remember { mutableStateOf<String?>(null) }
    var requestContactIds by remember {
        mutableStateOf<List<String>>(emptyList())
    }
    var completed by remember { mutableStateOf(false) }
    var confirmedCount by remember { mutableStateOf(0) }
    var confirmedState by remember { mutableStateOf("") }

    var includeLocation by remember { mutableStateOf(false) }
    var locationSnapshot by remember { mutableStateOf<EmergencyLocationSnapshot?>(null) }
    var pendingLocation by remember { mutableStateOf<EmergencyLocationSnapshot?>(null) }
    var requestHadLocation by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var locationToken by remember { mutableStateOf<CancellationTokenSource?>(null) }
    var permissionOwner by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val locationClient = remember(context) { LocationServices.getFusedLocationProviderClient(context) }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun fetchLocation() {
        val uid = activeUid ?: return
        if (!includeLocation || requestId != null || auth.currentUser?.uid != uid) return
        if (!hasLocationPermission()) {
            locationError = "Location permission is unavailable. You can turn off location and send the message alone."
            return
        }
        locationToken?.cancel()
        val token = CancellationTokenSource()
        locationToken = token
        val version = generation
        locating = true
        locationSnapshot = null
        locationError = null
        fun current(): Boolean = generation == version && auth.currentUser?.uid == uid &&
                locationToken === token && includeLocation && requestId == null
        try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(30000L)
                .setDurationMillis(20000L)
                .build()
            locationClient.getCurrentLocation(request, token.token)
                .addOnSuccessListener { location ->
                    if (current()) {
                        locating = false
                        val age = location?.let { (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1000000L }
                        if (location == null || age == null || age !in 0L..60000L || !location.hasAccuracy()) {
                            locationError = "No recent location was available. Try again or turn off location to send without it."
                        } else {
                            val snapshot = EmergencyLocationSnapshot(location.latitude, location.longitude,
                                location.accuracy.toDouble(), location.time)
                            if (snapshot.isValid()) locationSnapshot = snapshot
                            else locationError = "The device returned an invalid location. Try again or send without it."
                        }
                    }
                }.addOnFailureListener {
                    if (current()) {
                        locating = false
                        locationError = "Could not obtain location. Try again or turn off location to send without it."
                    }
                }
        } catch (_: SecurityException) {
            locating = false
            locationError = "Location permission was removed. Turn off location or grant permission again."
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val owner = permissionOwner
        permissionOwner = null
        if (owner != null && owner.first == activeUid && owner.second == generation && includeLocation) {
            if (hasLocationPermission()) fetchLocation()
            else locationError = "Permission was denied. Turn off location to send the message alone."
        }
    }

    fun requestJson(
        id: String,
        ids: List<String>,
        done: Boolean,
        count: Int = 0,
        state: String = "",
        encryptedLocation: String? = null,
        hadLocation: Boolean = false,
        cancellationPending: Boolean = false
    ): String = JSONObject().apply {
        put("requestId", id)
        put("contactIds", JSONArray(ids))
        put("completed", done)
        put("count", count)
        put("state", state)
        put("hadLocation", hadLocation)
        put("cancellationPending", cancellationPending)
        if (!done && encryptedLocation != null) put("encryptedLocation", encryptedLocation)
    }.toString()

    DisposableEffect(auth, db, reload) {
        var disposed = false

        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            generation++
            locationToken?.cancel()
            locationToken = null
            locating = false
            includeLocation = false
            locationSnapshot = null
            pendingLocation = null
            requestHadLocation = false
            locationError = null
            permissionOwner = null
            val version = generation
            val uid = firebaseAuth.currentUser?.uid

            activeUid = uid
            contacts = emptyList()
            message = ""
            loading = uid != null
            ready = false
            sending = false
            error = null
            showConfirm = false
            showCancelConfirm = false
            cancelling = false
            cancelPending = false
            requestId = null
            requestContactIds = emptyList()
            completed = false
            confirmedCount = 0
            confirmedState = ""

            fun current(): Boolean =
                !disposed &&
                        generation == version &&
                        auth.currentUser?.uid == uid

            if (uid == null) {
                error = "Please sign in to send an alert."
            } else {
                val stored = preferences.getString(uid, null)

                if (stored != null) {
                    try {
                        val data = JSONObject(stored)
                        val id = data.getString("requestId")
                        val parsedId = UUID.fromString(id)

                        check(parsedId.version() == 4)

                        val array = data.getJSONArray("contactIds")
                        val ids = List(array.length()) {
                            array.getString(it)
                        }

                        check(ids.size in 1..10)
                        check(ids.all {
                            it.isNotBlank() &&
                                    !it.contains("/") &&
                                    it != "." &&
                                    it != ".."
                        })

                        requestId = id
                        requestContactIds = ids
                        completed = data.getBoolean("completed")
                        confirmedCount = data.optInt("count", 0)
                        confirmedState = data.optString("state", "")
                        cancelPending = data.optBoolean("cancellationPending", false)
                        check(!cancelPending || completed)
                        if (completed) {
                            check(confirmedState == "active" || confirmedState == "cancelled")
                            check(confirmedCount in 1..ids.distinct().size)
                        }
                        requestHadLocation = data.optBoolean("hadLocation", false)
                        if (!completed && requestHadLocation) {
                            pendingLocation = EmergencyPendingLocationCipher.decrypt(uid, id, data.getString("encryptedLocation"))
                        }
                        loading = false
                        ready = true
                    } catch (_: Exception) {
                        loading = false
                        ready = false
                        requestId = null
                        requestContactIds = emptyList()
                        pendingLocation = null
                        completed = false
                        cancelPending = false
                        error =
                            "The saved alert request could not be read. Do not clear app data yet; check the existing alert before starting another."
                    }
                } else {
                    contactsRepository.getContacts(uid) { result ->
                        if (current()) {
                            result.fold(
                                onSuccess = { loadedContacts ->
                                    db.collection("users")
                                        .document(uid)
                                        .collection("emergencySettings")
                                        .document("preset")
                                        .get(Source.SERVER)
                                        .addOnSuccessListener { settings ->
                                            if (current()) {
                                                contacts = loadedContacts.filter {
                                                    it.emergencyContact &&
                                                            it.emergencyInAppOptIn &&
                                                            !it.linkedUserId.isNullOrBlank() &&
                                                            it.linkedUserId != uid
                                                }.sortedBy {
                                                    it.name.lowercase()
                                                }

                                                message =
                                                    settings.getString("message")
                                                        ?: ""

                                                loading = false
                                                ready = true
                                            }
                                        }
                                        .addOnFailureListener {
                                            if (current()) {
                                                loading = false
                                                error =
                                                    "Could not load your saved message. Check your connection and reload."
                                            }
                                        }
                                },
                                onFailure = {
                                    loading = false
                                    error =
                                        "Could not load your contacts. Check your connection and reload."
                                }
                            )
                        }
                    }
                }
            }
        }

        auth.addAuthStateListener(listener)

        onDispose {
            disposed = true
            locationToken?.cancel()
            locationToken = null
            permissionOwner = null
            generation++
            auth.removeAuthStateListener(listener)
        }
    }

    fun sendRequest(id: String, ids: List<String>, snapshot: EmergencyLocationSnapshot?) {
        val uid = activeUid

        if (uid == null || auth.currentUser?.uid != uid) {
            error = "Please sign in again."
            return
        }

        if (sending) return

        val version = generation
        sending = true
        error = null

        alertRepository.sendInAppAlert(
            uid = uid,
            requestId = id,
            contactIds = ids,
            location = snapshot
        ) { result ->
            if (
                generation == version &&
                auth.currentUser?.uid == uid
            ) {
                sending = false

                result.onSuccess { response ->
                    completed = true
                    confirmedCount = response.inAppRecordCount
                    confirmedState = response.state

                    val saved = preferences.edit()
                        .putString(
                            uid,
                            requestJson(
                                id = id,
                                ids = ids,
                                done = true,
                                count = response.inAppRecordCount,
                                state = response.state,
                                hadLocation = requestHadLocation
                            )
                        )
                        .commit()

                    if (saved) pendingLocation = null
                    if (!saved) {
                        error =
                            "The server confirmed the alert, but its local confirmation could not be saved. Reopening may offer a retry of the same request."
                    }
                }

                result.onFailure {
                    error = it.toEmergencyAlertMessage()
                }
            }
        }
    }

    fun cancelConfirmedAlert() {
        val uid = activeUid
        val id = requestId
        if (uid == null || auth.currentUser?.uid != uid || id == null || !completed) {
            error = "Sign in as the sender and confirm this alert before cancelling."
            return
        }
        if (sending || cancelling) return
        // Save intent before the call. Reopening offers an explicit safe retry.
        val stored = preferences.edit().putString(uid, requestJson(
            id = id, ids = requestContactIds, done = true,
            count = confirmedCount, state = confirmedState,
            hadLocation = requestHadLocation, cancellationPending = true
        )).commit()
        if (!stored) {
            error = "Could not save the cancellation request. No cancellation was submitted."
            return
        }
        cancelPending = true
        cancelling = true
        error = null
        val version = generation
        alertRepository.cancelInAppAlert(uid, "${uid}_${id.lowercase()}") { result ->
            if (version == generation && auth.currentUser?.uid == uid) {
                cancelling = false
                result.onSuccess { response ->
                    confirmedState = response.state
                    confirmedCount = response.inAppRecordCount
                    val saved = preferences.edit().putString(uid, requestJson(
                        id = id, ids = requestContactIds, done = true,
                        count = response.inAppRecordCount, state = response.state,
                        hadLocation = requestHadLocation, cancellationPending = false
                    )).commit()
                    cancelPending = !saved
                    if (!saved) {
                        error = "Cancellation was confirmed, but its local confirmation could not be saved. Retry cancellation to save it again."
                    }
                }.onFailure { error = it.toEmergencyCancellationMessage() }
            }
        }
    }

    if (showCancelConfirm) {
        AlertDialog(
            modifier = Modifier.trackPrivacyInteraction(),
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel this emergency alert?") },
            text = {
                PrivacyDialogFocus()
                Text("The alert will be marked Cancelled in recipient inboxes after they refresh. " +
                        "This does not erase its message or location, undo what anyone already saw, " +
                        "or confirm that you are safe. It does not stop a separate live location-sharing session.")
            },
            confirmButton = {
                TextButton(enabled = !cancelling && !sending, onClick = {
                    showCancelConfirm = false
                    cancelConfirmedAlert()
                }) { Text("Cancel alert") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Keep alert active") }
            }
        )
    }

    if (showConfirm) {
        AlertDialog(
            modifier = Modifier.trackPrivacyInteraction(),
            onDismissRequest = { showConfirm = false },
            title = { Text("Send in-app emergency alert?") },
            text = {
                PrivacyDialogFocus()
                Text(
                    "Send your saved help message to the " +
                            "${contacts.size} contact(s) listed on this screen? " +
                            (if (includeLocation) "Include the displayed location snapshot, its accuracy, and capture time for every listed recipient. "
                            else "No location will be included. ") +
                            "This does not send SMS, email, or contact emergency services."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = sendClick@{
                        showConfirm = false
                        val uid = activeUid

                        if (
                            uid == null ||
                            auth.currentUser?.uid != uid
                        ) {
                            error = "Please sign in again."
                        } else {
                            val snapshot = if (includeLocation) locationSnapshot else null
                            if (includeLocation && (snapshot == null || !hasLocationPermission() ||
                                        System.currentTimeMillis() - snapshot.capturedAtMillis !in 0L..120000L)) {
                                locationError = "Get a fresh location or turn off location before sending."
                                return@sendClick
                            }
                            val id = UUID.randomUUID().toString()
                            val ids = contacts.map { it.id }.distinct().sorted()

                            val encrypted = try {
                                snapshot?.let { EmergencyPendingLocationCipher.encrypt(uid, id, it) }
                            } catch (_: Exception) {
                                error = "Could not securely store the pending location. Nothing was sent. Turn off location to send without it."
                                return@sendClick
                            }
                            // Persist before making the network request.
                            val saved = preferences.edit()
                                .putString(
                                    uid,
                                    requestJson(
                                        id = id,
                                        ids = ids,
                                        done = false,
                                        encryptedLocation = encrypted,
                                        hadLocation = snapshot != null
                                    )
                                )
                                .commit()

                            if (saved) {
                                requestId = id
                                requestContactIds = ids
                                pendingLocation = snapshot
                                requestHadLocation = snapshot != null
                                sendRequest(id, ids, snapshot)
                            } else {
                                error =
                                    "Could not save the alert request on this device. Nothing was sent."
                            }
                        }
                    }
                ) {
                    Text("Send alert")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("Back")
        }

        Text(
            text = "Send emergency alert",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            "In-app alerts only. Recipients must open their emergency inbox " +
                    "and refresh. Location is optional. No phone push notification, SMS, or email is sent."
        )

        if (sending || cancelling) {
            CircularProgressIndicator()
            Text(if (cancelling) "Waiting for cancellation confirmation…" else "Waiting for server confirmation…")
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        when {
            loading -> CircularProgressIndicator()

            completed -> {
                Text(
                    if (confirmedState == "cancelled") {
                        "This alert was previously created and is now cancelled."
                    } else {
                        "The server confirmed an alert record in " +
                                "$confirmedCount recipient inbox(es)."
                    }
                )

                Text(if (requestHadLocation) "This alert included a location snapshot. It is not live tracking." else "This alert did not include location.")
                Text("This does not confirm that anyone has read it or will respond.")

                if (cancelPending) {
                    Text("A cancellation request is saved. Retry to confirm its outcome; reopening does not submit it automatically.")
                    OutlinedButton(enabled = !cancelling && !sending, onClick = { cancelConfirmedAlert() }) {
                        Text("Retry cancellation")
                    }
                } else if (confirmedState == "active") {
                    OutlinedButton(enabled = !cancelling && !sending, onClick = { showCancelConfirm = true }) {
                        Text("Cancel alert")
                    }
                }
                if (confirmedState == "cancelled") {
                    Text("Recipients see Cancelled after refreshing. This does not confirm that you are safe or recall the original information.")
                }

                OutlinedButton(
                    enabled = !sending && !cancelling && !cancelPending,
                    onClick = {
                        val uid = activeUid
                        if (
                            uid != null &&
                            auth.currentUser?.uid == uid
                        ) {
                            if (preferences.edit().remove(uid).commit()) {
                                reload++
                            } else {
                                error = "Could not prepare a new alert. Please retry."
                            }
                        }
                    }
                ) {
                    Text("Prepare another alert")
                }
                Text("Preparing another alert does not cancel an active earlier alert. This screen keeps only the latest alert reference; cancel first if needed.")
            }

            requestId != null -> {
                Text(
                    "An alert request is saved for ${requestContactIds.size} " +
                            "contact(s). Its outcome has not been confirmed here."
                )

                Text(
                    "Retry uses the same request ID. If the alert already exists, " +
                            "the server returns it without creating another."
                )

                if (requestHadLocation) {
                    Text("Retry keeps the original snapshot, even if it is now older. It will not obtain a new location.")
                    pendingLocation?.let { Text("Captured: ${dateFormat.format(Date(it.capturedAtMillis))}") }
                }
                Button(
                    enabled = !sending && (!requestHadLocation || pendingLocation != null),
                    onClick = {
                        requestId?.let {
                            sendRequest(it, requestContactIds, pendingLocation)
                        }
                    }
                ) {
                    Text("Retry same alert")
                }
            }

            ready -> {
                Text(
                    "Only saved emergency contacts with Send in-app alerts " +
                            "enabled are included."
                )

                if (contacts.isEmpty()) {
                    Text(
                        "No eligible contacts. Go back, select a linked contact, " +
                                "save settings, and enable their in-app channel."
                    )
                } else {
                    contacts.forEach { contact ->
                        Text("• ${contact.name.ifBlank { "Unnamed contact" }}")
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = message.ifBlank {
                            "No saved help message. Go back and save one first."
                        },
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Text(
                    "The server checks the latest saved message and contact " +
                            "preferences when processing the request."
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Include location snapshot", modifier = Modifier.weight(1f))
                            Switch(checked = includeLocation, enabled = !sending, onCheckedChange = { enabled ->
                                includeLocation = enabled
                                locationToken?.cancel()
                                locationToken = null
                                locating = false
                                locationSnapshot = null
                                locationError = null
                            })
                        }
                        Text("Off by default. When included, the snapshot is stored with this alert and visible to all listed recipients. It does not start live location sharing.")
                        if (includeLocation) {
                            Text("Approximate permission is supported; the reported accuracy will be shown to recipients.")
                            locationSnapshot?.let { snapshot ->
                                Text(String.format(Locale.US, "Latitude %.5f, longitude %.5f", snapshot.latitude, snapshot.longitude))
                                Text("Reported accuracy: about ${snapshot.accuracyMeters.toInt()} m")
                                Text("Captured: ${dateFormat.format(Date(snapshot.capturedAtMillis))}")
                            }
                            locationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            if (locating) { CircularProgressIndicator(); Text("Getting location…") }
                            OutlinedButton(enabled = !locating && !sending, onClick = {
                                if (hasLocationPermission()) fetchLocation()
                                else {
                                    activeUid?.let { permissionOwner = it to generation }
                                    locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                }
                            }) { Text(if (locationSnapshot == null) "Get location" else "Refresh location") }
                            Text("If location is unavailable, turn this switch off to send without it.")
                        }
                    }
                }

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sending &&
                            contacts.size in 1..10 &&
                            message.isNotBlank() && !locating &&
                            (!includeLocation || locationSnapshot != null),
                    onClick = { showConfirm = true }
                ) {
                    Text("Review and send alert")
                }

                if (contacts.size > 10) {
                    Text("This version supports up to 10 contacts per alert.")
                }

                OutlinedButton(
                    enabled = !sending,
                    onClick = { reload++ }
                ) {
                    Text("Reload saved settings")
                }
            }

            else -> {
                Button(onClick = { reload++ }) {
                    Text("Reload")
                }
            }
        }

        Text(
            "Back and Quick Exit leave this screen. They do not cancel " +
                    "a request already submitted."
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}
