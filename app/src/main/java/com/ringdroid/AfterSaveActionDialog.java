/*
 * Copyright (C) 2009 Google Inc.
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

import android.content.Context;
import android.os.Message;
import android.view.LayoutInflater;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.ringdroid.databinding.AfterSaveActionBinding;

public class AfterSaveActionDialog {

    private final Message mResponse;
    private final AlertDialog mDialog;

    public AfterSaveActionDialog(Context context, Message response) {

        // Inflate our UI from its XML layout description.
        AfterSaveActionBinding binding = AfterSaveActionBinding.inflate(LayoutInflater.from(context));
        
        mDialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.alert_title_success)
                .setMessage(R.string.what_to_do_with_ringtone)
                .setView(binding.getRoot())
                .create();

        binding.buttonMakeDefault.setOnClickListener(view -> closeAndSendResult(R.id.button_make_default));
        binding.buttonChooseContact
                .setOnClickListener(view -> closeAndSendResult(R.id.button_choose_contact));
        binding.buttonDoNothing.setOnClickListener(view -> closeAndSendResult(R.id.button_do_nothing));

        mResponse = response;
    }

    private void closeAndSendResult(int clickedButtonId) {
        mResponse.arg1 = clickedButtonId;
        mResponse.sendToTarget();
        mDialog.dismiss();
    }
    
    public void show() {
        mDialog.show();
    }
}
