package com.ringdroid.select;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

/** Shared setup for the dropdown popups on the select screen. */
final class Popups {
    private Popups() {
    }

    static PopupWindow create(View contentView, int widthDp) {
        float density = contentView.getResources().getDisplayMetrics().density;
        PopupWindow popupWindow = new PopupWindow(contentView, (int) (widthDp * density),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(12 * density);
        popupWindow.setAnimationStyle(android.R.style.Animation_Dialog);
        return popupWindow;
    }

    static void showAlignedRight(PopupWindow popup, View anchor, int widthDp, int yOffsetDp) {
        float density = anchor.getResources().getDisplayMetrics().density;
        int xOffset = anchor.getWidth() - (int) (widthDp * density) - (int) (8 * density);
        popup.showAsDropDown(anchor, xOffset, (int) (yOffsetDp * density));
    }
}
