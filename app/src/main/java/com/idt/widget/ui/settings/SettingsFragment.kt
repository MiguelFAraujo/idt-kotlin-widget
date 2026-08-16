package com.idt.widget.ui.settings

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.idt.widget.BuildConfig
import com.idt.widget.R
import com.idt.widget.data.local.ConfigDataSource
import com.idt.widget.util.ChangelogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {

    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val appContext = requireActivity().applicationContext
        val configDataSource = ConfigDataSource(appContext)
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(appContext)

        // Sobre: versão atual + changelog + links
        findPreference<Preference>("about_version")?.summary = "v${BuildConfig.VERSION_NAME}"
        findPreference<Preference>("about_changelog")?.setOnPreferenceClickListener {
            ChangelogHelper.showDialog(requireContext(), BuildConfig.VERSION_NAME)
            true
        }
        findPreference<Preference>("about_repo")?.setOnPreferenceClickListener {
            openUrl("https://github.com/MiguelFAraujo/idt-kotlin-widget")
            true
        }
        findPreference<Preference>("about_site")?.setOnPreferenceClickListener {
            openUrl("https://inteligenciadotopo.com.br")
            true
        }
        findPreference<Preference>("open_webdav")?.setOnPreferenceClickListener {
            openConfiguredWebDav()
            true
        }

        // Sync preference display values from the app config, guarding against a destroyed host.
        val fragment = this
        lifecycleScope.launch(Dispatchers.IO) {
            val config = configDataSource.getConfig()
            val activity = fragment.activity ?: return@launch
            activity.runOnUiThread {
                if (fragment.isAdded) {
                    findPreference<EditTextPreference>("server_url")?.setText(config.serverUrl)
                    findPreference<EditTextPreference>("server_user")?.setText(config.serverUser)
                    findPreference<EditTextPreference>("server_pass")?.setText(config.serverPass)
                    findPreference<EditTextPreference>("server_path")?.setText(config.serverPath)
                    findPreference<SwitchPreferenceCompat>("use_webdav")?.isChecked = config.useWebDav
                    findPreference<SwitchPreferenceCompat>("use_fingerprint")?.isChecked = config.useFingerprint
                }
            }
        }

        // Persist every supported change back into the app config. The refresh_interval
        // preference is stored as a String by ListPreference, so never read it as Long.
        val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key !in SUPPORTED_KEYS) return@OnSharedPreferenceChangeListener
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val currentConfig = configDataSource.getConfig()
                    val newConfig = currentConfig.copy(
                        serverUrl = prefs.getString("server_url", currentConfig.serverUrl) ?: currentConfig.serverUrl,
                        serverUser = prefs.getString("server_user", currentConfig.serverUser) ?: currentConfig.serverUser,
                        serverPass = prefs.getString("server_pass", currentConfig.serverPass) ?: currentConfig.serverPass,
                        serverPath = prefs.getString("server_path", currentConfig.serverPath) ?: currentConfig.serverPath,
                        useWebDav = prefs.getBoolean("use_webdav", currentConfig.useWebDav),
                        autoRefresh = prefs.getBoolean("auto_refresh", currentConfig.autoRefresh),
                        refreshIntervalSeconds = prefs.getString("refresh_interval", null)?.toLongOrNull()
                            ?: currentConfig.refreshIntervalSeconds,
                        showNotifications = prefs.getBoolean("show_notifications", currentConfig.showNotifications),
                        compactView = prefs.getBoolean("compact_view", currentConfig.compactView),
                        useFingerprint = prefs.getBoolean("use_fingerprint", currentConfig.useFingerprint),
                    )
                    configDataSource.saveConfig(newConfig)
                } catch (_: Exception) {
                    // Never crash the UI on a config write failure.
                }
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(changeListener)
        listener = changeListener
    }

    override fun onDestroy() {
        listener?.let { l ->
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .unregisterOnSharedPreferenceChangeListener(l)
        }
        listener = null
        super.onDestroy()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            // Sem navegador disponível — ignora silenciosamente
        }
    }

    private fun openConfiguredWebDav() {
        lifecycleScope.launch(Dispatchers.IO) {
            val config = ConfigDataSource(requireActivity().applicationContext).getConfig()
            val base = config.serverUrl.trimEnd('/')
            val path = config.serverPath.takeIf { it.isNotBlank() }?.trimStart('/') ?: ""
            val url = if (path.isNotEmpty()) "$base/$path" else "$base/"
            val activity = activity ?: return@launch
            activity.runOnUiThread { openUrl(url) }
        }
    }

    companion object {
        private val SUPPORTED_KEYS = setOf(
            "server_url", "server_user", "server_pass", "server_path",
            "use_webdav", "use_fingerprint", "auto_refresh",
            "refresh_interval", "show_notifications", "compact_view",
        )
    }
}
