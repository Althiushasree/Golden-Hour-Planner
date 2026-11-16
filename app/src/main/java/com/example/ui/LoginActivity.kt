package com.example.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.R
import com.example.databinding.ActivityLoginBinding
import com.example.viewmodel.AuthUiState
import com.example.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.loginRoot) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnGoogleSignIn.setOnClickListener {
            authViewModel.signInWithGoogle(this)
        }

        observeAuthState()
        authViewModel.checkExistingOrAutoSignIn(this)
    }

    private fun observeAuthState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.uiState.collect { state ->
                    when (state) {
                        is AuthUiState.Checking -> {
                            binding.layoutAuthProgress.visibility = View.VISIBLE
                            binding.tvAuthProgress.setText(R.string.login_checking_session)
                            binding.btnGoogleSignIn.isEnabled = false
                            binding.cardAuthError.visibility = View.GONE
                        }
                        is AuthUiState.Loading -> {
                            binding.layoutAuthProgress.visibility = View.VISIBLE
                            binding.tvAuthProgress.setText(R.string.login_signing_in)
                            binding.btnGoogleSignIn.isEnabled = false
                            binding.cardAuthError.visibility = View.GONE
                        }
                        is AuthUiState.SignedOut -> {
                            binding.layoutAuthProgress.visibility = View.GONE
                            binding.btnGoogleSignIn.isEnabled = true
                            binding.cardAuthError.visibility = View.GONE
                        }
                        is AuthUiState.Error -> {
                            binding.layoutAuthProgress.visibility = View.GONE
                            binding.btnGoogleSignIn.isEnabled = true
                            binding.cardAuthError.visibility = View.VISIBLE
                            binding.tvAuthError.text = state.message
                        }
                        is AuthUiState.Authenticated -> {
                            navigateToDashboard()
                        }
                    }
                }
            }
        }
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
