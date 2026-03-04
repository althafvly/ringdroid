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

import android.app.AlertDialog;
import android.content.Context;
import android.os.Message;
import android.view.LayoutInflater;

import com.ringdroid.databinding.AfterSaveActionBinding;

public class AfterSaveActionDialog {

    public static final int ACTION_MAKE_DEFAULT = 1;
    public static final int ACTION_CHOOSE_CONTACT = 2;
    public static final int ACTION_DO_NOTHING = 3;

    private final Message mResponse;
    private final AlertDialog mDialog;

    public AfterSaveActionDialog(Context context, Message response) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.alert_title_success);

        // Inflate our UI from its XML layout description.
        AfterSaveActionBinding binding = AfterSaveActionBinding.inflate(LayoutInflater.from(context));
        builder.setView(binding.getRoot());

        binding.buttonMakeDefault.setOnClickListener(view -> closeAndSendResult(ACTION_MAKE_DEFAULT));
        binding.buttonChooseContact.setOnClickListener(view -> closeAndSendResult(ACTION_CHOOSE_CONTACT));
        binding.buttonDoNothing.setOnClickListener(view -> closeAndSendResult(ACTION_DO_NOTHING));

        mResponse = response;
        mDialog = builder.create();
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
