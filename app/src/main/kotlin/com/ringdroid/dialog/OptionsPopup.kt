package com.ringdroid.dialog

import android.view.LayoutInflater
import android.view.View
import com.ringdroid.BuildConfig
import com.ringdroid.R
import com.ringdroid.databinding.SelectOptionsPopupBinding

/** The select screen's overflow menu: the "show all audio" switch and the about dialog. */
internal object OptionsPopup {
    private const val WIDTH_DP = 280
    private const val Y_OFFSET_DP = 8

    fun show(anchor: View, showAll: Boolean, onShowAllChanged: (Boolean) -> Unit) {
        val context = anchor.context
        val binding = SelectOptionsPopupBinding.inflate(LayoutInflater.from(context))
        var checked = showAll
        binding.switchShowAll.isChecked = checked
        binding.menuAboutVersion.text =
            context.getString(R.string.version_label, BuildConfig.VERSION_NAME)
        val popup = Popups.create(binding.root, WIDTH_DP)
        binding.menuItemShowAll.setOnClickListener {
            checked = !checked
            binding.switchShowAll.isChecked = checked
            onShowAllChanged(checked)
        }
        binding.menuItemAbout.setOnClickListener {
            popup.dismiss()
            AboutDialog.show(context)
        }
        Popups.showAlignedRight(popup, anchor, WIDTH_DP, Y_OFFSET_DP)
    }
}
