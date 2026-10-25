package com.ringdroid.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.ringdroid.R
import com.ringdroid.databinding.FileSaveBinding
import com.ringdroid.util.createDialog
import java.util.Locale

/** Asks for the name and kind (music, alarm, notification, ringtone) of the file to save. */
class FileSaveDialog(
    private val context: Context,
    originalName: String?,
    listener: (title: CharSequence, fileKind: Int) -> Unit,
) {
    // In the order shown in the spinner, matching the FILE_KIND constants.
    private val typeNames =
        listOf(R.string.type_music, R.string.type_alarm, R.string.type_notification, R.string.type_ringtone)
            .map { context.getString(it) }
    private val binding = FileSaveBinding.inflate(LayoutInflater.from(context))
    private val baseName = stripTypeSuffixes(originalName.orEmpty())
    private var previousSelection = FILE_KIND_RINGTONE
    private val dialog: AlertDialog

    init {
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, typeNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.ringtoneType.adapter = adapter
        binding.ringtoneType.setSelection(FILE_KIND_RINGTONE)
        updateFilename(onlyIfNotEdited = false)
        binding.ringtoneType.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    updateFilename(onlyIfNotEdited = true)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

        dialog = createDialog(context, binding.root)
        binding.save.setOnClickListener {
            dialog.dismiss()
            listener(binding.filename.text, binding.ringtoneType.selectedItemPosition)
        }
        binding.cancel.setOnClickListener { dialog.dismiss() }
    }

    /** Removes trailing " Ringtone", "_Alarm" etc. so the new kind can be appended. */
    private fun stripTypeSuffixes(name: String): String {
        var base = name
        while (true) {
            val lower = base.lowercase(Locale.ROOT)
            val type =
                typeNames.firstOrNull {
                    val suffix = it.lowercase(Locale.ROOT)
                    lower.endsWith(" $suffix") || lower.endsWith("_$suffix")
                } ?: return base
            base = base.substring(0, base.length - (type.length + 1))
        }
    }

    private fun nameFor(kind: Int): String {
        val suffix = typeNames[kind]
        return if (baseName.isEmpty()) suffix else context.getString(R.string.filename_with_suffix, baseName, suffix)
    }

    // Keeps the name in sync with the chosen kind unless the user edited it.
    private fun updateFilename(onlyIfNotEdited: Boolean) {
        val expected =
            if (baseName.isEmpty()) {
                typeNames[previousSelection]
            } else {
                baseName + " " +
                    typeNames[previousSelection]
            }
        if (onlyIfNotEdited && !expected.contentEquals(binding.filename.text)) {
            return
        }
        previousSelection = binding.ringtoneType.selectedItemPosition
        binding.filename.setText(nameFor(previousSelection))
    }

    fun show() {
        dialog.show()
    }

    companion object {
        const val FILE_KIND_MUSIC = 0
        const val FILE_KIND_ALARM = 1
        const val FILE_KIND_NOTIFICATION = 2
        const val FILE_KIND_RINGTONE = 3
    }
}
