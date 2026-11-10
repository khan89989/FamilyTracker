package com.family.tracker.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.family.tracker.databinding.FragmentParentPrayersBinding
import com.family.tracker.utils.AutoBackupHelper

class ParentPrayersFragment : Fragment() {
    private var _binding: FragmentParentPrayersBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParentPrayersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val childId = arguments?.getLong("CHILD_ID") ?: -1

        // Directly embed the History fragment
        val historyFragment = PrayerHistoryFragment().apply {
            arguments = Bundle().apply { putLong("CHILD_ID", childId) }
        }

        childFragmentManager.beginTransaction()
            .replace(binding.containerPrayers.id, historyFragment)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}