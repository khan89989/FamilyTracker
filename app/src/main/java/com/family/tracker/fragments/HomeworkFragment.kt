package com.family.tracker.fragments

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.family.tracker.R
import com.family.tracker.data.AppDatabase
import com.family.tracker.data.Homework
import com.family.tracker.data.Subject
import com.family.tracker.databinding.FragmentHomeworkBinding
import com.family.tracker.utils.AutoBackupHelper
import com.family.tracker.utils.FontUtils
import com.family.tracker.utils.PasswordUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeworkFragment : Fragment() {

    private var _binding: FragmentHomeworkBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private var childId: Long = -1
    private lateinit var adapter: HomeworkAdapter
    private val expandedSubjects = mutableSetOf<Long>()

    private var totalAssignments = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeworkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childId = arguments?.getLong("CHILD_ID") ?: arguments?.getLong("USER_ID") ?: -1
        db = AppDatabase.getDatabase(requireContext())

        adapter = HomeworkAdapter(mutableListOf())
        binding.rvChildHomework.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChildHomework.adapter = adapter

        loadHomework()
    }

    override fun onResume() {
        super.onResume()
        loadHomework()
    }

    private fun loadHomework() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        lifecycleScope.launch(Dispatchers.IO) {
            val subjects = db.subjectDao().getSubjectsForChild(childId)
            val homeworkList = db.homeworkDao().getHomeworkForChildByDate(childId, today)

            totalAssignments = homeworkList.size

            val listItems = mutableListOf<Any>()
            for (sub in subjects) {
                listItems.add(sub)
                if (expandedSubjects.contains(sub.id)) {
                    val hwForSub = homeworkList.filter { it.subjectId == sub.id }
                    listItems.addAll(hwForSub)
                }
            }

            withContext(Dispatchers.Main) {
                adapter.updateData(listItems)
            }
        }
    }

    private fun showMotivationalPopup(isSuccess: Boolean) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_motivational, null)
        val tvEmoji = dialogView.findViewById<TextView>(R.id.tvEmoji)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvMessage)
        if (isSuccess) { tvEmoji.text = "😃"; tvMessage.text = "Great Job!" } else { tvEmoji.text = "🤔"; tvMessage.text = "Don't Worry!" }
        AlertDialog.Builder(requireContext()).setView(dialogView).setPositiveButton("OK") { d, _ -> d.dismiss() }.setCancelable(false).show()
    }

    private fun showParentPasswordDialog(onSuccess: () -> Unit) {
        val container = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 0) }
        val etPassword = EditText(requireContext()).apply { hint = "Parent Password to Approve"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        container.addView(etPassword)

        AlertDialog.Builder(requireContext()).setTitle("Parent Approval Required").setView(container).setPositiveButton("Approve", null).setNegativeButton("Cancel", null).create().apply {
            setOnShowListener {
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val inputPass = etPassword.text.toString().trim()
                    lifecycleScope.launch(Dispatchers.IO) {
                        val parents = db.userDao().getAllUsers().filter { it.role == "PARENT" }
                        val isValid = parents.any { PasswordUtils.hashPassword(inputPass) == it.passwordHash }
                        withContext(Dispatchers.Main) {
                            if (isValid) { onSuccess(); dismiss() }
                            else { Toast.makeText(requireContext(), "Incorrect parent password!", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
            }
        }.show()
    }

    private fun updatePointsForAssignment(hw: Homework, newStatus: String) {
        val fraction = if (totalAssignments > 0) 1.0 / totalAssignments else 0.0

        lifecycleScope.launch(Dispatchers.IO) {
            if (newStatus == "DONE" && hw.status != "DONE") {
                db.userDao().updatePoints(childId, fraction)
            } else if (newStatus != "DONE" && hw.status == "DONE") {
                db.userDao().updatePoints(childId, -fraction)
            }
        }
    }

    private fun markCompleted(hw: Homework) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.homeworkDao().updateHomework(hw.copy(status = "DONE", completionTimeMillis = System.currentTimeMillis()))
            AutoBackupHelper.triggerSilentBackup(requireContext())
            withContext(Dispatchers.Main) {
                updatePointsForAssignment(hw, "DONE")
                loadHomework()
                showMotivationalPopup(true)
            }
        }
    }

    private fun markNeedHelp(hw: Homework) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.homeworkDao().updateHomework(hw.copy(status = "NEEDS_HELP", needsHelp = true, completionTimeMillis = System.currentTimeMillis()))
            AutoBackupHelper.triggerSilentBackup(requireContext())
            withContext(Dispatchers.Main) {
                updatePointsForAssignment(hw, "NEEDS_HELP")
                loadHomework()
                showMotivationalPopup(false)
            }
        }
    }

    inner class HomeworkAdapter(private var items: MutableList<Any>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_SUBJECT = 1
        private val TYPE_HW = 2

        override fun getItemViewType(position: Int): Int {
            return if (items[position] is Subject) TYPE_SUBJECT else TYPE_HW
        }

        inner class SubjectViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvSubjectName)
            val tvExpand: TextView = view.findViewById(R.id.tvExpandIcon)
        }

        inner class HomeworkViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvHwTitle)
            val tvNotes: TextView = view.findViewById(R.id.tvHwNotes)
            val tvHelpMessage: TextView = view.findViewById(R.id.tvHelpMessage)
            val tvHelpAttachment: TextView = view.findViewById(R.id.tvHelpAttachment)
            val tvStatus: TextView = view.findViewById(R.id.tvStatus)
            val btnNeedHelp: Button = view.findViewById(R.id.btnNeedHelp)
            val btnComplete: Button = view.findViewById(R.id.btnComplete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_SUBJECT) {
                SubjectViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_subject_child, parent, false))
            } else {
                HomeworkViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_homework, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (getItemViewType(position) == TYPE_SUBJECT) {
                val subject = items[position] as Subject
                val sHolder = holder as SubjectViewHolder
                sHolder.tvName.text = subject.name
                FontUtils.applySmartFont(requireContext(), sHolder.tvName, subject.name)

                val isExpanded = expandedSubjects.contains(subject.id)
                sHolder.tvExpand.text = if (isExpanded) "▼" else "▶"

                sHolder.itemView.setOnClickListener {
                    if (isExpanded) expandedSubjects.remove(subject.id) else expandedSubjects.add(subject.id)
                    loadHomework()
                }
            } else {
                val hw = items[position] as Homework
                val hHolder = holder as HomeworkViewHolder

                hHolder.tvTitle.text = hw.title
                hHolder.tvNotes.text = hw.notes

                FontUtils.applySmartFont(requireContext(), hHolder.tvTitle, hw.title)
                FontUtils.applySmartFont(requireContext(), hHolder.tvNotes, hw.notes)

                if (hw.helpText != null) {
                    hHolder.tvHelpMessage.visibility = View.VISIBLE
                    hHolder.tvHelpMessage.text = "Parent Help: ${hw.helpText}"
                    FontUtils.applySmartFont(requireContext(), hHolder.tvHelpMessage, hw.helpText!!)
                } else hHolder.tvHelpMessage.visibility = View.GONE

                // FIXED: Open File/Photo securely using FileProvider
                if (hw.helpPhotoUri != null || hw.helpFileUri != null) {
                    hHolder.tvHelpAttachment.visibility = View.VISIBLE
                    hHolder.tvHelpAttachment.setOnClickListener {
                        val path = hw.helpPhotoUri ?: hw.helpFileUri
                        if (path != null) {
                            try {
                                val file = File(path)
                                if (file.exists()) {
                                    val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, requireContext().contentResolver.getType(uri))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    startActivity(intent)
                                } else {
                                    Toast.makeText(requireContext(), "File no longer exists", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(requireContext(), "Cannot open attachment", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else hHolder.tvHelpAttachment.visibility = View.GONE

                val timeStr = if (hw.completionTimeMillis > 0L) SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(hw.completionTimeMillis)) else ""
                val statusText = when (hw.status) {
                    "DONE" -> "✅ Completed at $timeStr"
                    "NEEDS_HELP" -> "🤔 Help Req at $timeStr"
                    else -> "⏳ Pending"
                }
                hHolder.tvStatus.text = statusText

                if (hw.status == "DONE") {
                    hHolder.btnComplete.visibility = View.GONE
                    hHolder.btnNeedHelp.visibility = View.GONE
                } else if (hw.helpText != null || hw.helpPhotoUri != null || hw.helpFileUri != null) {
                    hHolder.btnNeedHelp.visibility = View.GONE
                    hHolder.btnComplete.visibility = View.VISIBLE
                } else if (hw.needsHelp) {
                    hHolder.btnNeedHelp.visibility = View.GONE
                    hHolder.btnComplete.visibility = View.GONE
                } else {
                    hHolder.btnNeedHelp.visibility = View.VISIBLE
                    hHolder.btnComplete.visibility = View.VISIBLE
                }

                hHolder.btnComplete.setOnClickListener {
                    showParentPasswordDialog { markCompleted(hw) }
                }

                hHolder.btnNeedHelp.setOnClickListener {
                    markNeedHelp(hw)
                }
            }
        }

        override fun getItemCount() = items.size

        fun updateData(newItems: List<Any>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}