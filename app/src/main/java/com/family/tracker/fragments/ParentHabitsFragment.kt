package com.family.tracker.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.family.tracker.ParentChildPagerAdapter
import com.family.tracker.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParentHabitsFragment : Fragment() {

    private var viewPagerParentHabits: ViewPager2? = null
    private var tabLayoutParentHabits: TabLayout? = null
    private var childId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            childId = it.getLong("child_id", -1L)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_parent_habits_tabs, container, false)
        viewPagerParentHabits = view.findViewById(R.id.viewPagerParentHabits)
        tabLayoutParentHabits = view.findViewById(R.id.tabLayoutParentHabits)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        viewPagerParentHabits?.let { vp ->
            val adapter = ParentChildPagerAdapter(requireActivity(), childId, currentDate)
            vp.adapter = adapter

            tabLayoutParentHabits?.let { tl ->
                TabLayoutMediator(tl, vp) { tab, position ->
                    tab.text = when (position) {
                        0 -> "Habits"
                        1 -> "History"
                        else -> "Tab $position"
                    }
                }.attach()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewPagerParentHabits = null
        tabLayoutParentHabits = null
    }
}