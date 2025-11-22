package com.ringdroid.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ringdroid.R
import com.ringdroid.data.ContactItem

/**
 * RecyclerView adapter for contacts.
 *
 * Uses existing contact_row layout IDs:
 *  - R.id.row_display_name
 *  - R.id.row_ringtone
 *  - R.id.row_starred
 */
class ContactsAdapter(
    private val onClick: (ContactItem) -> Unit,
) : ListAdapter<ContactItem, ContactsAdapter.ContactViewHolder>(DiffCallback) {
    object DiffCallback : DiffUtil.ItemCallback<ContactItem>() {
        override fun areItemsTheSame(
            oldItem: ContactItem,
            newItem: ContactItem,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: ContactItem,
            newItem: ContactItem,
        ): Boolean = oldItem == newItem
    }

    inner class ContactViewHolder(
        itemView: View,
    ) : RecyclerView.ViewHolder(itemView) {
        private val nameView: TextView = itemView.findViewById(R.id.row_display_name)
        private val ringtoneIndicator: View? = itemView.findViewById(R.id.row_ringtone)
        private val starredIndicator: View? = itemView.findViewById(R.id.row_starred)

        fun bind(item: ContactItem) {
            nameView.text = item.displayName

            ringtoneIndicator?.visibility =
                if (item.hasCustomRingtone) View.VISIBLE else View.INVISIBLE

            starredIndicator?.visibility =
                if (item.isStarred) View.VISIBLE else View.INVISIBLE

            itemView.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ContactViewHolder {
        val view =
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.contact_row, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ContactViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }
}
