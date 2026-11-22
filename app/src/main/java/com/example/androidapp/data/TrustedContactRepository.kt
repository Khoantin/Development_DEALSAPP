package com.example.androidapp.data
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source


import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
class TrustedContactRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private fun contactsCollection(uid: String) =
        db.collection("users")
            .document(uid)
            .collection("trustedContacts")


    fun saveContacts(
        uid: String,
        contacts: List<TrustedContact>,
        onResult: (Result<Unit>) -> Unit
    ) {

        if (contacts.isEmpty()) {
            onResult(Result.success(Unit))
            return
        }

        val batch = db.batch()

        contacts.forEach { contact ->

            val document =
                if (contact.id.isBlank()) contactsCollection(uid).document()
                else contactsCollection(uid).document(contact.id)

            val data =
                hashMapOf<String, Any?>(
                    "name" to contact.name,
                    "phoneNumber" to contact.phoneNumber,
                    "relationship" to contact.relationship,
                    "accessGiven" to contact.accessGiven,
                    "linkedUserId" to contact.linkedUserId,
                    "privateNotes" to contact.privateNotes,
                    "safeToStay" to contact.safeToStay
                )

            batch.set(document, data, SetOptions.merge())
        }

        batch.commit()
            .addOnSuccessListener {
                onResult(Result.success(Unit))
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    fun saveContact(
        uid: String,
        contact: TrustedContact,
        onResult: (Result<Unit>) -> Unit
    ) {
        val docRef = if (contact.id.isBlank()) {
            contactsCollection(uid).document()
        } else {
            contactsCollection(uid).document(contact.id)
        }

        val data = hashMapOf<String, Any?>(
            "name" to contact.name,
            "phoneNumber" to contact.phoneNumber,
            "relationship" to contact.relationship,
            "accessGiven" to contact.accessGiven,
            "linkedUserId" to contact.linkedUserId,
            "privateNotes" to contact.privateNotes,
            "safeToStay" to contact.safeToStay
        )

        docRef.set(data, SetOptions.merge())
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }


    fun getContacts(uid: String, onResult: (Result<List<TrustedContact>>) -> Unit) {
        readContacts(uid, Source.DEFAULT, onResult)
    }

    fun getContactsFromServer(uid: String, onResult: (Result<List<TrustedContact>>) -> Unit) {
        readContacts(uid, Source.SERVER, onResult)
    }

    private fun readContacts(
        uid: String,
        source: Source,
        onResult: (Result<List<TrustedContact>>) -> Unit
    ) {

        contactsCollection(uid)
            .get(source)
            .addOnSuccessListener { snapshot ->

                val contacts =
                    snapshot.documents.map { document ->

                        TrustedContact(
                            id = document.id,

                            name =
                                document.getString("name")
                                    ?: "",

                            phoneNumber =
                                document.getString("phoneNumber")
                                    ?: "",

                            relationship =
                                document.getString("relationship")
                                    ?: "",

                            accessGiven =
                                document.getBoolean("accessGiven")
                                    ?: false,

                            linkedUserId =
                                document.getString("linkedUserId"),

                            privateNotes =
                                document.getString("privateNotes")
                                    ?: "",

                            safeToStay =
                                document.getBoolean("safeToStay")
                                    ?: false,

                            email =
                                document.getString("email")
                                    ?: "",

                            emergencyContact =
                                document.getBoolean("emergencyContact")
                                    ?: false,

                            emergencyEmailOptIn =
                                document.getBoolean("emergencyEmailOptIn")
                                    ?: false,

                            emergencyInAppOptIn =
                                document.getBoolean("emergencyInAppOptIn")
                                    ?: false,

                            emergencySmsRecipient =
                                document.getBoolean("emergencySmsRecipient") ?: false,
                            categories = (document.get("categories") as? List<*>)
                                ?.filterIsInstance<String>() ?: listOf("TRUSTED"),
                            contactCreatedAtMillis = document.getTimestamp("createdAt")?.toDate()?.time
                                ?: document.getLong("contactCreatedAtMillis") ?: 0L,
                            contactUpdatedAtMillis = document.getTimestamp("updatedAt")?.toDate()?.time
                                ?: document.getLong("contactUpdatedAtMillis") ?: 0L
                        )
                    }

                onResult(
                    Result.success(contacts)
                )
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }


    fun updateAccess(
        uid: String,
        contactId: String,
        accessGiven: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {

        contactsCollection(uid)
            .document(contactId)
            .update(
                "accessGiven",
                accessGiven
            )
            .addOnSuccessListener {
                onResult(Result.success(Unit))
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }


    fun deleteContact(
        uid: String,
        contactId: String,
        onResult: (Result<Unit>) -> Unit
    ) {

        contactsCollection(uid)
            .document(contactId)
            .delete()
            .addOnSuccessListener {
                onResult(Result.success(Unit))
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }
    fun saveEmergencySetup(
        uid: String,
        selectedContactIds: Set<String>,
        message: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val cleanMessage = message.trim()

        if (
            uid.isBlank() ||
            FirebaseAuth.getInstance().currentUser?.uid != uid
        ) {
            onResult(
                Result.failure(
                    IllegalStateException("Please sign in again.")
                )
            )
            return
        }

        if (cleanMessage.isEmpty() || cleanMessage.length > 1000) {
            onResult(
                Result.failure(
                    IllegalArgumentException(
                        "Enter a help message between 1 and 1000 characters."
                    )
                )
            )
            return
        }

        val settingsRef = db.collection("users")
            .document(uid)
            .collection("emergencySettings")
            .document("preset")

        // Fetch the current contact list from the server.
        contactsCollection(uid)
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { snapshot ->
                if (FirebaseAuth.getInstance().currentUser?.uid != uid) {
                    onResult(
                        Result.failure(
                            IllegalStateException("Your account changed. Please retry.")
                        )
                    )
                    return@addOnSuccessListener
                }

                val availableIds = snapshot.documents.map { it.id }.toSet()

                if (!availableIds.containsAll(selectedContactIds)) {
                    onResult(
                        Result.failure(
                            IllegalStateException(
                                "Your contacts changed. Reload the screen and try again."
                            )
                        )
                    )
                    return@addOnSuccessListener
                }

                // Keep this setup operation within a small, bounded write count.
                if (snapshot.size() > 100) {
                    onResult(
                        Result.failure(
                            IllegalStateException(
                                "This setup currently supports up to 100 saved contacts."
                            )
                        )
                    )
                    return@addOnSuccessListener
                }

                db.runTransaction { transaction ->
                    // Read before performing any writes.
                    val existingSettings = transaction.get(settingsRef)

                    // Preserve the separate preference for receiving in-app alerts.
                    // Saving outbound contacts must not silently enable receiving.
                    val receiveInApp =
                        existingSettings.getBoolean("receiveInApp") ?: false

                    snapshot.documents.forEach { contact ->
                        val selected = contact.id in selectedContactIds

                        val updates = mutableMapOf<String, Any>(
                            "emergencyContact" to selected
                        )

                        if (!selected) {
                            updates["emergencyEmailOptIn"] = false
                            updates["emergencyInAppOptIn"] = false
                            updates["emergencySmsRecipient"] = false
                        }

                        // update() fails if a contact was deleted in the meantime.
                        // The transaction then saves none of these changes.
                        transaction.update(contact.reference, updates)
                    }

                    transaction.set(
                        settingsRef,
                        mapOf(
                            "message" to cleanMessage,
                            "receiveInApp" to receiveInApp
                        ),
                        SetOptions.merge()
                    )

                    Unit
                }
                    .addOnSuccessListener {
                        onResult(Result.success(Unit))
                    }
                    .addOnFailureListener {
                        onResult(Result.failure(it))
                    }
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    fun setContactEmergencyInApp(
        uid: String,
        contactId: String,
        enabled: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (
            uid.isBlank() ||
            FirebaseAuth.getInstance().currentUser?.uid != uid ||
            contactId.isBlank() ||
            contactId.contains("/")
        ) {
            onResult(
                Result.failure(
                    IllegalArgumentException("A signed-in account and contact are required.")
                )
            )
            return
        }

        val contactRef = contactsCollection(uid).document(contactId)

        db.runTransaction { transaction ->
            val contact = transaction.get(contactRef)

            if (!contact.exists()) {
                throw IllegalStateException(
                    "This contact no longer exists. Reload your contacts."
                )
            }

            if (enabled) {
                val linkedUid = contact.getString("linkedUserId")

                if (contact.getBoolean("emergencyContact") != true) {
                    throw IllegalStateException(
                        "Save this contact as an emergency contact first."
                    )
                }

                if (
                    linkedUid.isNullOrBlank() ||
                    linkedUid == uid ||
                    linkedUid.contains("/")
                ) {
                    throw IllegalStateException(
                        "This contact needs a linked account belonging to another person."
                    )
                }
            }

            transaction.update(
                contactRef,
                "emergencyInAppOptIn",
                enabled
            )

            Unit
        }
            .addOnSuccessListener {
                onResult(Result.success(Unit))
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    fun setReceiveEmergencyInApp(
        uid: String,
        enabled: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (
            uid.isBlank() ||
            FirebaseAuth.getInstance().currentUser?.uid != uid
        ) {
            onResult(
                Result.failure(
                    IllegalStateException("Please sign in again.")
                )
            )
            return
        }

        val settingsRef = db.collection("users")
            .document(uid)
            .collection("emergencySettings")
            .document("preset")

        db.runTransaction { transaction ->
            val settings = transaction.get(settingsRef)

            if (settings.exists()) {
                // Change only the receiving preference.
                // Preserve the user's existing outgoing help message.
                transaction.update(
                    settingsRef,
                    "receiveInApp",
                    enabled
                )
            } else {
                transaction.set(
                    settingsRef,
                    mapOf(
                        "message" to "Please check on me. I need help.",
                        "receiveInApp" to enabled
                    )
                )
            }

            Unit
        }
            .addOnSuccessListener {
                onResult(Result.success(Unit))
            }
            .addOnFailureListener {
                onResult(Result.failure(it))
            }
    }

    // Used only by the redesigned contact editor. Existing save methods and
    // emergency setup callers retain their existing behavior.
    fun saveContactFromEditor(
        uid: String,
        contact: TrustedContact,
        original: TrustedContact?,
        onResult: (Result<Unit>) -> Unit
    ) {
        if (FirebaseAuth.getInstance().currentUser?.uid != uid ||
            uid.isBlank() || contact.id.isBlank() || contact.id.contains("/")) {
            onResult(Result.failure(IllegalArgumentException("Please sign in again.")))
            return
        }
        val ref = contactsCollection(uid).document(contact.id)
        db.runTransaction { transaction ->
            val current = transaction.get(ref)
            fun matches(candidate: TrustedContact): Boolean {
                val categories = (current.get("categories") as? List<*>)
                    ?.filterIsInstance<String>() ?: listOf("TRUSTED")
                return (current.getString("name") ?: "") == candidate.name &&
                        (current.getString("phoneNumber") ?: "") == candidate.phoneNumber &&
                        (current.getString("email") ?: "") == candidate.email &&
                        (current.getString("relationship") ?: "") == candidate.relationship &&
                        (current.getString("privateNotes") ?: "") == candidate.privateNotes &&
                        (current.getBoolean("accessGiven") ?: false) == candidate.accessGiven &&
                        (current.getBoolean("safeToStay") ?: false) == candidate.safeToStay &&
                        (current.getBoolean("emergencyContact") ?: false) == candidate.emergencyContact &&
                        categories.toSet() == candidate.categories.toSet()
            }
            if (original != null) {
                check(current.exists()) { "This contact was deleted. Reopen the contact list." }
                check(matches(original)) { "This contact changed elsewhere. Reopen it before editing." }
            } else if (current.exists()) {
                check(matches(contact)) { "A previous save completed. Reopen the contact list." }
                return@runTransaction Unit
            }
            val fields = hashMapOf<String, Any>(
                "name" to contact.name,
                "phoneNumber" to contact.phoneNumber,
                "email" to contact.email,
                "relationship" to contact.relationship,
                "privateNotes" to contact.privateNotes,
                "accessGiven" to contact.accessGiven,
                "safeToStay" to contact.safeToStay,
                "categories" to contact.categories,
                "emergencyContact" to contact.emergencyContact,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!contact.emergencyContact) {
                fields["emergencyInAppOptIn"] = false
                fields["emergencyEmailOptIn"] = false
                fields["emergencySmsRecipient"] = false
            }
            if (current.exists()) {
                if (contact.email != (current.getString("email") ?: "")) {
                    fields["emergencyEmailOptIn"] = false
                }
                if (contact.phoneNumber != (current.getString("phoneNumber") ?: "")) {
                    fields["emergencySmsRecipient"] = false
                }
                // Never overwrite linkedUserId or independent opt-ins from an old UI copy.
                transaction.update(ref, fields)
            } else {
                fields["createdAt"] = FieldValue.serverTimestamp()
                fields["emergencyInAppOptIn"] = false
                fields["emergencyEmailOptIn"] = false
                fields["emergencySmsRecipient"] = false
                transaction.set(ref, fields)
            }
            Unit
        }.addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }
}
