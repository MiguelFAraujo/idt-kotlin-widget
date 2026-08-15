package com.idt.widget.data.model

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val changelog: String = "",
    val timestamp: Long = 0L,
) {
    fun isNewerThan(currentName: String, currentCode: Int): Boolean =
        versionCode > currentCode && versionName != currentName
}
