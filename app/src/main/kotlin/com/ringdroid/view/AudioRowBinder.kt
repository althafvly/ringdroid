package com.ringdroid.view

import android.content.res.ColorStateList
import android.database.Cursor
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.ringdroid.R
import com.ringdroid.data.AudioRow.Companion.isFlagSet
import com.ringdroid.soundfile.SoundFile

/** Binds a list row: the kind icon, optional artist and album, and the options button. */
internal class AudioRowBinder(private val onOptions: (anchor: View, position: Int) -> Unit) :
    SimpleCursorAdapter.ViewBinder {
    override fun setViewValue(view: View, cursor: Cursor, columnIndex: Int): Boolean {
        when (view.id) {
            R.id.row_options_button -> {
                val position = cursor.position
                view.setOnClickListener { onOptions(it, position) }
            }

            R.id.row_icon -> {
                setSoundIcon(view as ImageView, cursor)
            }

            R.id.row_artist -> {
                bindOptionalText(view as TextView, cursor.getString(columnIndex), hideZero = false)
            }

            R.id.row_album -> {
                bindOptionalText(view as TextView, cursor.getString(columnIndex), hideZero = true)
            }

            else -> {
                return false
            }
        }
        return true
    }

    // MediaStore reports missing tags as "<unknown>" (and a missing album as "0").
    private fun bindOptionalText(view: TextView, text: String?, hideZero: Boolean) {
        val hidden =
            text.isNullOrEmpty() || text.equals("<unknown>", ignoreCase = true) ||
                (hideZero && text == "0")
        view.isVisible = !hidden
        if (!hidden) {
            view.text = text
        }
    }

    private fun setSoundIcon(view: ImageView, cursor: Cursor) {
        val (iconRes, iconColorRes, containerColorRes) =
            when {
                cursor.isFlagSet(MediaStore.Audio.Media.IS_RINGTONE) -> {
                    Triple(
                        R.drawable.baseline_call_24,
                        R.color.category_icon_ringtone,
                        R.color.category_container_ringtone,
                    )
                }

                cursor.isFlagSet(MediaStore.Audio.Media.IS_ALARM) -> {
                    Triple(
                        R.drawable.baseline_access_alarm_24,
                        R.color.category_icon_alarm,
                        R.color.category_container_alarm,
                    )
                }

                cursor.isFlagSet(MediaStore.Audio.Media.IS_NOTIFICATION) -> {
                    Triple(
                        R.drawable.baseline_notifications_24,
                        R.color.category_icon_notification,
                        R.color.category_container_notification,
                    )
                }

                else -> {
                    Triple(
                        R.drawable.baseline_music_note_24,
                        R.color.category_icon_music,
                        R.color.category_container_music,
                    )
                }
            }
        val filename = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
        val supported = filename != null && SoundFile.isFilenameSupported(filename)
        val context = view.context
        view.setImageResource(iconRes)
        view.imageTintList =
            ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (supported) iconColorRes else R.color.category_icon_unsupported,
                ),
            )
        view.backgroundTintList =
            ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (supported) containerColorRes else R.color.category_container_unsupported,
                ),
            )
    }
}
