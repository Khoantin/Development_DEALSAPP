package com.example.androidapp.security

import android.app.Activity
import android.view.WindowManager

object PrivacyShieldManager {

    fun apply(
        activity: Activity,
        privacyLevel: PrivacyLevel
    ) {

        when (privacyLevel) {

            PrivacyLevel.PUBLIC -> {
                disable(activity)
            }

            PrivacyLevel.PROTECTED,
            PrivacyLevel.CRITICAL -> {
                enable(activity)
            }
        }
    }

    fun enable(activity: Activity) {
        activity.window.addFlags(
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    fun disable(activity: Activity) {
        activity.window.clearFlags(
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }
}