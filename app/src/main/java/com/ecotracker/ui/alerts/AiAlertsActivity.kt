package com.ecotracker.ui.alerts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ecotracker.R
import com.ecotracker.notifications.AiAlertsManager
import com.ecotracker.ui.chatbot.ChatbotActivity
import com.ecotracker.ui.media.InteractiveMediaActivity
import com.ecotracker.ui.navigation.NavigationActivity
import com.google.android.material.button.MaterialButton

class AiAlertsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnTriggerInstantAlert: MaterialButton
    private lateinit var btnAlertLogAction: MaterialButton
    private lateinit var btnAlertOpenNav: MaterialButton
    private lateinit var btnAlertAskEcoBot: MaterialButton
    private lateinit var btnAlertOpenLens: MaterialButton

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                dispatchAiAlert()
            } else {
                Toast.makeText(
                    this,
                    "Notification permission denied. Enable it in App Settings to receive alerts.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_alerts)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnTriggerInstantAlert = findViewById(R.id.btnTriggerInstantAlert)
        btnAlertLogAction = findViewById(R.id.btnAlertLogAction)
        btnAlertOpenNav = findViewById(R.id.btnAlertOpenNav)
        btnAlertAskEcoBot = findViewById(R.id.btnAlertAskEcoBot)
        btnAlertOpenLens = findViewById(R.id.btnAlertOpenLens)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnTriggerInstantAlert.setOnClickListener {
            checkAndDispatchAlert()
        }

        btnAlertLogAction.setOnClickListener {
            finish()
        }

        btnAlertOpenNav.setOnClickListener {
            startActivity(Intent(this, NavigationActivity::class.java))
        }

        btnAlertAskEcoBot.setOnClickListener {
            startActivity(Intent(this, ChatbotActivity::class.java))
        }

        btnAlertOpenLens.setOnClickListener {
            startActivity(Intent(this, InteractiveMediaActivity::class.java))
        }
    }

    private fun checkAndDispatchAlert() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                dispatchAiAlert()
            } else {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            dispatchAiAlert()
        }
    }

    private fun dispatchAiAlert() {
        AiAlertsManager.evaluateAndNotify(this)
        Toast.makeText(this, "AI Alert dispatched to system notification drawer 🔔", Toast.LENGTH_SHORT).show()
    }
}
