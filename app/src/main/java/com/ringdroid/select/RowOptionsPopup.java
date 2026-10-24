package com.ringdroid.select;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupWindow;

import com.ringdroid.R;
import com.ringdroid.databinding.RowOptionsPopupBinding;

/** Popup with the actions available for one audio file in the list. */
final class RowOptionsPopup {
    private static final int WIDTH_DP = 260;

    interface Actions {
        void onEdit();

        void onSetDefault();

        void onAssignContact();

        void onDelete();
    }

    private RowOptionsPopup() {
    }

    static void show(View anchor, String title, boolean isRingtone, boolean isNotification,
            boolean canDelete, Actions actions) {
        RowOptionsPopupBinding binding = RowOptionsPopupBinding
                .inflate(LayoutInflater.from(anchor.getContext()));
        PopupWindow popup = Popups.create(binding.getRoot(), WIDTH_DP);
        binding.rowMenuTitle.setText(title != null ? title : "");
        bind(binding.menuItemEdit, true, popup, actions::onEdit);

        bind(binding.menuItemDefault, isRingtone || isNotification, popup, actions::onSetDefault);
        if (isRingtone) {
            binding.menuDefaultIcon.setImageResource(R.drawable.baseline_call_24);
            binding.menuDefaultText.setText(R.string.context_menu_default_ringtone);
        } else if (isNotification) {
            binding.menuDefaultIcon.setImageResource(R.drawable.baseline_notifications_24);
            binding.menuDefaultText.setText(R.string.context_menu_default_notification);
        }

        bind(binding.menuItemContact, isRingtone, popup, actions::onAssignContact);
        bind(binding.menuItemDelete, canDelete, popup, actions::onDelete);
        binding.deleteDivider.setVisibility(canDelete ? View.VISIBLE : View.GONE);

        Popups.showAlignedRight(popup, anchor, WIDTH_DP, 4);
    }

    private static void bind(View item, boolean visible, PopupWindow popup, Runnable action) {
        item.setVisibility(visible ? View.VISIBLE : View.GONE);
        item.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
    }
}
