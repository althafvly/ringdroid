package com.ringdroid.dialog

import android.view.LayoutInflater
import android.view.View
import android.widget.PopupWindow
import androidx.core.view.isVisible
import com.ringdroid.R
import com.ringdroid.databinding.RowOptionsPopupBinding

/** Popup with the actions available for one audio file in the list. */
internal object RowOptionsPopup {
    private const val WIDTH_DP = 260

    interface Actions {
        fun onEdit()

        fun onSetDefault()

        fun onAssignContact()

        fun onDelete()
    }

    fun show(
        anchor: View,
        title: String?,
        isRingtone: Boolean,
        isNotification: Boolean,
        canDelete: Boolean,
        actions: Actions,
    ) {
        val binding = RowOptionsPopupBinding.inflate(LayoutInflater.from(anchor.context))
        val popup = Popups.create(binding.root, WIDTH_DP)
        binding.rowMenuTitle.text = title.orEmpty()
        bind(binding.menuItemEdit, true, popup, actions::onEdit)

        bind(binding.menuItemDefault, isRingtone || isNotification, popup, actions::onSetDefault)
        if (isRingtone) {
            binding.menuDefaultIcon.setImageResource(R.drawable.baseline_call_24)
            binding.menuDefaultText.setText(R.string.context_menu_default_ringtone)
        } else if (isNotification) {
            binding.menuDefaultIcon.setImageResource(R.drawable.baseline_notifications_24)
            binding.menuDefaultText.setText(R.string.context_menu_default_notification)
        }

        bind(binding.menuItemContact, isRingtone, popup, actions::onAssignContact)
        bind(binding.menuItemDelete, canDelete, popup, actions::onDelete)
        binding.deleteDivider.isVisible = canDelete

        Popups.showAlignedRight(popup, anchor, WIDTH_DP, 4)
    }

    private fun bind(
        item: View,
        visible: Boolean,
        popup: PopupWindow,
        action: () -> Unit,
    ) {
        item.isVisible = visible
        item.setOnClickListener {
            popup.dismiss()
            action()
        }
    }
}
