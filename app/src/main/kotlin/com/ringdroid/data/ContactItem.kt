package com.ringdroid.data

/**
 * Data model for a contact row.
 */
data class ContactItem(
    val id: Long,
    val displayName: String,
    val hasCustomRingtone: Boolean,
    val isStarred: Boolean,
)
