package com.idt.widget.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.idt.widget.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}
