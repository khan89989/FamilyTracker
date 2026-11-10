package com.family.tracker

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.family.tracker.fragments.ParentHabitsListFragment
import com.family.tracker.fragments.ParentHomeworkFragment
import com.family.tracker.fragments.ParentTasksFragment
import com.family.tracker.fragments.PrayerHistoryFragment
import com.family.tracker.fragments.ParentStatusFragment // <-- THIS WAS MISSING

class ParentChildPagerAdapter(
    activity: FragmentActivity,
    private val childId: Long,
    private val dateString: String
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        val args = Bundle().apply {
            putLong("CHILD_ID", childId)
            putLong("USER_ID", childId)
            putString("DATE_STRING", dateString)
        }
        return when (position) {
            0 -> ParentTasksFragment().apply { arguments = args }
            1 -> ParentHabitsListFragment().apply { arguments = args }
            2 -> ParentHomeworkFragment().apply { arguments = args }
            3 -> PrayerHistoryFragment().apply { arguments = args }
            else -> ParentStatusFragment().apply { arguments = args } // <-- THIS WAS FAILING
        }
    }
}