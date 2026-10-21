package com.ecotracker.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ecotracker.data.LocalEcoRepository
import com.ecotracker.databinding.FragmentReportsBinding
import java.util.Locale

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: LocalEcoRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = LocalEcoRepository(requireContext())

        val state = repository.getState()
        binding.tvTotalCo2Saved.text = String.format(Locale.getDefault(), "%.1f kg", state.co2SavedKg)
        binding.tvTotalWaterSaved.text = String.format(Locale.getDefault(), "%,d L", state.waterSavedLitres)
        binding.tvTotalActivitiesLogged.text = state.weeklyActivities.toString()
    }

    override fun onResume() {
        super.onResume()
        val state = repository.getState()
        binding.tvTotalCo2Saved.text = String.format(Locale.getDefault(), "%.1f kg", state.co2SavedKg)
        binding.tvTotalWaterSaved.text = String.format(Locale.getDefault(), "%,d L", state.waterSavedLitres)
        binding.tvTotalActivitiesLogged.text = state.weeklyActivities.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
