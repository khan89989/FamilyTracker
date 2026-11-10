package com.family.tracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.family.tracker.fragments.HabitHistoryFragment

class HabitHistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_habit_history)

        val habitId = intent.getLongExtra("HABIT_ID", -1)
        val habitTitle = intent.getStringExtra("HABIT_TITLE") ?: "Habit History"

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = habitTitle
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.setNavigationOnClickListener { finish() }

        // Load the Fragment
        if (savedInstanceState == null) {
            val fragment = HabitHistoryFragment().apply {
                arguments = Bundle().apply { putLong("HABIT_ID", habitId) }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
        }
    }
}