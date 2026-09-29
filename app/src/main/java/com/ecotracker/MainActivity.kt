package com.ecotracker

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.ecotracker.data.LocalEcoRepository
import com.ecotracker.databinding.ActivityMainBinding
import com.ecotracker.ui.auth.LoginActivity
import com.ecotracker.ui.fragments.ActivitiesFragment
import com.ecotracker.ui.fragments.ChallengesFragment
import com.ecotracker.ui.fragments.HomeFragment
import com.ecotracker.ui.fragments.ProfileFragment
import com.ecotracker.ui.fragments.ReportsFragment

/**
 * Main Activity hosting multi-tab fragment navigation for EcoTracker.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: LocalEcoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = LocalEcoRepository(applicationContext)

        // Check authentication state on launch
        if (!repository.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()
        setupBottomNavigation()

        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.bottomNav.setPadding(
                binding.bottomNav.paddingLeft,
                binding.bottomNav.paddingTop,
                binding.bottomNav.paddingRight,
                systemBars.bottom
            )
            view.onApplyWindowInsets(insets.toWindowInsets())
            insets
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            val targetFragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_activities -> ActivitiesFragment()
                R.id.nav_challenges -> ChallengesFragment()
                R.id.nav_reports -> ReportsFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> HomeFragment()
            }
            loadFragment(targetFragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
