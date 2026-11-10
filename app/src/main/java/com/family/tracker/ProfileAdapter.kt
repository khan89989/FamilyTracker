package com.family.tracker

import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.family.tracker.data.User
import java.io.File

class ProfileAdapter(
    private var users: List<User>,
    private val onProfileClick: (User) -> Unit
) : RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

    class ProfileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivProfile: ImageView = view.findViewById(R.id.ivProfileTile)
        val tvName: TextView = view.findViewById(R.id.tvProfileName)
        val tvPoints: TextView = view.findViewById(R.id.tvProfilePoints)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_profile_selection, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val user = users[position]
        holder.tvName.text = user.username

        // DIFFERENTIATE PARENT AND CHILD
        if (user.role == "PARENT") {
            holder.tvPoints.text = "👑 Parent"
            holder.tvPoints.setTextColor(Color.parseColor("#6200EE")) // Purple for parents
        } else {
            holder.tvPoints.text = "⭐ ${user.points}"
            holder.tvPoints.setTextColor(Color.parseColor("#FF9800")) // Orange for children
        }

        if (user.profileImageUri != null) {
            val imgFile = File(user.profileImageUri)
            if (imgFile.exists()) {
                val bm = BitmapFactory.decodeFile(imgFile.absolutePath)
                holder.ivProfile.setImageBitmap(bm)
            } else {
                holder.ivProfile.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        } else {
            holder.ivProfile.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        holder.itemView.setOnClickListener { onProfileClick(user) }
    }

    override fun getItemCount() = users.size

    fun updateData(newUsers: List<User>) {
        users = newUsers
        notifyDataSetChanged()
    }
}