package com.ringdroid.data

data class ContactItem(
    val id: String,
    val name: String,
    val starred: Boolean,
    val hasCustomRingtone: Boolean,
)
