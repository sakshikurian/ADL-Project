package com.ecotracker.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.ecotracker.MainActivity
import com.ecotracker.R
import com.ecotracker.data.LocalEcoRepository
import com.ecotracker.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var repository: LocalEcoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = LocalEcoRepository(applicationContext)

        setupListeners()
    }

    private fun setupListeners() {
        // Clear inline errors as user types
        binding.etEmail.doAfterTextChanged {
            binding.tilEmail.error = null
        }

        binding.etPassword.doAfterTextChanged {
            binding.tilPassword.error = null
        }

        binding.btnLogin.setOnClickListener {
            performLogin()
        }

        binding.tvRegisterPrompt.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }

    private fun performLogin() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()

        var isValid = true

        // 1. Email validation
        if (email.isEmpty()) {
            binding.tilEmail.error = "Please enter your email address"
            isValid = false
        } else if (!EMAIL_REGEX.matches(email)) {
            binding.tilEmail.error = "Please enter a valid email (e.g., student@campus.edu)"
            isValid = false
        } else {
            binding.tilEmail.error = null
        }

        // 2. Password validation
        if (password.isEmpty()) {
            binding.tilPassword.error = "Please enter your password"
            isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = "Password must be at least 6 characters"
            isValid = false
        } else {
            binding.tilPassword.error = null
        }

        if (!isValid) return

        // Validate before Firebase calls; never log plaintext passwords
        binding.btnLogin.isEnabled = false
        repository.loginWithFirebase(email, password) { success, errorMsg ->
            binding.btnLogin.isEnabled = true
            if (success) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            } else {
                val message = errorMsg ?: "Invalid email or password. Please try again."
                if (message.contains("password", ignoreCase = true) || message.contains("credential", ignoreCase = true)) {
                    binding.tilPassword.error = message
                } else if (message.contains("user", ignoreCase = true) || message.contains("email", ignoreCase = true)) {
                    binding.tilEmail.error = message
                } else {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
    }
}
