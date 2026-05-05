package com.moqi.im.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.ListPreference
import com.moqi.im.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val modePref = findPreference<ListPreference>("input_mode")
        modePref?.setOnPreferenceChangeListener { _, newValue ->
            val prefs = requireActivity().getSharedPreferences("moqi_im_prefs", 0)
            prefs.edit().putString("input_mode", newValue as String).apply()
            true
        }

        val soundPref = findPreference<SwitchPreferenceCompat>("key_sound")
        soundPref?.setOnPreferenceChangeListener { _, newValue ->
            val prefs = requireActivity().getSharedPreferences("moqi_im_prefs", 0)
            prefs.edit().putBoolean("key_sound", newValue as Boolean).apply()
            true
        }

        val vibratePref = findPreference<SwitchPreferenceCompat>("key_vibrate")
        vibratePref?.setOnPreferenceChangeListener { _, newValue ->
            val prefs = requireActivity().getSharedPreferences("moqi_im_prefs", 0)
            prefs.edit().putBoolean("key_vibrate", newValue as Boolean).apply()
            true
        }

        val privacyPref = findPreference<androidx.preference.Preference>("privacy")
        privacyPref?.setOnPreferenceClickListener {
            Toast.makeText(requireContext(), R.string.pref_privacy_summary, Toast.LENGTH_LONG).show()
            true
        }
    }
}