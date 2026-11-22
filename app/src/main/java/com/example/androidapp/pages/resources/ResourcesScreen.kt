package com.example.androidapp.pages.resources

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import java.net.URI
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidapp.security.trackPrivacyInteraction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges

private const val ALL = "All"

private val regions = linkedMapOf(
    "CA-AB" to "Alberta",
    "CA-BC" to "British Columbia"
)

private val needs = linkedMapOf(
    ALL to "All resources",
    "GENERAL" to "Find local services",
    "HOUSING" to "Somewhere to stay",
    "CONVERSATION" to "Someone to talk to",
    "ESSENTIALS" to "Food and essentials"
)

private val methods = linkedMapOf(
    ALL to "Any contact method",
    "PHONE" to "Phone",
    "TEXT" to "Text-based contact",
    "IN_PERSON" to "In person",
    "WEBSITE" to "Website / online directory"
)

private data class SupportResource(
    val id: String,
    val name: String,
    val need: String,
    val summary: String,
    val contactMethods: List<String>,
    val exampleProcess: String,
    val isDemo: Boolean,
    val websiteUrl: String?,
    val phoneNumber: String?,
    val hoursNote: String?,
    val lastReviewed: String?
)

// Optional contact fields are validated independently: a bad contact value
// hides that action without hiding an otherwise valid listing.
private fun validWebsite(value: String?): String? {
    val cleaned = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        val uri = URI(cleaned)
        cleaned.takeIf {
            uri.scheme.equals("https", ignoreCase = true) &&
                    !uri.host.isNullOrBlank() &&
                    uri.rawUserInfo == null &&
                    (uri.port == -1 || uri.port == 443)
        }
    }.getOrNull()
}

private fun validPhone(value: String?): String? {
    val cleaned = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (!cleaned.matches(Regex("[+0-9() .-]+"))) return null
    val normalized = cleaned.filter { it in '0'..'9' || it == '+' }
    return normalized.takeIf { it.matches(Regex("[+]?[0-9]{3,15}")) }
}

// Reject malformed records instead of displaying misleading details.
private fun parseResource(
    document: DocumentSnapshot
): SupportResource? {
    val data = document.data ?: return null

    fun text(field: String): String? =
        (data[field] as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    if (data["published"] != true) return null

    val name = text("name") ?: return null
    val need = text("need") ?: return null
    val summary = text("summary") ?: return null
    val process = text("exampleProcess") ?: return null
    val isDemo = data["isDemo"] as? Boolean ?: return null

    if (need == ALL || need !in needs) return null

    val rawMethods = data["contactMethods"] as? List<*>
        ?: return null

    if (rawMethods.isEmpty()) return null

    val contactMethods = rawMethods.map { value ->
        val method = value as? String ?: return null
        if (method == ALL || method !in methods) return null
        method
    }.distinct()

    return SupportResource(
        id = document.id,
        name = name,
        need = need,
        summary = summary,
        contactMethods = contactMethods,
        exampleProcess = process,
        isDemo = isDemo,
        websiteUrl = validWebsite(text("websiteUrl")),
        phoneNumber = validPhone(text("phoneNumber")),
        hoursNote = text("hoursNote"),
        lastReviewed = text("lastReviewed")
    )
}

@Composable
fun ResourcesScreen(
    onBack: () -> Unit,
    onQuickExit: () -> Unit,
    onGuidanceClick: () -> Unit = {}
) {
    val auth = remember { FirebaseAuth.getInstance() }

    var uid by remember {
        mutableStateOf(auth.currentUser?.uid)
    }

    var regionId by remember {
        mutableStateOf<String?>(null)
    }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            uid = it.currentUser?.uid
        }

        auth.addAuthStateListener(listener)

        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    // Changing accounts or regions disposes the previous region's state.
    key(uid, regionId) {
        RegionResourcesContent(
            uid = uid,
            regionId = regionId,
            onRegionSelected = { regionId = it },
            onBack = onBack,
            onGuidanceClick = onGuidanceClick,
            onQuickExit = {
                regionId = null
                onQuickExit()
            }
        )
    }
}

