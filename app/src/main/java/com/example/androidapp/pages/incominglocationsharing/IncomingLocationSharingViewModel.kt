package com.example.androidapp.pages.incominglocationsharing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.androidapp.data.AuthRepository
import com.example.androidapp.data.IncomingLocationShare
import com.example.androidapp.data.IncomingLocationSharingRepository
import com.google.firebase.firestore.ListenerRegistration


data class IncomingLocationSharingUiState(

    val shares: List<IncomingLocationShare> =
        emptyList(),

    val isLoading: Boolean =
        true,

    val errorMessage: String? =
        null
)


class IncomingLocationSharingViewModel :
    ViewModel() {


    private val authRepository =
        AuthRepository()


    private val repository =
        IncomingLocationSharingRepository()


    private var listenerRegistration:
            ListenerRegistration? = null


    var uiState by
    mutableStateOf(
        IncomingLocationSharingUiState()
    )
        private set


    init {
        startListening()
    }


    private fun startListening() {

        val uid =
            authRepository
                .currentUser
                ?.uid


        if (uid == null) {

            uiState =
                uiState.copy(
                    isLoading = false,
                    errorMessage =
                        "You must be signed in."
                )

            return
        }


        listenerRegistration =
            repository
                .listenForIncomingShares(
                    recipientUid = uid
                ) { result ->


                    result
                        .onSuccess { shares ->

                            uiState =
                                uiState.copy(
                                    shares =
                                        shares,

                                    isLoading =
                                        false,

                                    errorMessage =
                                        null
                                )
                        }
                        .onFailure { exception ->

                            uiState =
                                uiState.copy(
                                    isLoading =
                                        false,

                                    errorMessage =
                                        exception.message
                                            ?: "Unable to load incoming location shares."
                                )
                        }
                }
    }


    override fun onCleared() {

        listenerRegistration
            ?.remove()

        listenerRegistration =
            null

        super.onCleared()
    }
}