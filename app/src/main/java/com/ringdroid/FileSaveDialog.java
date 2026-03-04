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

package com.ringdroid;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Resources;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import com.ringdroid.databinding.FileSaveBinding;

import java.util.ArrayList;

public class FileSaveDialog {

    // File kinds - these should correspond to the order in which
    // they're presented in the spinner control
    public static final int FILE_KIND_MUSIC = 0;
    public static final int FILE_KIND_ALARM = 1;
    public static final int FILE_KIND_NOTIFICATION = 2;
    public static final int FILE_KIND_RINGTONE = 3;

    private final Spinner mTypeSpinner;
    private final EditText mFilename;
    private final String mOriginalName;
    private final ArrayList<String> mTypeArray;
    private int mPreviousSelection;
    private AlertDialog mDialog;
    private final Context mContext;

    public FileSaveDialog(Context context, Resources resources, String originalName, Message response) {
        mContext = context;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.file_save_title);

        // Inflate our UI from its XML layout description.
        FileSaveBinding binding = FileSaveBinding.inflate(LayoutInflater.from(context));
        builder.setView(binding.getRoot());

        mTypeArray = new ArrayList<>();
        mTypeArray.add(resources.getString(R.string.type_music));
        mTypeArray.add(resources.getString(R.string.type_alarm));
        mTypeArray.add(resources.getString(R.string.type_notification));
        mTypeArray.add(resources.getString(R.string.type_ringtone));

        mFilename = binding.filename;
        mOriginalName = originalName;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, mTypeArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mTypeSpinner = binding.ringtoneType;
        mTypeSpinner.setAdapter(adapter);
        mTypeSpinner.setSelection(FILE_KIND_RINGTONE);
        mPreviousSelection = FILE_KIND_RINGTONE;

        setFilenameEditBoxFromName(false);

        mTypeSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                setFilenameEditBoxFromName(true);
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        mDialog = builder.create();

        binding.save.setOnClickListener(view -> {
            response.obj = mFilename.getText();
            response.arg1 = mTypeSpinner.getSelectedItemPosition();
            response.sendToTarget();
            mDialog.dismiss();
        });

        binding.cancel.setOnClickListener(view -> mDialog.dismiss());
    }

    private void setFilenameEditBoxFromName(boolean onlyIfNotEdited) {
        if (onlyIfNotEdited) {
            CharSequence currentText = mFilename.getText();
            String expectedText = mOriginalName + " " + mTypeArray.get(mPreviousSelection);

            if (!expectedText.contentEquals(currentText)) {
                return;
            }
        }

        int newSelection = mTypeSpinner.getSelectedItemPosition();
        String newSuffix = mTypeArray.get(newSelection);
        mFilename.setText(mContext.getString(R.string.filename_with_suffix, mOriginalName, newSuffix));
        mPreviousSelection = mTypeSpinner.getSelectedItemPosition();
    }

    public void show() {
        mDialog.show();
    }
}
