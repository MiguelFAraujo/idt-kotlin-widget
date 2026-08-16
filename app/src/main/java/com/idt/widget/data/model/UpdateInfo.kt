package com.idt.widget.data.model

data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val changelog: String = "",
    val timestamp: Long = 0L,
) {
    /** versionCode é a fonte de verdade (nome pode variar entre "v1.0" e "1.0"). */
    fun isNewerThan(currentName: String, currentCode: Int): Boolean =
        versionCode > currentCode
}
