package com.ecotracker.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ecotracker.R
import com.ecotracker.data.LocalEcoRepository
import com.ecotracker.databinding.FragmentChallengesBinding

class ChallengesFragment : Fragment() {

    private var _binding: FragmentChallengesBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: LocalEcoRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChallengesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = LocalEcoRepository(requireContext())

        val state = repository.getState()
        if (state.challengeJoined) {
            binding.btnJoinChallengeTab.text = getString(R.string.challenge_joined_label)
            binding.btnJoinChallengeTab.isEnabled = false
        } else {
            binding.btnJoinChallengeTab.setOnClickListener {
                repository.joinChallenge()
                binding.btnJoinChallengeTab.text = getString(R.string.challenge_joined_label)
                binding.btnJoinChallengeTab.isEnabled = false
                Toast.makeText(requireContext(), getString(R.string.challenge_joined_toast), Toast.LENGTH_SHORT).show()
            }
        }

        binding.cardChallengeZeroWaste.setOnClickListener {
            binding.tvStatusZeroWaste.text = "Joined ✓ • +50 Points"
            Toast.makeText(requireContext(), "Joined Zero Waste Weekend! 📦 +50 Points", Toast.LENGTH_SHORT).show()
        }

        binding.cardChallengeMeatless.setOnClickListener {
            binding.tvStatusMeatless.text = "Joined ✓ • +30 Points"
            Toast.makeText(requireContext(), "Joined Meatless Monday! 🥗 +30 Points", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
