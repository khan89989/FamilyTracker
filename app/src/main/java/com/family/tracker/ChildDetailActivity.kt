package com.family.tracker

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.family.tracker.data.AppDatabase
import com.family.tracker.databinding.ActivityChildDetailBinding
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ChildDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChildDetailBinding
    private lateinit var db: AppDatabase
    private var childId: Long = -1
    private var childName: String = "Child"

    private var currentDate: Calendar = Calendar.getInstance()
    private val dbFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        childId = intent.getLongExtra("CHILD_ID", -1)
        childName = intent.getStringExtra("CHILD_NAME") ?: "Child"

        binding.tvChildTitle.text = childName

        loadProfileImage()
        updateDateLabel()

        binding.btnPrevDate.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_MONTH, -1)
            updateDateLabel()
            setupTabs()
        }

        binding.btnNextDate.setOnClickListener {
            currentDate.add(Calendar.DAY_OF_MONTH, 1)
            updateDateLabel()
            setupTabs()
        }

        setupTabs()
    }

    private fun updateDateLabel() {
        val today = Calendar.getInstance()
        binding.tvCurrentDate.text = when {
            dbFormat.format(currentDate.time) == dbFormat.format(today.time) -> "Today (${displayFormat.format(currentDate.time)})"
            else -> displayFormat.format(currentDate.time)
        }
    }

    private fun setupTabs() {
        val dateStr = dbFormat.format(currentDate.time)
        val currentTab = binding.viewPagerParent.currentItem

        val adapter = ParentChildPagerAdapter(this, childId, dateStr)
        binding.viewPagerParent.adapter = adapter

        TabLayoutMediator(binding.tabLayoutParent, binding.viewPagerParent) { tab, position ->
            tab.text = when (position) {
                0 -> "Tasks"
                1 -> "Habits"
                2 -> "H/W"
                3 -> "Prayers"
                else -> "Status"
            }
        }.attach()

        if (currentTab > 0) {
            binding.viewPagerParent.setCurrentItem(currentTab, false)
        }

        // Add page change listener to hide date navigator for Prayers tab
        binding.viewPagerParent.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 3) { // Prayers Tab index
                    binding.layoutDateNavigator.visibility = View.GONE
                } else {
                    binding.layoutDateNavigator.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun loadProfileImage() {
        lifecycleScope.launch(Dispatchers.IO) {
            val user = db.userDao().getUserById(childId)
            withContext(Dispatchers.Main) {
                if (user?.profileImageUri != null) {
                    val imgFile = File(user.profileImageUri)
                    if (imgFile.exists()) {
                        val bm = BitmapFactory.decodeFile(imgFile.absolutePath)
                        binding.ivProfilePhoto.setImageBitmap(bm)
                    }
                }
            }
        }
    }
}