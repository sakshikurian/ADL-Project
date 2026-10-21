package com.ecotracker.ui.fragments

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.ecotracker.R
import com.ecotracker.data.LocalEcoRepository
import com.ecotracker.databinding.FragmentProfileBinding
import com.ecotracker.notifications.NotificationHelper
import com.ecotracker.notifications.ReminderScheduler
import com.ecotracker.ui.auth.LoginActivity
import java.util.Calendar

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: LocalEcoRepository
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                enableRemindersAndSchedule()
            } else {
                binding.switchNotifications.setChecked(false, notify = false)
                repository.setNotificationsEnabled(false)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.notification_permission_rationale),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

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

        setupNotificationRemindersSection()
        setupAppSettingsListeners()

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun setupNotificationRemindersSection() {
        // Initialize notification channel
        NotificationHelper.createNotificationChannel(requireContext())

        // Set initial toggle switch state
        val isEnabled = repository.areNotificationsEnabled()
        binding.switchNotifications.setChecked(isEnabled, notify = false)

        // Set initial reminder time display
        updateReminderTimeDisplay()

        // Toggle listener
        binding.switchNotifications.onCheckedChangeListener = { isChecked ->
            if (isChecked) {
                checkPermissionAndEnable()
            } else {
                disableReminders()
            }
        }

        // Reminder Time row click -> open TimePickerDialog
        binding.rowReminderTime.setOnClickListener {
            showTimePicker()
        }
    }

    private fun checkPermissionAndEnable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
                enableRemindersAndSchedule()
            } else {
                notificationPermissionLauncher.launch(permission)
            }
        } else {
            enableRemindersAndSchedule()
        }
    }

    private fun enableRemindersAndSchedule() {
        repository.setNotificationsEnabled(true)
        binding.switchNotifications.setChecked(true, notify = false)

        val (hour, minute) = repository.getReminderTime()
        ReminderScheduler.scheduleReminder(requireContext(), hour, minute)

        val formattedTime = formatTime(hour, minute)
        Toast.makeText(
            requireContext(),
            getString(R.string.toast_reminder_enabled, formattedTime),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun disableReminders() {
        repository.setNotificationsEnabled(false)
        binding.switchNotifications.setChecked(false, notify = false)
        ReminderScheduler.cancelReminder(requireContext())
        Toast.makeText(requireContext(), getString(R.string.toast_reminder_disabled), Toast.LENGTH_SHORT).show()
    }

    private fun showTimePicker() {
        val (currentHour, currentMinute) = repository.getReminderTime()
        val is24Hour = DateFormat.is24HourFormat(requireContext())

        val dialog = TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                repository.setReminderTime(selectedHour, selectedMinute)
                updateReminderTimeDisplay()

                // If reminders are enabled, reschedule immediately
                if (repository.areNotificationsEnabled()) {
                    ReminderScheduler.scheduleReminder(requireContext(), selectedHour, selectedMinute)
                }

                val formatted = formatTime(selectedHour, selectedMinute)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_reminder_updated, formatted),
                    Toast.LENGTH_SHORT
                ).show()
            },
            currentHour,
            currentMinute,
            is24Hour
        )
        dialog.show()
    }

    private fun updateReminderTimeDisplay() {
        val (hour, minute) = repository.getReminderTime()
        binding.tvReminderTimeValue.text = formatTime(hour, minute)
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val timeFormat = DateFormat.getTimeFormat(requireContext())
        return timeFormat.format(calendar.time)
    }

    private fun setupAppSettingsListeners() {
        val isDark = com.ecotracker.data.ThemeManager.isDarkModeEnabled(requireContext())
        binding.switchDarkMode.setOnCheckedChangeListener(null)
        binding.switchDarkMode.isChecked = isDark
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            com.ecotracker.data.ThemeManager.setDarkModeEnabled(requireContext(), isChecked)
            val message = if (isChecked) "Dark Mode enabled 🌙" else "Light Mode enabled ☀️"
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        binding.rowPrivacy.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.setting_privacy)
                .setMessage("Your EcoTracker data is stored securely on your device and synchronized to your private cloud storage when signed in.")
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

    override fun onResume() {
        super.onResume()
        val state = repository.getState()
        binding.tvProfileScore.text = state.ecoScore.toString()
        binding.tvProfileStreak.text = "${state.streakDays} Days"
        val xpCurrent = (state.ecoScore * 10).coerceAtMost(1000)
        binding.progressLevelXp.progress = xpCurrent
        binding.tvXpProgressText.text = getString(R.string.profile_xp_format, xpCurrent, 1000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
