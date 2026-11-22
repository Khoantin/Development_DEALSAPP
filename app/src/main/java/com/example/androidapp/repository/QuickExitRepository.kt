package com.example.androidapp.repository

class QuickExitRepository {

    private var quickExitRequested: Boolean = false

    fun requestQuickExit() {
        quickExitRequested = true
    }

    fun isQuickExitRequested(): Boolean {
        return quickExitRequested
    }

    fun resetQuickExitRequest() {
        quickExitRequested = false
    }
}