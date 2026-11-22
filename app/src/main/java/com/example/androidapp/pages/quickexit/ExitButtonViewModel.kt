package com.example.androidapp.pages.quickexit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.androidapp.repository.QuickExitRepository

class ExitButtonViewModel(
    private val quickExitRepository: QuickExitRepository = QuickExitRepository()
) : ViewModel() {

    var quickExitRequested by mutableStateOf(
        quickExitRepository.isQuickExitRequested()
    )
        private set

    fun onExitClicked() {
        quickExitRepository.requestQuickExit()
        quickExitRequested = true
    }

    fun clearQuickExitRequest() {
        quickExitRepository.resetQuickExitRequest()
        quickExitRequested = false
    }
}