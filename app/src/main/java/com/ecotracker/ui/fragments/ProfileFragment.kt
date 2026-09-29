package com.ecotracker.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.ecotracker.R
import com.ecotracker.data.LocalEcoRepository
import com.ecotracker.databinding.FragmentProfileBinding
import com.ecotracker.ui.auth.LoginActivity

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: LocalEcoRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = LocalEcoRepository(requireContext())

        val profile = repository.getUserProfile()
        val state = repository.getState()

        if (profile != null) {
            binding.tvUserName.text = profile.name
            binding.tvUserEmail.text = profile.email
            binding.tvMemberSince.text = getString(R.string.profile_member_since, profile.joinDate)
        }

        binding.tvProfileScore.text = state.ecoScore.toString()
        binding.tvProfileStreak.text = "${state.streakDays} Days"

        // Dynamic XP progress based on eco score
        val xpCurrent = (state.ecoScore * 10).coerceAtMost(1000)
        binding.progressLevelXp.progress = xpCurrent
        binding.tvXpProgressText.text = getString(R.string.profile_xp_format, xpCurrent, 1000)

        setupSettingsListeners()

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun setupSettingsListeners() {
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            val message = if (isChecked) "Daily eco reminders enabled 🔔" else "Notifications disabled"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            val message = if (isChecked) "Dark Mode preview enabled 🌙" else "System theme enabled ☀️"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        binding.rowPrivacy.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.setting_privacy)
                .setMessage("Your EcoTracker data is securely stored locally on your device. No personal data is shared with third parties.")
                .setPositiveButton("OK", null)
                .show()
        }

        binding.rowSupport.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.setting_support)
                .setMessage("Have questions or feedback? Contact us at support@ecotracker.org or visit our community forum.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.logout_confirmation_title)
            .setMessage(R.string.logout_confirmation_msg)
            .setPositiveButton(R.string.btn_logout) { _, _ ->
                repository.logout()
                val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                requireActivity().finish()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
