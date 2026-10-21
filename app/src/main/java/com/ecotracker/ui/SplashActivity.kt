package com.ecotracker.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.ecotracker.MainActivity
import com.ecotracker.data.LocalEcoRepository
import com.ecotracker.databinding.ActivitySplashBinding
import com.ecotracker.ui.auth.LoginActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var repository: LocalEcoRepository
    private var hasNavigated = false
    private val handler = Handler(Looper.getMainLooper())
    private val navigationRunnable = Runnable { navigateToNextScreen() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = LocalEcoRepository(applicationContext)

        startSplashAnimation()

        // Robust timer ensuring navigation occurs smoothly after 1200ms
        handler.postDelayed(navigationRunnable, 1200)
    }

    private fun startSplashAnimation() {
        // Initial states: slightly scaled down and hidden
        binding.layoutLogoContainer.alpha = 0f
        binding.layoutLogoContainer.scaleX = 0.9f
        binding.layoutLogoContainer.scaleY = 0.9f

        binding.tvSplashTitle.alpha = 0f
        binding.tvSplashTagline.alpha = 0f
        binding.tvSplashFooter.alpha = 0f

        // 1. Logo gently fades in and slightly scales to 100% (800ms)
        binding.layoutLogoContainer.animate()
            .alpha(1f)
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(800)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // 2. App name title fades in shortly after (starts at 300ms, duration 600ms)
        binding.tvSplashTitle.animate()
            .alpha(1f)
            .setStartDelay(300)
            .setDuration(600)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // 3. Tagline fades in
        binding.tvSplashTagline.animate()
            .alpha(1f)
            .setStartDelay(500)
            .setDuration(500)
            .start()

        // 4. Subtle footer fades in
        binding.tvSplashFooter.animate()
            .alpha(0.8f)
            .setStartDelay(650)
            .setDuration(500)
            .start()
    }

    private fun navigateToNextScreen() {
        if (hasNavigated || isFinishing || isDestroyed) return
        hasNavigated = true

        val nextIntent = if (repository.isLoggedIn()) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }

        startActivity(nextIntent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(navigationRunnable)
        super.onDestroy()
    }
}
