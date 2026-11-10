package com.family.tracker.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.family.tracker.databinding.FragmentChildPrayersMainBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.family.tracker.utils.AutoBackupHelper

class ChildPrayersMainFragment : Fragment() {
    private var _binding: FragmentChildPrayersMainBinding? = null
    private val binding get() = _binding!!
    private var userId: Long = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChildPrayersMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userId = arguments?.getLong("USER_ID") ?: -1

        val adapter = PrayerPagerAdapter(this, userId)
        binding.viewPagerPrayers.adapter = adapter

        TabLayoutMediator(binding.tabLayoutPrayers, binding.viewPagerPrayers) { tab, position ->
            tab.text = when (position) {
                0 -> "Prayers"
                else -> "History"
            }
        }.attach()
    }

    inner class PrayerPagerAdapter(fragment: Fragment, private val userId: Long) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2
        override fun createFragment(position: Int): Fragment {
            return if (position == 0) {
                PrayersListFragment().apply { arguments = Bundle().apply { putLong("USER_ID", userId) } }
            } else {
                PrayerHistoryFragment().apply { arguments = Bundle().apply { putLong("CHILD_ID", userId) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}