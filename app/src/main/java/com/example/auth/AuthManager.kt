package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.model.UserProfile
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class AuthActionResult {
    data class Success(val user: UserProfile) : AuthActionResult()
    data object Cancelled : AuthActionResult()
    data object NoSavedCredential : AuthActionResult()
    data class Error(val message: String) : AuthActionResult()
}

class AuthManager(
    private val auth: FirebaseAuth = Firebase.auth
) {

    fun currentUser(): FirebaseUser? = auth.currentUser

    fun currentUserProfile(): UserProfile? = auth.currentUser?.toUserProfile()

    fun observeAuthState(): Flow<UserProfile?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toUserProfile())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun attemptAutoSignIn(context: Context): AuthActionResult {
        auth.currentUser?.let {
            return AuthActionResult.Success(it.toUserProfile())
        }

        val clientId = resolveWebClientId(context)
            ?: return AuthActionResult.NoSavedCredential

        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user?.toUserProfile()
                    ?: return AuthActionResult.Error(context.getString(R.string.error_auth_no_user))
                AuthActionResult.Success(user)
            } else {
                AuthActionResult.NoSavedCredential
            }
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No authorized Google credential available for auto sign-in")
            AuthActionResult.NoSavedCredential
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Auto sign-in cancelled: ${e.message}", e)
            AuthActionResult.NoSavedCredential
        } catch (e: Exception) {
            Log.w(TAG, "Auto sign-in failed: ${e.message}", e)
            AuthActionResult.NoSavedCredential
        }
    }

    suspend fun signInWithGoogle(activity: Activity): AuthActionResult {
        val clientId = resolveWebClientId(activity)
            ?: return AuthActionResult.Error(activity.getString(R.string.error_auth_missing_client_id))

        val credentialManager = CredentialManager.create(activity)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user?.toUserProfile()
                    ?: return AuthActionResult.Error(activity.getString(R.string.error_auth_no_user))
                AuthActionResult.Success(user)
            } else {
                AuthActionResult.Error(activity.getString(R.string.error_auth_unexpected_credential))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            AuthActionResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google credential available: ${e.message}", e)
            AuthActionResult.Error(activity.getString(R.string.error_auth_no_google_account))
        } catch (e: GetCredentialProviderConfigurationException) {
            Log.e(TAG, "Credential Manager provider unavailable: ${e.message}", e)
            AuthActionResult.Error(activity.getString(R.string.error_auth_provider_unavailable))
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            AuthActionResult.Error(e.localizedMessage ?: activity.getString(R.string.error_auth_failed))
        }
    }

    suspend fun signOut(context: Context) {
        val credentialManager = CredentialManager.create(context)
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }

    private fun resolveWebClientId(context: Context): String? {
        return try {
            val value = context.getString(R.string.default_web_client_id)
            value.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "default_web_client_id resource not found; check google-services.json", e)
            null
        }
    }

    private fun FirebaseUser.toUserProfile(): UserProfile {
        val name = displayName?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore('@')
            ?: "Photographer"
        return UserProfile(
            uid = uid,
            displayName = name,
            email = email,
            photoUrl = photoUrl?.toString()
        )
    }

    companion object {
        private const val TAG = "AuthManager"
    }
}
