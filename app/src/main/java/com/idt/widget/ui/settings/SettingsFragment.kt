package com.idt.widget.ui.settings

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.idt.widget.IDTApplication
import com.idt.widget.R
import com.idt.widget.data.local.ConfigDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val configDataSource = ConfigDataSource(requireActivity().applicationContext)

        // Load config asynchronously and sync preferences
        lifecycleScope.launch(Dispatchers.IO) {
            val config = configDataSource.getConfig()
            requireActivity().runOnUiThread {
                findPreference<EditTextPreference>("server_url")?.setText(config.serverUrl)
                findPreference<EditTextPreference>("server_user")?.setText(config.serverUser)
                findPreference<EditTextPreference>("server_pass")?.setText(config.serverPass)
                findPreference<EditTextPreference>("server_path")?.setText(config.serverPath)
                findPreference<SwitchPreferenceCompat>("use_webdav")?.isChecked = config.useWebDav
                findPreference<SwitchPreferenceCompat>("use_fingerprint")?.isChecked = config.useFingerprint
            }
        }

        // Listen for changes
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        sharedPrefs.registerOnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "server_url", "server_user", "server_pass", "server_path", "use_webdav", "use_fingerprint",
                "auto_refresh", "refresh_interval", "show_notifications", "compact_view" -> {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val currentConfig = configDataSource.getConfig()
                        val newConfig = currentConfig.copy(
                            serverUrl = sharedPrefs.getString("server_url", currentConfig.serverUrl) ?: currentConfig.serverUrl,
                            serverUser = sharedPrefs.getString("server_user", currentConfig.serverUser) ?: currentConfig.serverUser,
                            serverPass = sharedPrefs.getString("server_pass", currentConfig.serverPass) ?: currentConfig.serverPass,
                            serverPath = sharedPrefs.getString("server_path", currentConfig.serverPath) ?: currentConfig.serverPath,
                            useWebDav = sharedPrefs.getBoolean("use_webdav", currentConfig.useWebDav),
                            autoRefresh = sharedPrefs.getBoolean("auto_refresh", currentConfig.autoRefresh),
                            refreshIntervalSeconds = sharedPrefs.getLong("refresh_interval", currentConfig.refreshIntervalSeconds),
                            showNotifications = sharedPrefs.getBoolean("show_notifications", currentConfig.showNotifications),
                            compactView = sharedPrefs.getBoolean("compact_view", currentConfig.compactView),
                            useFingerprint = sharedPrefs.getBoolean("use_fingerprint", currentConfig.useFingerprint),
                        )
                        configDataSource.saveConfig(newConfig)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener { _, _ -> }
    }
}
