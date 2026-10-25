package com.ringdroid.dialog

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnDetach

/** Shared setup for the dropdown popups on the select screen. */
internal object Popups {
    fun create(
        contentView: View,
        widthDp: Int,
    ): PopupWindow {
        val density = contentView.resources.displayMetrics.density
        return PopupWindow(contentView, (widthDp * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            isOutsideTouchable = true
            elevation = 12 * density
            animationStyle = android.R.style.Animation_Dialog
        }
    }

    fun showAlignedRight(
        popup: PopupWindow,
        anchor: View,
        widthDp: Int,
        yOffsetDp: Int,
    ) {
        val density = anchor.resources.displayMetrics.density
        val xOffset = anchor.width - (widthDp * density).toInt() - (8 * density).toInt()
        // Close with the screen, so recreating the activity doesn't leak the popup.
        anchor.doOnDetach { popup.dismiss() }
        popup.showAsDropDown(anchor, xOffset, (yOffsetDp * density).toInt())
    }
}
