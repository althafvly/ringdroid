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

package com.ringdroid.editor;

import com.ringdroid.R;
import com.ringdroid.util.RingdroidUtils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import com.ringdroid.databinding.FileSaveBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

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
    private final Context mContext;
    private int mPreviousSelection;
    private AlertDialog mDialog;

    public interface Listener {
        void onSave(CharSequence title, int fileKind);
    }

    public FileSaveDialog(Context context, String originalName, Listener listener) {
        Resources resources = context.getResources();
        mContext = context;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        // Inflate our UI from its XML layout description.
        FileSaveBinding binding = FileSaveBinding.inflate(LayoutInflater.from(context));
        builder.setView(binding.getRoot());

        mTypeArray = new ArrayList<>(Arrays.asList(resources.getString(R.string.type_music),
                resources.getString(R.string.type_alarm),
                resources.getString(R.string.type_notification),
                resources.getString(R.string.type_ringtone)));

        mFilename = binding.filename;
        mOriginalName = originalName;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, mTypeArray);
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
        RingdroidUtils.setTransparentBackground(mDialog);

        binding.save.setOnClickListener(view -> {
            mDialog.dismiss();
            listener.onSave(mFilename.getText(), mTypeSpinner.getSelectedItemPosition());
        });

        binding.cancel.setOnClickListener(view -> mDialog.dismiss());
    }

    private void setFilenameEditBoxFromName(boolean onlyIfNotEdited) {
        String baseName = mOriginalName != null ? mOriginalName : "";
        boolean trimmed;
        do {
            trimmed = false;
            for (String type : mTypeArray) {
                if (baseName.toLowerCase(Locale.ROOT)
                        .endsWith(" " + type.toLowerCase(Locale.ROOT))) {
                    baseName = baseName.substring(0, baseName.length() - (type.length() + 1));
                    trimmed = true;
                    break;
                } else if (baseName.toLowerCase(Locale.ROOT)
                        .endsWith("_" + type.toLowerCase(Locale.ROOT))) {
                    baseName = baseName.substring(0, baseName.length() - (type.length() + 1));
                    trimmed = true;
                    break;
                }
            }
        } while (trimmed);

        if (onlyIfNotEdited) {
            CharSequence currentText = mFilename.getText();
            String expectedText = baseName.isEmpty()
                    ? mTypeArray.get(mPreviousSelection)
                    : baseName + " " + mTypeArray.get(mPreviousSelection);

            if (!expectedText.contentEquals(currentText)) {
                return;
            }
        }

        int newSelection = mTypeSpinner.getSelectedItemPosition();
        String newSuffix = mTypeArray.get(newSelection);
        if (baseName.isEmpty()) {
            mFilename.setText(newSuffix);
        } else {
            mFilename.setText(
                    mContext.getString(R.string.filename_with_suffix, baseName, newSuffix));
        }
        mPreviousSelection = mTypeSpinner.getSelectedItemPosition();
    }

    public void show() {
        mDialog.show();
    }
}
