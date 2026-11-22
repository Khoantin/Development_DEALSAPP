package com.example.androidapp.security.voice
import com.google.firebase.firestore.MetadataChanges
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.androidapp.MainActivity
import com.example.androidapp.data.DeviceLocation
import com.example.androidapp.data.DeviceLocationRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class VoiceLocationService : Service() {

    companion object {
        const val EXTRA_UID = "owner_uid"
        const val EXTRA_EVENT_ID = "event_id"

        private const val CHANNEL_ID = "location_sharing"
        private const val NOTIFICATION_ID = 39501
    }

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var locations: DeviceLocationRepository

    private var ownerUid: String? = null
    private var eventId: String? = null
    private var writingLocation = false
    private var generation = 0
    private var stopping = false

    private var eventListener: ListenerRegistration? = null
    private var shareListener: ListenerRegistration? = null

    private val authListener =
        FirebaseAuth.AuthStateListener { firebaseAuth ->
            val owner = ownerUid
            if (owner != null && firebaseAuth.currentUser?.uid != owner) {
                finishService()
            }
        }

    override fun onCreate() {
        super.onCreate()

        locations = DeviceLocationRepository(applicationContext)
        createNotificationChannel()
        auth.addAuthStateListener(authListener)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        val uid = intent?.getStringExtra(EXTRA_UID)
        val id = intent?.getStringExtra(EXTRA_EVENT_ID)

        if (
            uid.isNullOrBlank() ||
            id.isNullOrBlank() ||
            uid.contains("/") ||
            id.contains("/") ||
            auth.currentUser?.uid != uid
        ) {
            finishService()
            return START_NOT_STICKY
        }

        if (stopping) {
            return START_NOT_STICKY
        }

        // Returning to the app must not register GPS callbacks twice.
        if (ownerUid == uid && eventId == id) {
            return START_NOT_STICKY
        }

        releaseSession()

        ownerUid = uid
        eventId = id

        val operation = generation

        try {
            val notification = buildNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            finishService()
            return START_NOT_STICKY
        }

        val eventRef =
            db.collection("emergencyEvents").document(id)

        val shareRef =
            db.collection("locationShares").document(uid)

        // Verify this event and its corresponding share before using GPS.
        db.runTransaction { transaction ->
            val event = transaction.get(eventRef)
            val share = transaction.get(shareRef)
            val startedAt = event.getTimestamp("startedAt")

            event.getString("ownerId") == uid &&
                    event.getString("status") == "active" &&
                    share.getString("ownerId") == uid &&
                    share.getBoolean("active") == true &&
                    startedAt != null &&
                    share.getTimestamp("startedAt") == startedAt
        }
            .addOnSuccessListener { valid ->
                if (!isCurrent(operation)) {
                    return@addOnSuccessListener
                }

                if (!valid) {
                    finishService()
                    return@addOnSuccessListener
                }


                observeSession(uid, id, operation)

                locations.startLocationUpdates(
                    onLocationUpdate = { location ->

                        if (isCurrent(operation) && !writingLocation) {
                            writeLocation(uid, id, operation, location)
                        }
                    },
                    onError = { exception ->

                        if (isCurrent(operation)) {
                            finishService()
                        }
                    }
                )
            }
            .addOnFailureListener {
                if (isCurrent(operation)) {
                    finishService()
                }
            }

        // Recovery happens when the user reopens the app.
        // Do not automatically restart a killed service.
        return START_NOT_STICKY
    }

    private fun observeSession(
        uid: String,
        id: String,
        operation: Int
    ) {
        eventListener =
            db.collection("emergencyEvents")
                .document(id)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (!isCurrent(operation)) {
                        return@addSnapshotListener
                    }

                    if (error != null) {

                        finishService()
                        return@addSnapshotListener
                    }

                    if (snapshot == null) {

                        finishService()
                        return@addSnapshotListener
                    }

                    if (snapshot.metadata.isFromCache) {

                        return@addSnapshotListener
                    }

                    val ownerMatches =
                        snapshot.getString("ownerId") == uid
                    val status = snapshot.getString("status")



                    if (
                        !snapshot.exists() ||
                        !ownerMatches ||
                        status != "active"
                    ) {
                        finishService()
                    }
                }

        shareListener =
            db.collection("locationShares")
                .document(uid)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (!isCurrent(operation)) {
                        return@addSnapshotListener
                    }

                    if (error != null) {
                        finishService()
                        return@addSnapshotListener
                    }

                    if (snapshot == null) {

                        finishService()
                        return@addSnapshotListener
                    }

                    if (snapshot.metadata.isFromCache) {
                        return@addSnapshotListener
                    }

                    val ownerMatches =
                        snapshot.getString("ownerId") == uid
                    val active = snapshot.getBoolean("active")


                    if (
                        !snapshot.exists() ||
                        !ownerMatches ||
                        active != true
                    ) {
                        finishService()
                    }
                }
    }

    private fun writeLocation(
        uid: String,
        id: String,
        operation: Int,
        location: DeviceLocation
    ) {
        writingLocation = true

        val eventRef =
            db.collection("emergencyEvents").document(id)

        val shareRef =
            db.collection("locationShares").document(uid)

        // Check the session on every write, including transaction retries.
        db.runTransaction { transaction ->
            val event = transaction.get(eventRef)
            val share = transaction.get(shareRef)
            val startedAt = event.getTimestamp("startedAt")

            val active =
                event.getString("ownerId") == uid &&
                        event.getString("status") == "active" &&
                        share.getString("ownerId") == uid &&
                        share.getBoolean("active") == true &&
                        startedAt != null &&
                        share.getTimestamp("startedAt") == startedAt

            if (active) {
                transaction.update(
                    shareRef,
                    mapOf(
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            active
        }
            .addOnSuccessListener { active ->

                if (!isCurrent(operation)) {
                    return@addOnSuccessListener
                }

                writingLocation = false

                if (!active) {
                    finishService()
                }
            }
            .addOnFailureListener { exception ->


                if (isCurrent(operation)) {
                    writingLocation = false
                }
            }
    }

    private fun isCurrent(operation: Int): Boolean {
        return !stopping &&
                generation == operation &&
                ownerUid != null &&
                auth.currentUser?.uid == ownerUid
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location sharing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing location-sharing sessions"
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent =
            Intent(this, MainActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Location sharing")
            .setContentText("Location sharing service is running. Open the app to manage.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun releaseSession() {
        generation++

        locations.stopLocationUpdates()

        eventListener?.remove()
        eventListener = null

        shareListener?.remove()
        shareListener = null

        writingLocation = false
        ownerUid = null
        eventId = null
    }

    private fun finishService() {

        if (stopping) return

        stopping = true
        releaseSession()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {

        stopping = true
        releaseSession()
        auth.removeAuthStateListener(authListener)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}