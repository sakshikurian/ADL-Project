package com.ecotracker.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.ecotracker.MainActivity
import com.ecotracker.R
import com.ecotracker.data.LocalEcoRepository
import com.ecotracker.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var repository: LocalEcoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = LocalEcoRepository(applicationContext)

        setupListeners()
    }

    private fun setupListeners() {
        // Clear inline errors as user types
        binding.etName.doAfterTextChanged {
            binding.tilName.error = null
        }

        binding.etEmail.doAfterTextChanged {
            binding.tilEmail.error = null
        }

        binding.etPassword.doAfterTextChanged {
            binding.tilPassword.error = null
        }

        binding.btnRegister.setOnClickListener {
            performRegister()
        }

        binding.tvLoginPrompt.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun performRegister() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()

        var isValid = true

        // 1. Name validation: 2 to 50 characters, letters, spaces, hyphens, and apostrophes
        if (name.isEmpty()) {
            binding.tilName.error = "Please enter your full name"
            isValid = false
        } else if (!NAME_REGEX.matches(name)) {
            binding.tilName.error = "Name must be 2-50 characters (letters and spaces only)"
            isValid = false
        } else {
            binding.tilName.error = null
        }

        // 2. Email validation: valid standard email address regex
        if (email.isEmpty()) {
            binding.tilEmail.error = "Please enter your email address"
            isValid = false
        } else if (!EMAIL_REGEX.matches(email)) {
            binding.tilEmail.error = "Please enter a valid email (e.g., student@campus.edu)"
            isValid = false
        } else {
            binding.tilEmail.error = null
        }

        // 3. Password validation: at least 6 characters, at least one letter and one number
        if (password.isEmpty()) {
            binding.tilPassword.error = "Please enter a password"
            isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = "Password must be at least 6 characters"
            isValid = false
        } else if (!PASSWORD_REGEX.matches(password)) {
            binding.tilPassword.error = "Password must include at least one letter and one number"
            isValid = false
        } else {
            binding.tilPassword.error = null
        }

        if (!isValid) return

        // Validate before Firebase calls; never log plaintext passwords
        binding.btnRegister.isEnabled = false
        repository.registerWithFirebase(name, email, password) { success, errorMsg ->
            binding.btnRegister.isEnabled = true
            if (success) {
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            } else {
                val message = errorMsg ?: "Registration failed. Please check your details and try again."
                if (message.contains("email", ignoreCase = true) && message.contains("already", ignoreCase = true)) {
                    binding.tilEmail.error = message
                } else {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        private val NAME_REGEX = Regex("^[a-zA-Z\\s'-]{2,50}$")
        private val EMAIL_REGEX = Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
        private val PASSWORD_REGEX = Regex("^(?=.*[A-Za-z])(?=.*\\d).{6,}$")
    }
}
