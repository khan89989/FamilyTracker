package com.family.tracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.family.tracker.data.User

data class ChildProgress(
    val user: User,
    val tasksDone: Int, val tasksTotal: Int,
    val habitsDone: Int, val habitsTotal: Int,
    val hwDone: Int, val hwTotal: Int
)

class ChildAdapter(
    private var children: List<ChildProgress>,
    private val onChildClick: (User) -> Unit,
    private val onEditClick: (User) -> Unit
) : RecyclerView.Adapter<ChildAdapter.ChildViewHolder>() {

    class ChildViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvUsername: TextView = view.findViewById(R.id.tvChildUsername)
        val tvTaskBadge: TextView = view.findViewById(R.id.tvTaskBadge)
        val tvHabitBadge: TextView = view.findViewById(R.id.tvHabitBadge)
        val tvHwBadge: TextView = view.findViewById(R.id.tvHwBadge)
        val tvPointsBadge: TextView = view.findViewById(R.id.tvPointsBadge)
        val btnEdit: TextView = view.findViewById(R.id.btnEditChild)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChildViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_child, parent, false)
        return ChildViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChildViewHolder, position: Int) {
        val child = children[position]
        val user = child.user

        holder.tvUsername.text = user.username
        holder.tvTaskBadge.text = "T: ${child.tasksDone}/${child.tasksTotal}"
        holder.tvHabitBadge.text = "H: ${child.habitsDone}/${child.habitsTotal}"
        holder.tvHwBadge.text = "HW: ${child.hwDone}/${child.hwTotal}"
        holder.tvPointsBadge.text = "⭐ ${user.points}"

        holder.itemView.setOnClickListener { onChildClick(user) }
        holder.btnEdit.setOnClickListener { onEditClick(user) }
    }

    override fun getItemCount() = children.size

    fun updateData(newChildren: List<ChildProgress>) {
        children = newChildren
        notifyDataSetChanged()
    }
}