@Composable
private fun RegionResourcesContent(
    uid: String?,
    regionId: String?,
    onRegionSelected: (String) -> Unit,
    onBack: () -> Unit,
    onQuickExit: () -> Unit,
    onGuidanceClick: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }

    var resources by remember {
        mutableStateOf<List<SupportResource>>(emptyList())
    }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var skippedRecords by remember { mutableStateOf(0) }
    var retry by remember { mutableStateOf(0) }

    var selectedNeed by remember { mutableStateOf(ALL) }
    var selectedMethod by remember { mutableStateOf(ALL) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(uid, regionId, retry) {
        var disposed = false

        resources = emptyList()
        selectedId = null
        error = null
        skippedRecords = 0
        loading = uid != null && regionId != null

        val registration =
            if (uid != null && regionId != null) {
                db.collection("regionalResources")
                    .document(regionId)
                    .collection("resources")
                    .whereEqualTo("published", true)
                    .addSnapshotListener(
                        MetadataChanges.INCLUDE
                    ) { snapshot, exception ->
                        if (!disposed) {
                            when {
                                exception != null -> {
                                    resources = emptyList()
                                    selectedId = null
                                    loading = false
                                    error =
                                        "Could not load resources " +
                                                "(${exception.code.name}). " +
                                                "Check your connection and access, " +
                                                "then retry."
                                }

                                snapshot == null -> {
                                    resources = emptyList()
                                    selectedId = null
                                    loading = false
                                    error = "No response received. Please retry."
                                }

                                snapshot.metadata.isFromCache -> {
                                    // Avoid presenting stale publication
                                    // information as a current server result.
                                    resources = emptyList()
                                    selectedId = null
                                    loading = true
                                    error = null
                                }

                                else -> {
                                    val parsed = snapshot.documents
                                        .mapNotNull { parseResource(it) }
                                        .sortedBy { it.name.lowercase() }

                                    resources = parsed
                                    skippedRecords =
                                        snapshot.documents.size - parsed.size

                                    if (
                                        selectedId != null &&
                                        parsed.none { it.id == selectedId }
                                    ) {
                                        selectedId = null
                                    }

                                    loading = false
                                    error = null
                                }
                            }
                        }
                    }
            } else {
                null
            }

        onDispose {
            disposed = true
            registration?.remove()
        }
    }

    val selectedResource = resources.firstOrNull {
        it.id == selectedId
    }

    val filteredResources = resources.filter { resource ->
        (selectedNeed == ALL || resource.need == selectedNeed) &&
                (selectedMethod == ALL ||
                        selectedMethod in resource.contactMethods)
    }

    val resetFilters: () -> Unit = {
        selectedNeed = ALL
        selectedMethod = ALL
    }

    val handleBack: () -> Unit = {
        if (selectedId != null) {
            selectedId = null
        } else {
            onBack()
        }
    }

    BackHandler(onBack = handleBack)

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .trackPrivacyInteraction()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = handleBack) {
                    Text(
                        if (selectedResource == null) {
                            "Back"
                        } else {
                            "Back to results"
                        }
                    )
                }


            }

            if (selectedResource != null) {
                key(selectedResource.id) {
                    ResourceDetails(
                        resource = selectedResource,
                        regionName = regions[regionId].orEmpty(),
                        onQuickExit = onQuickExit,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Resources",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    OutlinedButton(
                        onClick = onGuidanceClick,
                        enabled = uid != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Emergency response guidance")
                    }

                    Text(
                        "Help that fits right now",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        "Choose your region, then browse resources " +
                                "or narrow them by your needs."
                    )

                    if (uid == null) {
                        Text("Sign in to browse regional resources.")
                    } else {
                        MySupportHotlinesSection(
                            uid = uid,
                            onQuickExit = onQuickExit
                        )

                        ResourceDropdown(
                            label = "Choose your region",
                            selected = regionId.orEmpty(),
                            options = regions,
                            placeholder = "Select a region",
                            onSelected = onRegionSelected
                        )

                        when {
                            regionId == null -> {
                                Text("Select a region to begin.")
                            }

                            error != null -> {
                                Text(error.orEmpty())

                                TextButton(onClick = { retry++ }) {
                                    Text("Retry")
                                }
                            }

                            loading -> {
                                CircularProgressIndicator()

                                Text(
                                    "Waiting for current resources. " +
                                            "An internet connection is required."
                                )

                                TextButton(onClick = { retry++ }) {
                                    Text("Retry")
                                }
                            }

                            else -> {
                                if (skippedRecords > 0) {
                                    Text(
                                        "Some listings could not be " +
                                                "displayed because their " +
                                                "information is incomplete."
                                    )
                                }

                                if (resources.isEmpty()) {
                                    Text(
                                        "No resource listings are available " +
                                                "here yet. This does not mean " +
                                                "support is unavailable in " +
                                                "your region."
                                    )
                                } else {
                                    if (resources.any { it.isDemo }) {
                                        Text(
                                            "Listings marked DEMO are " +
                                                    "fictional, not real services.",
                                            style = MaterialTheme.typography
                                                .labelLarge
                                        )
                                    }

                                    ResourceDropdown(
                                        label = "What do you need?",
                                        selected = selectedNeed,
                                        options = needs,
                                        onSelected = { selectedNeed = it }
                                    )

                                    ResourceDropdown(
                                        label = "How would you like to contact them?",
                                        selected = selectedMethod,
                                        options = methods,
                                        onSelected = { selectedMethod = it }
                                    )

                                    TextButton(onClick = resetFilters) {
                                        Text("Reset filters / Browse all")
                                    }

                                    Text(
                                        "${filteredResources.size} result(s)",
                                        style = MaterialTheme.typography
                                            .titleMedium
                                    )

                                    if (filteredResources.isEmpty()) {
                                        Text(
                                            "No listings match these filters. " +
                                                    "Try another contact method " +
                                                    "or browse all resources."
                                        )
                                    }

                                    filteredResources.forEach { resource ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                verticalArrangement =
                                                    Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (resource.isDemo) {
                                                    Text("DEMO — fictional resource")
                                                }

                                                Text(
                                                    resource.name,
                                                    style = MaterialTheme.typography
                                                        .titleMedium
                                                )

                                                Text(
                                                    needs[resource.need].orEmpty()
                                                )

                                                Text(resource.summary)

                                                Text(
                                                    "Contact options: " +
                                                            resource.contactMethods
                                                                .joinToString {
                                                                    methods[it].orEmpty()
                                                                }
                                                )

                                                Text(
                                                    "Why this matches",
                                                    style = MaterialTheme.typography
                                                        .labelLarge
                                                )

                                                Text(
                                                    buildString {
                                                        append(
                                                            "Listed for " +
                                                                    regions[regionId] + "."
                                                        )
                                                        if (selectedNeed != ALL) {
                                                            append(
                                                                " Matches your need."
                                                            )
                                                        }
                                                        if (selectedMethod != ALL) {
                                                            append(
                                                                " Lists your selected " +
                                                                        "contact method."
                                                            )
                                                        }
                                                    }
                                                )

                                                TextButton(
                                                    onClick = {
                                                        selectedId = resource.id
                                                    }
                                                ) {
                                                    Text("View details")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourceDropdown(
    label: String,
    selected: String,
    options: Map<String, String>,
    placeholder: String = "Select",
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(options[selected] ?: placeholder)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (id == selected) "✓ $name" else name
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelected(id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ResourceDetails(
    resource: SupportResource,
    regionName: String,
    onQuickExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initialMessage = when (resource.need) {
        "GENERAL" ->
            "Hi, I'm looking for local support services. Could you help " +
                    "me find options in my community and explain how to access them?"

        "HOUSING" ->
            "Hi, I'm looking for information about temporary " +
                    "accommodation. Could you explain your options " +
                    "and how to access them?"

        "CONVERSATION" ->
            "Hi, I'd like to speak with someone about support " +
                    "options. Could you explain how your service works?"

        else ->
            "Hi, I'm looking for help accessing food or essential " +
                    "supplies. Could you explain what support is " +
                    "available and any requirements?"
    }

    var contactMessage by remember(resource.id) {
        mutableStateOf(initialMessage)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            resource.name,
            style = MaterialTheme.typography.headlineSmall
        )

        if (resource.isDemo) {
            Text(
                "FICTIONAL DEMO — not a real service.",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Text("$regionName · ${needs[resource.need]}")
        Text(resource.summary)

        Text(
            "Listed contact options: " +
                    resource.contactMethods.joinToString {
                        methods[it].orEmpty()
                    }
        )

        Text(
            if (resource.isDemo) {
                "What happens next? — example only"
            } else {
                "What happens next?"
            },
            style = MaterialTheme.typography.titleMedium
        )

        Text(resource.exampleProcess)

        if (!resource.isDemo) {
            resource.hoursNote?.let { Text("Service hours: $it") }
            resource.lastReviewed?.let {
                Text("Listing checked: $it. Details can change; confirm with the provider.")
            }
        }

        key(resource.websiteUrl, resource.phoneNumber, resource.isDemo, resource.contactMethods) {
            ResourceContactActions(resource, onQuickExit)
        }

        if (resource.isDemo) {
            Text(
                "Real hours, costs, eligibility and availability " +
                        "have not been verified for this fictional example."
            )
        }

        Text(
            "Questions you could ask",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            "• What support do you offer?\n" +
                    "• Who can use your service?\n" +
                    "• Is an appointment required?\n" +
                    "• Are there any costs?"
        )

        Text(
            "Help me take the first step",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            "Prepare what you would like to say. This page does not " +
                    "send or save your message. Edits clear when you " +
                    "leave these details."
        )

        OutlinedTextField(
            value = contactMessage,
            onValueChange = {
                if (it.length <= 1000) {
                    contactMessage = it
                }
            },
            label = { Text("Your opening message") },
            supportingText = {
                Text("${contactMessage.length}/1000 characters")
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 8
        )

        TextButton(
            onClick = {
                contactMessage = initialMessage
            }
        ) {
            Text("Reset message")
        }

        Text(
            "Your opening message stays on this page. It is not passed to the website or dialler.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// Confirmation stays inside the protected screen, avoiding a separate dialog
// window that could activate the existing focus-loss privacy guard.
@Composable
private fun ResourceContactActions(
    resource: SupportResource,
    onQuickExit: () -> Unit
) {
    if (resource.isDemo) return

    val context = LocalContext.current
    val website = resource.websiteUrl
    val phone = resource.phoneNumber?.takeIf { "PHONE" in resource.contactMethods }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var launchError by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Contact this service", style = MaterialTheme.typography.titleMedium)

        if (website == null && phone == null) {
            Text("No usable website or phone number has been added to this listing yet.")
        }

        website?.let { url ->
            Text(url)
            OutlinedButton(onClick = {
                pendingAction = "website"
                launchError = null
            }) {
                Text("Open website")
            }
        }

        phone?.let { number ->
            OutlinedButton(onClick = {
                pendingAction = "phone"
                launchError = null
            }) {
                Text("Call $number — open dialler")
            }
        }

        val action = pendingAction
        if (action != null) {
            val isWebsite = action == "website"
            val target = if (isWebsite) website else phone
            if (target != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (isWebsite) "Open this website?" else "Open the phone dialler?",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(target)
                        Text(
                            "This opens another app. D.E.A.L.S. will return to WeatherNow " +
                                    "and clear your draft message. Its privacy protections " +
                                    "do not cover the other app."
                        )
                        Text(
                            if (isWebsite) {
                                "The website may appear in browser history."
                            } else {
                                "No call is placed automatically. If you place a call, " +
                                        "it may appear in call history or phone records."
                            }
                        )
                        Button(onClick = {
                            // Revalidate at launch, and never pass the user's draft.
                            val destination = if (isWebsite) validWebsite(target) else validPhone(target)
                            if (destination == null) {
                                pendingAction = null
                                launchError = "This contact detail is not valid."
                            } else {
                                val intent = if (isWebsite) {
                                    Intent(Intent.ACTION_VIEW, Uri.parse(destination)).apply {
                                        addCategory(Intent.CATEGORY_BROWSABLE)
                                    }
                                } else {
                                    Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", destination, null))
                                }
                                pendingAction = null
                                val launched = try {
                                    context.startActivity(intent)
                                    true
                                } catch (_: ActivityNotFoundException) {
                                    launchError = "No app is available to open this contact option."
                                    false
                                } catch (_: SecurityException) {
                                    launchError = "This device could not open this contact option."
                                    false
                                }
                                if (launched) onQuickExit()
                            }
                        }) {
                            Text(if (isWebsite) "Continue to website" else "Continue to dialler")
                        }
                        TextButton(onClick = { pendingAction = null }) {
                            Text("Stay here")
                        }
                    }
                }
            }
        }

        launchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
