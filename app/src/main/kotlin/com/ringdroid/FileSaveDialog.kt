/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ringdroid

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.res.Resources
import android.os.Message
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner

class FileSaveDialog(
    context: Context,
    resources: Resources,
    originalName: String?,
    response: Message,
) : Dialog(context) {
    private val mTypeSpinner: Spinner
    private val mFilename: EditText
    private val mResponse: Message
    private val mOriginalName: String?
    private val mTypeArray: ArrayList<String?>
    private var mPreviousSelection: Int

    init {
        // Inflate our UI from its XML layout description.
        setContentView(R.layout.file_save)

        setTitle(resources.getString(R.string.file_save_title))

        mTypeArray = ArrayList<String?>()
        mTypeArray.add(resources.getString(R.string.type_music))
        mTypeArray.add(resources.getString(R.string.type_alarm))
        mTypeArray.add(resources.getString(R.string.type_notification))
        mTypeArray.add(resources.getString(R.string.type_ringtone))

        mFilename = findViewById<EditText>(R.id.filename)
        mOriginalName = originalName

        val adapter =
            ArrayAdapter<String?>(context, android.R.layout.simple_spinner_item, mTypeArray)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mTypeSpinner = findViewById<Spinner>(R.id.ringtone_type)
        mTypeSpinner.adapter = adapter
        mTypeSpinner.setSelection(FILE_KIND_RINGTONE)
        mPreviousSelection = FILE_KIND_RINGTONE

        setFilenameEditBoxFromName(false)

        mTypeSpinner.onItemSelectedListener =
            object : OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    v: View?,
                    position: Int,
                    id: Long,
                ) {
                    setFilenameEditBoxFromName(true)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }

        val save = findViewById<Button>(R.id.save)
        val saveListener: View.OnClickListener =
            View.OnClickListener {
                mResponse.obj = mFilename.text
                mResponse.arg1 = mTypeSpinner.selectedItemPosition
                mResponse.sendToTarget()
                dismiss()
            }
        save.setOnClickListener(saveListener)
        val cancel = findViewById<Button>(R.id.cancel)
        val cancelListener = View.OnClickListener { view: View? -> dismiss() }
        cancel.setOnClickListener(cancelListener)
        mResponse = response
    }

    @SuppressLint("SetTextI18n")
    private fun setFilenameEditBoxFromName(onlyIfNotEdited: Boolean) {
        if (onlyIfNotEdited) {
            val currentText: CharSequence = mFilename.text
            val expectedText = "$mOriginalName ${mTypeArray[mPreviousSelection]}"
            if (!expectedText.contentEquals(currentText)) {
                return
            }
        }

        val newSelection = mTypeSpinner.selectedItemPosition
        val newSuffix = mTypeArray[newSelection]
        mFilename.setText("$mOriginalName $newSuffix")
        mPreviousSelection = mTypeSpinner.selectedItemPosition
    }

    companion object {
        // File kinds - these should correspond to the order in which
        // they're presented in the spinner control
        const val FILE_KIND_MUSIC: Int = 0
        const val FILE_KIND_ALARM: Int = 1
        const val FILE_KIND_NOTIFICATION: Int = 2
        const val FILE_KIND_RINGTONE: Int = 3
    }
}
