package com.ecotracker.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.ecotracker.R
import com.ecotracker.data.CustomActivity
import com.ecotracker.data.LocalEcoRepository
import com.ecotracker.data.LoggableActivity
import com.ecotracker.databinding.FragmentActivitiesBinding
import com.ecotracker.databinding.ItemCustomActivityBinding

class ActivitiesFragment : Fragment() {

    private var _binding: FragmentActivitiesBinding? = null
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
        _binding = FragmentActivitiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = LocalEcoRepository(requireContext())

        binding.cardActivity1.setOnClickListener {
            logSingleActivity(loggableActivities[0], binding.tvStatus1)
        }

        binding.cardActivity2.setOnClickListener {
            logSingleActivity(loggableActivities[1], binding.tvStatus2)
        }

        binding.cardActivity3.setOnClickListener {
            logSingleActivity(loggableActivities[2], binding.tvStatus3)
        }

        binding.btnLogNewActivity.setOnClickListener {
            showLogActivityDialog()
        }

        binding.btnOpenEcoLens.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.ecotracker.ui.media.InteractiveMediaActivity::class.java))
        }

        binding.btnOpenMlPredictor.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), com.ecotracker.ui.ml.MlEcoImpactActivity::class.java))
        }

        renderCustomActivities()
    }

    private fun renderCustomActivities() {
        binding.containerCustomActivities.removeAllViews()
        val customList = repository.getCustomActivities()

        if (customList.isEmpty()) {
            binding.tvCustomHeader.visibility = View.GONE
        } else {
            binding.tvCustomHeader.visibility = View.VISIBLE
            for (custom in customList) {
                val itemBinding = ItemCustomActivityBinding.inflate(layoutInflater, binding.containerCustomActivities, false)
                itemBinding.tvCustomName.text = custom.name
                itemBinding.tvCustomDetails.text = "+${custom.points} Eco Points • Custom Action"

                // Edit Button Handler
                itemBinding.btnEditCustom.setOnClickListener {
                    showEditCustomDialog(custom)
                }

                // Delete Button Handler
                itemBinding.btnDeleteCustom.setOnClickListener {
                    showDeleteCustomDialog(custom)
                }

                binding.containerCustomActivities.addView(itemBinding.root)
            }
        }
    }

    private fun showEditCustomDialog(custom: CustomActivity) {
        val input = EditText(requireContext()).apply {
            setText(custom.name)
            setSelection(custom.name.length)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Custom Activity")
            .setMessage("Update the name of your eco action:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    repository.editCustomActivity(custom.id, newName)
                    renderCustomActivities()
                    Toast.makeText(requireContext(), "Activity updated! ✏️", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteCustomDialog(custom: CustomActivity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Activity?")
            .setMessage("Are you sure you want to delete '${custom.name}'? Points earned will be adjusted.")
            .setPositiveButton("Delete") { _, _ ->
                repository.deleteCustomActivity(custom.id)
                renderCustomActivities()
                Toast.makeText(requireContext(), "Activity deleted 🗑️", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logSingleActivity(activity: LoggableActivity, statusTextView: TextView) {
        repository.logActivity(activity)
        statusTextView.text = "Logged ✓"
        Toast.makeText(
            requireContext(),
            getString(R.string.log_activity_toast_format, activity.label, activity.points),
            Toast.LENGTH_SHORT
        ).show()
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
                repository.logActivity(activity)
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
                repository.addCustomActivity(text)
                renderCustomActivities()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
