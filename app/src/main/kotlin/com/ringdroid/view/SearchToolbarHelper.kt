package com.ringdroid.view

import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import com.ringdroid.databinding.ToolbarSearchBinding
import com.ringdroid.util.RingdroidUtils

/** Swaps the toolbar for a search field and reports query changes. Back closes the search first. */
class SearchToolbarHelper(
    activity: ComponentActivity,
    private val normalToolbar: View,
    private val searchBinding: ToolbarSearchBinding,
    searchTriggerButton: View?,
    onQueryChanged: (String) -> Unit,
) {
    init {
        searchTriggerButton?.setOnClickListener { openSearch() }
        searchBinding.btnSearchBack.setOnClickListener { closeSearch() }
        searchBinding.btnSearchClear.setOnClickListener {
            searchBinding.searchQueryInput.setText(
                "",
            )
        }
        searchBinding.searchQueryInput.doOnTextChanged { text, _, _, _ ->
            searchBinding.btnSearchClear.isVisible = !text.isNullOrEmpty()
            onQueryChanged(text?.toString().orEmpty())
        }
        searchBinding.searchQueryInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                RingdroidUtils.hideKeyboard(v)
                true
            } else {
                false
            }
        }
        activity.onBackPressedDispatcher.addCallback(
            activity,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isSearchOpen) {
                        closeSearch()
                    } else {
                        isEnabled = false
                        activity.onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            },
        )
    }

    val isSearchOpen: Boolean
        get() = searchBinding.root.isVisible

    fun openSearch() {
        normalToolbar.isVisible = false
        searchBinding.root.isVisible = true
        RingdroidUtils.showKeyboard(searchBinding.searchQueryInput)
    }

    fun closeSearch() {
        searchBinding.searchQueryInput.setText("")
        RingdroidUtils.hideKeyboard(searchBinding.searchQueryInput)
        searchBinding.root.isVisible = false
        normalToolbar.isVisible = true
    }

    /** The trimmed search text while the search is open and not empty, otherwise null. */
    val activeQuery: String?
        get() =
            if (isSearchOpen) {
                searchBinding.searchQueryInput.text
                    .toString()
                    .trim()
                    .ifEmpty { null }
            } else {
                null
            }
}
