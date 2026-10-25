package com.ringdroid.dialog

import android.app.AlertDialog
import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import androidx.core.text.HtmlCompat
import com.ringdroid.BuildConfig
import com.ringdroid.R
import com.ringdroid.databinding.AboutDialogBinding
import com.ringdroid.util.RingdroidUtils

object AboutDialog {
    fun show(context: Context) {
        val binding = AboutDialogBinding.inflate(LayoutInflater.from(context))
        binding.aboutVersion.text = context.getString(R.string.version_label, BuildConfig.VERSION_NAME)
        val html = context.getString(R.string.about_text_html)
        binding.aboutText.text = HtmlCompat.fromHtml(html.trim(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.aboutText.movementMethod = LinkMovementMethod.getInstance()

        val dialog = AlertDialog.Builder(context).setView(binding.root).create()
        RingdroidUtils.setTransparentBackground(dialog)
        binding.buttonAboutOk.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
