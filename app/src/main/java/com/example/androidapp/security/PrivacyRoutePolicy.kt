package com.example.androidapp.security

object PrivacyRoutePolicy {

    private val publicRoutes =
        setOf(
            "weather",
            "weatherContact"
        )

    private val criticalRoutes =
        setOf(
            "locationSharing",
            "incomingLocationSharing",
            "accountSettings",
            "messages",
            "accountSetup/personal",
            "accountSetup/documents",
            "accountSetup/contacts",
            "adminStaffMessages",
            "adminStaffThread/{supportUid}",
            "supportStaffThread"
        )

    fun levelFor(
        route: String?
    ): PrivacyLevel {

        if (route == null) {
            return PrivacyLevel.PROTECTED
        }

        if (route in publicRoutes) {
            return PrivacyLevel.PUBLIC
        }

        if (
            route in criticalRoutes ||
            route.startsWith("accountSetup/") ||
            route.startsWith("adminStaffThread/")
        ) {
            return PrivacyLevel.CRITICAL
        }

        return PrivacyLevel.PROTECTED
    }
}