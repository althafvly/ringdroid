package com.ringdroid.select;

import com.ringdroid.util.RingdroidUtils;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;

import com.ringdroid.databinding.ToolbarSearchBinding;

/**
 * Reusable helper to manage modern toolbar search bar behavior, input changes, keyboard toggling,
 * and back-press dismissal.
 */
public class SearchToolbarHelper {

    public interface SearchCallback {
        void onQueryChanged(String query);
    }

    private final ComponentActivity mActivity;
    private final View mNormalToolbar;
    private final ToolbarSearchBinding mSearchBinding;
    private final SearchCallback mCallback;

    public SearchToolbarHelper(ComponentActivity activity, View normalToolbar,
            ToolbarSearchBinding searchBinding, View searchTriggerButton, SearchCallback callback) {
        mActivity = activity;
        mNormalToolbar = normalToolbar;
        mSearchBinding = searchBinding;
        mCallback = callback;

        if (searchTriggerButton != null) {
            searchTriggerButton.setOnClickListener(v -> openSearch());
        }

        mSearchBinding.btnSearchBack.setOnClickListener(v -> closeSearch());
        mSearchBinding.btnSearchClear
                .setOnClickListener(v -> mSearchBinding.searchQueryInput.setText(""));

        mSearchBinding.searchQueryInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mSearchBinding.btnSearchClear
                        .setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                if (mCallback != null) {
                    mCallback.onQueryChanged(s != null ? s.toString() : "");
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        mSearchBinding.searchQueryInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                RingdroidUtils.hideKeyboard(v);
                return true;
            }
            return false;
        });

        mActivity.getOnBackPressedDispatcher().addCallback(mActivity,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (isSearchOpen()) {
                            closeSearch();
                        } else {
                            setEnabled(false);
                            mActivity.getOnBackPressedDispatcher().onBackPressed();
                            setEnabled(true);
                        }
                    }
                });
    }

    public boolean isSearchOpen() {
        return mSearchBinding.getRoot().getVisibility() == View.VISIBLE;
    }

    public void openSearch() {
        mNormalToolbar.setVisibility(View.GONE);
        mSearchBinding.getRoot().setVisibility(View.VISIBLE);
        RingdroidUtils.showKeyboard(mSearchBinding.searchQueryInput);
    }

    public void closeSearch() {
        mSearchBinding.searchQueryInput.setText("");
        RingdroidUtils.hideKeyboard(mSearchBinding.searchQueryInput);
        mSearchBinding.getRoot().setVisibility(View.GONE);
        mNormalToolbar.setVisibility(View.VISIBLE);
    }

    public String getQuery() {
        return mSearchBinding.searchQueryInput.getText().toString();
    }

    public String getActiveQuery() {
        if (isSearchOpen()) {
            String query = getQuery().trim();
            return query.isEmpty() ? null : query;
        }
        return null;
    }
}
