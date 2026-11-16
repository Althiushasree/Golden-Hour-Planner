package com.example.viewmodel

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AuthActionResult
import com.example.auth.AuthManager
import com.example.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    data object Checking : AuthUiState()
    data object SignedOut : AuthUiState()
    data object Loading : AuthUiState()
    data class Authenticated(val user: UserProfile) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(
    private val authManager: AuthManager = AuthManager()
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(
        authManager.currentUserProfile()?.let { AuthUiState.Authenticated(it) }
            ?: AuthUiState.Checking
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var autoSignInAttempted = false

    fun checkExistingOrAutoSignIn(context: Context) {
        val existing = authManager.currentUserProfile()
        if (existing != null) {
            _uiState.value = AuthUiState.Authenticated(existing)
            return
        }
        if (autoSignInAttempted) {
            _uiState.value = AuthUiState.SignedOut
            return
        }
        autoSignInAttempted = true
        viewModelScope.launch {
            _uiState.value = AuthUiState.Checking
            when (val result = authManager.attemptAutoSignIn(context)) {
                is AuthActionResult.Success -> _uiState.value = AuthUiState.Authenticated(result.user)
                else -> _uiState.value = AuthUiState.SignedOut
            }
        }
    }

    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = authManager.signInWithGoogle(activity)) {
                is AuthActionResult.Success -> _uiState.value = AuthUiState.Authenticated(result.user)
                is AuthActionResult.Cancelled -> _uiState.value = AuthUiState.SignedOut
                is AuthActionResult.NoSavedCredential -> _uiState.value = AuthUiState.SignedOut
                is AuthActionResult.Error -> _uiState.value = AuthUiState.Error(result.message)
            }
        }
    }

    fun signOut(context: Context, onComplete: () -> Unit) {
        viewModelScope.launch {
            authManager.signOut(context)
            _uiState.value = AuthUiState.SignedOut
            onComplete()
        }
    }
}
