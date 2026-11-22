package com.example.androidapp.pages.emergency

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidapp.data.EmergencyLocationSnapshot
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private data class EmergencyInboxItem(
    val id: String,
    val message: String,
    val createdAt: Date?,
    val read: Boolean,
    val location: EmergencyLocationSnapshot?,
    val locationInvalid: Boolean,
    val status: String?,
    val cancelledAt: Date?
)

@Composable
fun EmergencyInboxScreen(onBack: () -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    var items by remember { mutableStateOf<List<EmergencyInboxItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    DisposableEffect(auth, db, reload) {
        var disposed = false
        var requestVersion = 0
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val version = ++requestVersion
            val uid = firebaseAuth.currentUser?.uid
            items = emptyList()
            error = null
            loading = uid != null
            fun isCurrent(): Boolean = !disposed && version == requestVersion && auth.currentUser?.uid == uid
            if (uid == null) {
                error = "Please sign in to view your emergency alerts."
            } else {
                db.collection("users").document(uid).collection("emergencyNotifications")
                    .orderBy("createdAt", Query.Direction.DESCENDING).limit(50).get(Source.SERVER)
                    .addOnSuccessListener { snapshot ->
                        if (isCurrent()) {
                            items = snapshot.documents.map { document ->
                                val rawLocation = document.get("location")
                                val location = (rawLocation as? Map<*, *>)?.let { EmergencyLocationSnapshot.fromMap(it) }
                                EmergencyInboxItem(
                                    id = document.id,
                                    message = document.getString("message") ?: "Alert message unavailable.",
                                    createdAt = document.getTimestamp("createdAt")?.toDate(),
                                    read = document.getBoolean("read") ?: false,
                                    location = location,
                                    locationInvalid = rawLocation != null && location == null,
                                    status = document.getString("status"),
                                    cancelledAt = document.getTimestamp("cancelledAt")?.toDate()
                                )
                            }
                            loading = false
                        }
                    }.addOnFailureListener {
                        if (isCurrent()) {
                            items = emptyList()
                            loading = false
                            error = "Unable to load emergency alerts. Check your connection and retry."
                        }
                    }
            }
        }
        auth.addAuthStateListener(listener)
        onDispose {
            disposed = true
            requestVersion++
            auth.removeAuthStateListener(listener)
        }
    }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Emergency inbox", style = MaterialTheme.typography.headlineMedium)
        Text("Your latest 50 alerts. Tap Refresh to check for new alerts.")
        Button(onClick = { reload++ }, enabled = !loading) { Text("Refresh") }
        when {
            loading -> CircularProgressIndicator()
            error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            items.isEmpty() -> Text("No emergency alerts yet.")
            else -> items.forEach { item ->
                key(item.id) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (item.status == "cancelled") "Emergency alert — Cancelled" else "Emergency alert",
                                style = MaterialTheme.typography.titleMedium)
                            if (item.status == "cancelled") {
                                Text("The sender cancelled this alert. This does not confirm that they are safe.")
                                item.cancelledAt?.let { Text("Cancelled: ${dateFormat.format(it)}") }
                            } else {
                                Text(if (item.status == null || item.status == "active")
                                    "No cancellation recorded in this inbox. Refresh to check for changes."
                                else "Alert status unavailable. Refresh to check for changes.")
                            }
                            Text(item.message)
                            Text(item.createdAt?.let(dateFormat::format) ?: "Time unavailable",
                                style = MaterialTheme.typography.bodySmall)
                            val location = item.location
                            if (location != null) {
                                HorizontalDivider()
                                Text("Location snapshot", style = MaterialTheme.typography.titleSmall)
                                Text("Device-reported position at the time below. This is not live tracking; the sender may have moved.")
                                Text(String.format(Locale.US, "Latitude: %.5f\nLongitude: %.5f", location.latitude, location.longitude))
                                Text("Reported accuracy: about ${location.accuracyMeters.toInt()} m")
                                Text("Captured: ${dateFormat.format(Date(location.capturedAtMillis))}")
                            } else {
                                Text(if (item.locationInvalid) "Location could not be displayed." else "No location attached.")
                            }
                        }
                    }
                }
            }
        }
        Text("Viewing this inbox does not send a response or confirm that help is on the way.",
            style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(80.dp))
    }
}
