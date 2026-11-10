package com.family.tracker

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.family.tracker.fragments.HabitsFragment
import com.family.tracker.fragments.HomeworkFragment
import com.family.tracker.fragments.PrayersListFragment
import com.family.tracker.fragments.TasksFragment

class ChildPagerAdapter(
    activity: FragmentActivity,
    private val userId: Long
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        // Put both keys just in case fragments are looking for one or the other
        val args = Bundle().apply {
            putLong("USER_ID", userId)
            putLong("CHILD_ID", userId)
        }
        return when (position) {
            0 -> TasksFragment().apply { arguments = args }
            1 -> HabitsFragment().apply { arguments = args }
            2 -> HomeworkFragment().apply { arguments = args }
            else -> PrayersListFragment().apply { arguments = args }
        }
    }
}