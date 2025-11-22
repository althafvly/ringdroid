package com.ringdroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ringdroid.repo.ContactsRepository

/**
 * Factory to create the ViewModel with repository dependency.
 */
class ChooseContactViewModelFactory(
    private val repository: ContactsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChooseContactViewModel::class.java)) {
            return ChooseContactViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
