package com.idt.widget.ui.settings

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.idt.widget.BuildConfig
import com.idt.widget.R
import com.idt.widget.data.local.ConfigDataSource
import com.idt.widget.data.remote.UpdateChecker
import com.idt.widget.update.UpdateNowReceiver
import com.idt.widget.util.ChangelogHelper
import com.idt.widget.util.ConnectionSyncHelper
import com.idt.widget.util.NotificationHelper
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
        findPreference<Preference>("check_updates")?.setOnPreferenceClickListener {
            checkForUpdates()
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
                    findPreference<SwitchPreferenceCompat>("auto_update")?.isChecked = config.autoUpdate
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
                        autoUpdate = prefs.getBoolean("auto_update", currentConfig.autoUpdate),
                    )
                    configDataSource.saveConfig(newConfig)
                    // Endereço ou credenciais mudaram => re-sincroniza com o host configurado
                    // e limpa o cache de status (nada de resultado inventado/antigo na tela).
                    ConnectionSyncHelper.resyncAndRefresh(requireContext())
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

    /** Verificação manual de atualização: mostra resultado imediato no toast. */
    private fun checkForUpdates() {
        val activity = activity ?: return
        val appContext = activity.applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            val result = try {
                UpdateChecker().check(appContext)
            } catch (e: Exception) {
                null
            }
            val isUpdate = result != null &&
                result.isNewerThan(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            val autoUpdate = ConfigDataSource(appContext).current().autoUpdate
            activity.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                val message = if (isUpdate) {
                    val update = result!!
                    if (autoUpdate) {
                        UpdateNowReceiver.trigger(appContext, update.apkUrl, update.versionName)
                    } else {
                        NotificationHelper.notifyUpdate(appContext, update.versionName, update.apkUrl, update.changelog)
                    }
                    getString(R.string.check_updates_found, update.versionName)
                } else {
                    getString(R.string.check_updates_done, BuildConfig.VERSION_NAME)
                }
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            }
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
            "auto_update",
        )
    }
}
