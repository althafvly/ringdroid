package com.ringdroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ringdroid.data.ContactItem
import com.ringdroid.repo.ContactsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel holding contacts as a StateFlow.
 */
class ChooseContactViewModel(
    private val repository: ContactsRepository,
) : ViewModel() {
    private val _contacts = MutableStateFlow<List<ContactItem>>(emptyList())
    val contacts: StateFlow<List<ContactItem>> = _contacts.asStateFlow()

    fun loadContacts(filter: String?) {
        viewModelScope.launch {
            val list = repository.loadContacts(filter)
            _contacts.value = list
        }
    }
}
