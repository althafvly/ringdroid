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

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Context;
import android.os.Message;

public class AfterSaveActionDialog {

    public static final int ACTION_MAKE_DEFAULT = 1;
    public static final int ACTION_CHOOSE_CONTACT = 2;
    public static final int ACTION_DO_NOTHING = 3;

    private final Message mResponse;
    private final AlertDialog mDialog;

    public AfterSaveActionDialog(Context context, Message response) {
        mResponse = response;

        int[] actions = {
                ACTION_MAKE_DEFAULT,
                ACTION_CHOOSE_CONTACT,
                ACTION_DO_NOTHING
        };

        CharSequence[] items = {
                context.getString(R.string.make_default_ringtone_button),
                context.getString(R.string.choose_contact_ringtone_button),
                context.getString(R.string.do_nothing_with_ringtone_button)
        };

        mDialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.alert_title_success)
                .setItems(items, (dialog, which) -> closeAndSendResult(actions[which]))
                .create();
    }

    private void closeAndSendResult(int action) {
        mResponse.arg1 = action;
        mResponse.sendToTarget();
        mDialog.dismiss();
    }

    public void show() {
        mDialog.show();
    }
}
