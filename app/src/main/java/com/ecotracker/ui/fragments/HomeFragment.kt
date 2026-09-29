package com.ecotracker.ui.fragments

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.ecotracker.R
import com.ecotracker.data.EcoState
import com.ecotracker.data.LocalEcoRepository
import com.ecotracker.data.LoggableActivity
import com.ecotracker.databinding.FragmentHomeBinding
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: LocalEcoRepository

    private val loggableActivities = listOf(
        LoggableActivity("Used a reusable water bottle", points = 5, co2Kg = 0.2, waterLitres = 5),
        LoggableActivity("Walked or cycled instead of driving", points = 8, co2Kg = 0.6, waterLitres = 0),
        LoggableActivity("Carried a reusable cloth bag", points = 4, co2Kg = 0.1, waterLitres = 0),
        LoggableActivity("Switched off unused lights", points = 3, co2Kg = 0.15, waterLitres = 0),
        LoggableActivity("Took a short shower to save water", points = 6, co2Kg = 0.0, waterLitres = 40)
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = LocalEcoRepository(requireContext())

        setupClickListeners()

        val userProfile = repository.getUserProfile()
        val userName = userProfile?.name?.ifBlank { "Eco Friend" } ?: "Eco Friend"
        binding.tvGreeting.text = getString(R.string.greeting_format, userName)

        render(repository.getState(), animateScore = true)
        animateContentEntrance()
    }

    private fun render(state: EcoState, animateScore: Boolean) {
        binding.tvEcoScoreValue.text = state.ecoScore.toString()
        binding.tvEcoScoreStatus.text = statusLabelFor(state.ecoScore)
        binding.tvEcoScoreDelta.text =
            getString(R.string.eco_score_delta_format, state.scoreDeltaThisWeek)
        binding.ecoScoreView.setScore(state.ecoScore, state.ecoScoreMax, animate = animateScore)

        binding.statCardActivities.setValue(state.weeklyActivities.toString())
        binding.statCardCo2.setValue(String.format(Locale.getDefault(), "%.1f kg", state.co2SavedKg))
        binding.statCardWater.setValue(String.format(Locale.getDefault(), "%,d L", state.waterSavedLitres))

        binding.tvStreakValue.text = state.streakDays.toString()

        renderChallenge(state.challengeJoined)
        renderProgress(state.progressCompleted, state.progressTotal, animate = animateScore)
    }

    private fun statusLabelFor(score: Int): String = when {
        score >= 80 -> getString(R.string.eco_score_status_excellent)
        score >= 60 -> getString(R.string.eco_score_status_great)
        else -> getString(R.string.eco_score_status_good)
    }

    private fun renderChallenge(joined: Boolean) {
        if (joined) {
            binding.btnJoinChallenge.text = getString(R.string.challenge_joined_label)
            binding.btnJoinChallenge.isEnabled = false
            binding.btnJoinChallenge.alpha = 0.9f
        } else {
            binding.btnJoinChallenge.text = getString(R.string.challenge_join)
            binding.btnJoinChallenge.isEnabled = true
            binding.btnJoinChallenge.alpha = 1f
        }
    }

    private fun renderProgress(completed: Int, total: Int, animate: Boolean) {
        val percent = if (total == 0) 0 else (completed * 100f / total).toInt()

        binding.tvProgressActivities.text =
            getString(R.string.progress_activities_format, completed, total)
        binding.tvProgressPercent.text = getString(R.string.progress_percent_format, percent)
        binding.tvProgressCaption.text = if (completed >= total) {
            getString(R.string.progress_caption_complete)
        } else {
            getString(R.string.progress_caption_ontrack)
        }

        if (animate) {
            ObjectAnimator.ofInt(binding.progressToday, "progress", binding.progressToday.progress, percent)
                .apply {
                    duration = 700L
                    interpolator = DecelerateInterpolator()
                    start()
                }
        } else {
            binding.progressToday.progress = percent
        }
    }

    private fun setupClickListeners() {
        binding.btnNotifications.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.cd_notifications), Toast.LENGTH_SHORT).show()
        }

        binding.btnJoinChallenge.setOnClickListener { view ->
            view.animate()
                .scaleX(0.95f).scaleY(0.95f)
                .setDuration(80)
                .withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
                .start()

            val updated = repository.joinChallenge()
            render(updated, animateScore = true)
            Toast.makeText(requireContext(), getString(R.string.challenge_joined_toast), Toast.LENGTH_SHORT).show()
        }

        binding.btnLogActivity.setOnClickListener { showLogActivityDialog() }
    }

    private fun showLogActivityDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_log_activity, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.btnCloseDialog)?.setOnClickListener { dialog.dismiss() }

        val items = listOf(
            Pair(dialogView.findViewById<View>(R.id.itemBottle), loggableActivities[0]),
            Pair(dialogView.findViewById<View>(R.id.itemWalk), loggableActivities[1]),
            Pair(dialogView.findViewById<View>(R.id.itemBag), loggableActivities[2]),
            Pair(dialogView.findViewById<View>(R.id.itemLights), loggableActivities[3]),
            Pair(dialogView.findViewById<View>(R.id.itemShower), loggableActivities[4]),
            Pair(dialogView.findViewById<View>(R.id.itemCompost), LoggableActivity("Composted organic food waste", points = 7, co2Kg = 0.4, waterLitres = 0))
        )

        for ((view, activity) in items) {
            view?.setOnClickListener {
                val updated = repository.logActivity(activity)
                render(updated, animateScore = true)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.log_activity_toast_format, activity.label, activity.points),
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
        }

        val btnLogCustom = dialogView.findViewById<android.widget.Button>(R.id.btnLogCustomAction)
        val etCustom = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCustomAction)

        btnLogCustom?.setOnClickListener {
            val text = etCustom?.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                val customActivity = LoggableActivity(text, points = 5, co2Kg = 0.2, waterLitres = 5)
                val updated = repository.logActivity(customActivity)
                render(updated, animateScore = true)
                Toast.makeText(
                    requireContext(),
                    "Logged '$text' • +5 points 🌱",
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            } else {
                etCustom?.error = "Please enter an activity name"
            }
        }

        dialog.show()
    }

    private fun animateContentEntrance() {
        binding.scrollContent.alpha = 0f
        binding.scrollContent.translationY = 24f
        binding.scrollContent.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
