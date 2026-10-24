package com.ringdroid.util;

import android.app.AlertDialog;
import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;

import androidx.core.text.HtmlCompat;

import com.ringdroid.BuildConfig;
import com.ringdroid.R;
import com.ringdroid.databinding.AboutDialogBinding;

public final class AboutDialog {
    private AboutDialog() {
    }

    public static void show(Context context) {
        AboutDialogBinding binding = AboutDialogBinding.inflate(LayoutInflater.from(context));
        binding.aboutVersion
                .setText(context.getString(R.string.version_label, BuildConfig.VERSION_NAME));
        String html = context.getString(R.string.about_text_html);
        binding.aboutText
                .setText(HtmlCompat.fromHtml(html.trim(), HtmlCompat.FROM_HTML_MODE_LEGACY));
        binding.aboutText.setMovementMethod(LinkMovementMethod.getInstance());

        AlertDialog dialog = new AlertDialog.Builder(context).setView(binding.getRoot()).create();
        RingdroidUtils.setTransparentBackground(dialog);
        binding.buttonAboutOk.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
