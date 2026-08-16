// registro: 2026-08-16 | autor: Miguel | modelo: opencode/big-pickle
package com.idt.widget.util

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import com.idt.widget.BuildConfig
import com.idt.widget.R

/**
 * Mostra as notas da versão (popup na primeira execução após atualização e
 * dialog "Notas da versão" nas Configurações).
 */
object ChangelogHelper {
    private const val PREFS = "idt_changelog"
    private const val KEY_LAST_SEEN = "last_seen_version_code"

    /** Verdadeiro se a versão atual ainda não foi "vista" por este usuário. */
    fun isNewVersion(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getInt(KEY_LAST_SEEN, 0)
        return BuildConfig.VERSION_CODE > last
    }

    /** Marca a versão atual como vista (silencia o popup até a próxima atualização). */
    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LAST_SEEN, BuildConfig.VERSION_CODE).apply()
    }

    /** Mostra o diálogo das notas da versão atual. */
    fun showDialog(context: Context, versionName: String) {
        val notes = buildNotes(context)
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.changelog_title, versionName))
            .setMessage(notes)
            .setPositiveButton("OK", null)
            .show()
    }

    /** Mostra o popup apenas se houver uma versão nova; marca como vista. */
    fun showIfNew(context: Context) {
        if (isNewVersion(context)) {
            showDialog(context, BuildConfig.VERSION_NAME)
            markSeen(context)
        }
    }

    private fun buildNotes(context: Context): String {
        val items = context.resources.getStringArray(R.array.patch_notes)
        return if (items.size <= 1) "" else items.drop(1).joinToString("\n")
    }
}
