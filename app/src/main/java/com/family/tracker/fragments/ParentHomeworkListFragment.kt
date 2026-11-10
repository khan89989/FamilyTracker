package com.family.tracker.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.family.tracker.R

class ParentHomeworkListFragment : Fragment() {

    private var rvHomework: RecyclerView? = null
    private var tvCurrentDate: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_parent_homework, container, false)
        rvHomework = view.findViewById(R.id.rvHomework)
        tvCurrentDate = view.findViewById(R.id.tvCurrentDate)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvHomework?.layoutManager = LinearLayoutManager(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rvHomework = null
        tvCurrentDate = null
    }
}