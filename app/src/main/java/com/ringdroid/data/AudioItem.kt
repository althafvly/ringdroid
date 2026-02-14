package com.ringdroid.data

data class AudioItem(
    val id: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val duration: Long,
    val mimeType: String?,
    val path: String?,
    val isInternal: Boolean,
    val isRingtone: Boolean,
    val isAlarm: Boolean,
    val isNotification: Boolean,
    val isMusic: Boolean,
)
