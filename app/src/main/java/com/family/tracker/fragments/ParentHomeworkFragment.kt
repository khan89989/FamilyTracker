package com.family.tracker.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.family.tracker.R
import com.family.tracker.data.AppDatabase
import com.family.tracker.data.Homework
import com.family.tracker.data.Subject
import com.family.tracker.databinding.FragmentParentHomeworkBinding
import com.family.tracker.utils.AutoBackupHelper
import com.family.tracker.utils.FontUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParentHomeworkFragment : Fragment() {

    private var _binding: FragmentParentHomeworkBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private var childId: Long = -1
    private var dateString: String = ""
    private lateinit var adapter: ParentHomeworkAdapter

    private val expandedSubjects = mutableSetOf<Long>()

    private var currentHelpHw: Homework? = null
    private var currentHelpText: String = ""

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && currentHelpHw != null) {
            saveHelp(currentHelpHw!!, currentHelpText, null, uri)
        }
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            if (imageBitmap != null && currentHelpHw != null) {
                val file = File(requireContext().filesDir, "help_${System.currentTimeMillis()}.jpg")
                val fos = FileOutputStream(file)
                imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                fos.close()
                // Call saveHelp but pass the Uri from the file
                saveHelp(currentHelpHw!!, currentHelpText, Uri.fromFile(file), null)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParentHomeworkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childId = arguments?.getLong("CHILD_ID") ?: -1
        dateString = arguments?.getString("DATE_STRING") ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        db = AppDatabase.getDatabase(requireContext())

        adapter = ParentHomeworkAdapter(mutableListOf())
        binding.rvParentHomework.layoutManager = LinearLayoutManager(requireContext())
        binding.rvParentHomework.adapter = adapter

        binding.btnAddSubject.setOnClickListener { showAddSubjectDialog() }

        loadHomework()
    }

    override fun onResume() {
        super.onResume()
        loadHomework()
    }

    private fun loadHomework() {
        lifecycleScope.launch(Dispatchers.IO) {
            val subjects = db.subjectDao().getSubjectsForChild(childId)
            val homeworkList = db.homeworkDao().getHomeworkForChildByDate(childId, dateString)

            val listItems = mutableListOf<Any>()
            for (sub in subjects) {
                listItems.add(sub)
                if (expandedSubjects.contains(sub.id)) {
                    val hwForSub = homeworkList.filter { it.subjectId == sub.id }
                    listItems.addAll(hwForSub)
                }
            }

            withContext(Dispatchers.Main) { adapter.updateData(listItems) }
        }
    }

    private fun showAddSubjectDialog() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val etSubject = EditText(requireContext()).apply { hint = "Subject Name (e.g. Math)" }

        etSubject.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                FontUtils.applySmartFont(requireContext(), etSubject, s.toString())
            }
        })

        container.addView(etSubject)

        AlertDialog.Builder(requireContext())
            .setTitle("Add Subject")
            .setView(container)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = etSubject.text.toString().trim()
                        if (name.isNotEmpty()) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                db.subjectDao().insertSubject(Subject(childId = childId, name = name))
                                AutoBackupHelper.triggerSilentBackup(requireContext())
                                withContext(Dispatchers.Main) { loadHomework(); dismiss() }
                            }
                        }
                    }
                }
            }.show()
    }

    private fun showEditSubjectDialog(subject: Subject) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val etSubject = EditText(requireContext()).apply {
            hint = "Subject Name"
            setText(subject.name)
        }
        etSubject.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                FontUtils.applySmartFont(requireContext(), etSubject, s.toString())
            }
        })
        container.addView(etSubject)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Subject")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = etSubject.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.subjectDao().updateSubject(subject.copy(name = name))
                        AutoBackupHelper.triggerSilentBackup(requireContext())
                        withContext(Dispatchers.Main) { loadHomework() }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSubject(subject: Subject) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Subject?")
            .setMessage("Are you sure you want to delete '${subject.name}' and all its assignments?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.homeworkDao().deleteHomeworkBySubject(subject.id)
                    db.subjectDao().deleteSubject(subject)
                    AutoBackupHelper.triggerSilentBackup(requireContext())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Subject deleted", Toast.LENGTH_SHORT).show()
                        loadHomework()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddAssignmentDialog(subject: Subject, existingHw: Homework? = null) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val etTitle = EditText(requireContext()).apply { hint = "Assignment Title" }
        val etNotes = EditText(requireContext()).apply { hint = "Notes (optional)" }

        if (existingHw != null) {
            etTitle.setText(existingHw.title)
            etNotes.setText(existingHw.notes)
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                FontUtils.applySmartFont(requireContext(), etTitle, s.toString())
            }
        }
        etTitle.addTextChangedListener(watcher)

        val notesWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                FontUtils.applySmartFont(requireContext(), etNotes, s.toString())
            }
        }
        etNotes.addTextChangedListener(notesWatcher)

        container.addView(etTitle)
        container.addView(etNotes)

        AlertDialog.Builder(requireContext())
            .setTitle(if (existingHw == null) "Add Assignment to ${subject.name}" else "Edit Assignment")
            .setView(container)
            .setPositiveButton(if (existingHw == null) "Add" else "Save", null)
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val title = etTitle.text.toString().trim()
                        val notes = etNotes.text.toString().trim()
                        if (title.isNotEmpty()) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                if (existingHw == null) {
                                    db.homeworkDao().insertHomework(
                                        Homework(childId = childId, subjectId = subject.id, title = title, assignedDate = dateString, notes = notes)
                                    )
                                } else {
                                    db.homeworkDao().updateHomework(existingHw.copy(title = title, notes = notes))
                                }
                                AutoBackupHelper.triggerSilentBackup(requireContext())
                                withContext(Dispatchers.Main) { loadHomework(); dismiss() }
                            }
                        }
                    }
                }
            }.show()
    }

    private fun showProvideHelpDialog(hw: Homework) {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val etHelp = EditText(requireContext()).apply { hint = "Enter help text..."; minLines = 2 }

        etHelp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                FontUtils.applySmartFont(requireContext(), etHelp, s.toString())
            }
        })

        val btnCamera = Button(requireContext()).apply { text = "📷 Attach Photo" }
        val btnFile = Button(requireContext()).apply { text = "📎 Attach File" }

        container.addView(etHelp)
        container.addView(btnCamera)
        container.addView(btnFile)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Help for ${hw.title}")
            .setView(container)
            .setPositiveButton("Send Text Help", null)
            .setNegativeButton("Cancel", null)
            .create()

        btnCamera.setOnClickListener {
            currentHelpHw = hw
            currentHelpText = etHelp.text.toString().trim()
            takePhotoLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            dialog.dismiss()
        }

        btnFile.setOnClickListener {
            currentHelpHw = hw
            currentHelpText = etHelp.text.toString().trim()
            pickFileLauncher.launch("*/*")
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = etHelp.text.toString().trim()
                if (text.isNotEmpty()) {
                    saveHelp(hw, text, null, null)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    // --- FIXED: Saves the URI content to a local file so the child can open it ---
    private fun saveHelp(hw: Homework, text: String, photoUri: Uri?, fileUri: Uri?) {
        lifecycleScope.launch(Dispatchers.IO) {
            var finalPhotoPath: String? = null
            var finalFilePath: String? = null

            if (photoUri != null) {
                try {
                    val file = File(requireContext().filesDir, "help_photo_${System.currentTimeMillis()}.jpg")
                    requireContext().contentResolver.openInputStream(photoUri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    finalPhotoPath = file.absolutePath
                } catch (e: Exception) { e.printStackTrace() }
            }

            if (fileUri != null) {
                try {
                    val file = File(requireContext().filesDir, "help_file_${System.currentTimeMillis()}")
                    requireContext().contentResolver.openInputStream(fileUri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    finalFilePath = file.absolutePath
                } catch (e: Exception) { e.printStackTrace() }
            }

            db.homeworkDao().updateHomework(hw.copy(
                helpText = if (text.isEmpty()) null else text,
                helpPhotoUri = finalPhotoPath,
                helpFileUri = finalFilePath,
                needsHelp = false
            ))
            AutoBackupHelper.triggerSilentBackup(requireContext())
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Help sent!", Toast.LENGTH_SHORT).show()
                loadHomework()
            }
        }
    }

    private fun resetAssignment(hw: Homework) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.homeworkDao().updateHomework(hw.copy(
                status = "PENDING", completionTimeMillis = 0L, needsHelp = false, helpText = null, helpPhotoUri = null, helpFileUri = null
            ))
            AutoBackupHelper.triggerSilentBackup(requireContext())
            withContext(Dispatchers.Main) { loadHomework() }
        }
    }

    private fun deleteAssignment(hw: Homework) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.homeworkDao().deleteHomework(hw)
            AutoBackupHelper.triggerSilentBackup(requireContext())
            withContext(Dispatchers.Main) { loadHomework() }
        }
    }

    inner class ParentHomeworkAdapter(private var items: MutableList<Any>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_SUBJECT = 1
        private val TYPE_HW = 2

        override fun getItemViewType(position: Int): Int {
            return if (items[position] is Subject) TYPE_SUBJECT else TYPE_HW
        }

        inner class SubjectViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvSubjectName)
            val tvExpand: TextView = view.findViewById(R.id.tvExpandIcon)
            val btnAdd: Button = view.findViewById(R.id.btnAddAssignment)
        }

        inner class HomeworkViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvHwTitle)
            val tvNotes: TextView = view.findViewById(R.id.tvHwNotes)
            val tvStatusTime: TextView = view.findViewById(R.id.tvStatusTime)
            val tvHelpText: TextView = view.findViewById(R.id.tvHelpMessage)
            val tvHelpAttachment: TextView = view.findViewById(R.id.tvHelpAttachment)
            val btnProvideHelp: Button = view.findViewById(R.id.btnProvideHelp)
            val btnEdit: Button = view.findViewById(R.id.btnEditAssignment)
            val btnDelete: Button = view.findViewById(R.id.btnDeleteAssignment)
            val btnReset: Button = view.findViewById(R.id.btnResetAssignment)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_SUBJECT) {
                SubjectViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_subject_parent, parent, false))
            } else {
                HomeworkViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_homework_parent, parent, false))
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

                sHolder.btnAdd.setOnClickListener { showAddAssignmentDialog(subject) }
            } else {
                val hw = items[position] as Homework
                val hHolder = holder as HomeworkViewHolder

                hHolder.tvTitle.text = hw.title
                hHolder.tvNotes.text = hw.notes

                FontUtils.applySmartFont(requireContext(), hHolder.tvTitle, hw.title)
                FontUtils.applySmartFont(requireContext(), hHolder.tvNotes, hw.notes)

                val timeStr = if (hw.completionTimeMillis > 0L) SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(hw.completionTimeMillis)) else ""
                val statusText = when (hw.status) {
                    "DONE" -> "✅ Completed at $timeStr"
                    "NEEDS_HELP" -> "🤔 Help Req at $timeStr"
                    else -> "⏳ Pending"
                }
                hHolder.tvStatusTime.text = statusText

                if (hw.helpText != null) {
                    hHolder.tvHelpText.visibility = View.VISIBLE
                    hHolder.tvHelpText.text = "Help: ${hw.helpText}"
                    FontUtils.applySmartFont(requireContext(), hHolder.tvHelpText, hw.helpText!!)
                } else hHolder.tvHelpText.visibility = View.GONE

                if (hw.helpPhotoUri != null || hw.helpFileUri != null) {
                    hHolder.tvHelpAttachment.visibility = View.VISIBLE
                } else hHolder.tvHelpAttachment.visibility = View.GONE

                if (hw.needsHelp && hw.helpText == null && hw.helpPhotoUri == null && hw.helpFileUri == null) {
                    hHolder.btnProvideHelp.visibility = View.VISIBLE
                    hHolder.btnProvideHelp.setOnClickListener { showProvideHelpDialog(hw) }
                } else {
                    hHolder.btnProvideHelp.visibility = View.GONE
                }

                hHolder.btnEdit.setOnClickListener {
                    val subject = items.find { it is Subject && it.id == hw.subjectId } as? Subject
                    if (subject != null) showAddAssignmentDialog(subject, hw)
                }

                hHolder.btnDelete.setOnClickListener {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Delete Assignment?")
                        .setMessage("Are you sure you want to delete '${hw.title}'?")
                        .setPositiveButton("Yes") { _, _ -> deleteAssignment(hw) }
                        .setNegativeButton("No", null).show()
                }

                hHolder.btnReset.setOnClickListener {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Reset Assignment?")
                        .setMessage("Clear all statuses and help for this assignment?")
                        .setPositiveButton("Yes") { _, _ -> resetAssignment(hw) }
                        .setNegativeButton("No", null).show()
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