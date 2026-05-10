package com.moqi.im.settings

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.moqi.im.R
import com.moqi.im.engine.MoqiImeSession
import com.moqi.im.engine.RimeSchemaEntry
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import mobilebridge.Mobilebridge

class SettingsFragment : PreferenceFragmentCompat() {
    companion object {
        private const val DEFAULT_KEYBOARD_HEIGHT_PERCENT = 100
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var executor: ExecutorService? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "moqi_im_prefs"
        setPreferencesFromResource(R.xml.preferences, rootKey)
        executor = Executors.newSingleThreadExecutor()

        val modePref = findPreference<ListPreference>("input_mode")
        modePref?.setOnPreferenceChangeListener { _, newValue ->
            val prefs = requireActivity().getSharedPreferences("moqi_im_prefs", 0)
            prefs.edit().putString("input_mode", newValue as String).apply()
            true
        }

        findPreference<ListPreference>("rime_scheme_set")?.setOnPreferenceChangeListener { _, newValue ->
            selectRimeSchemeSet(newValue as String)
            true
        }

        findPreference<ListPreference>("rime_schema")?.setOnPreferenceChangeListener { _, newValue ->
            selectRimeSchema(newValue as String)
            true
        }

        val keyboardHeightPref = findPreference<SeekBarPreference>("keyboard_height")
        keyboardHeightPref?.let { pref ->
            updateKeyboardHeightSummary(pref, pref.value)
            pref.setOnPreferenceChangeListener { preference, newValue ->
                updateKeyboardHeightSummary(preference, newValue as Int)
                true
            }
        }

        findPreference<Preference>("keyboard_height_reset")?.setOnPreferenceClickListener {
            keyboardHeightPref?.value = DEFAULT_KEYBOARD_HEIGHT_PERCENT
            keyboardHeightPref?.let { pref ->
                updateKeyboardHeightSummary(pref, DEFAULT_KEYBOARD_HEIGHT_PERCENT)
            }
            Toast.makeText(requireContext(), R.string.pref_keyboard_height_reset_done, Toast.LENGTH_SHORT).show()
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

        loadRimePreferences()
    }

    private fun updateKeyboardHeightSummary(pref: Preference, percent: Int) {
        pref.summary = getString(R.string.pref_keyboard_height_summary, percent)
    }

    override fun onDestroy() {
        executor?.shutdown()
        executor = null
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun loadRimePreferences() {
        val context = context?.applicationContext ?: return
        val schemeSetPref = findPreference<ListPreference>("rime_scheme_set")
        val schemaPref = findPreference<ListPreference>("rime_schema")
        schemeSetPref?.isEnabled = false
        schemaPref?.isEnabled = false
        executor?.execute {
            val session = MoqiImeSession(
                guid = Mobilebridge.GUIDRime,
                androidDataDir = context.filesDir.absolutePath
            )
            val schemeSets = session.schemeSets()
            val currentSchemeSet = session.currentSchemeSet()
            val schemas = session.schemaEntries()
            val currentSchemaId = session.currentSchemaId()
            session.close()
            mainHandler.post {
                if (!isAdded) return@post
                updateSchemeSetPreference(schemeSetPref, schemeSets, currentSchemeSet)
                updateSchemaPreference(schemaPref, schemas, currentSchemaId)
            }
        }
    }

    private fun selectRimeSchemeSet(name: String) {
        val context = context?.applicationContext ?: return
        executor?.execute {
            val session = MoqiImeSession(
                guid = Mobilebridge.GUIDRime,
                androidDataDir = context.filesDir.absolutePath
            )
            val result = session.selectSchemeSet(name)
            session.close()
            mainHandler.post {
                if (!isAdded) return@post
                if (!result.success) {
                    Toast.makeText(requireContext(), "${getString(R.string.pref_rime_switch_failed)}: ${result.error}", Toast.LENGTH_LONG).show()
                }
                loadRimePreferences()
            }
        }
    }

    private fun selectRimeSchema(schemaId: String) {
        val context = context?.applicationContext ?: return
        executor?.execute {
            val session = MoqiImeSession(
                guid = Mobilebridge.GUIDRime,
                androidDataDir = context.filesDir.absolutePath
            )
            val result = session.selectSchema(schemaId)
            session.close()
            mainHandler.post {
                if (!isAdded) return@post
                if (!result.success) {
                    Toast.makeText(requireContext(), "${getString(R.string.pref_rime_switch_failed)}: ${result.error}", Toast.LENGTH_LONG).show()
                }
                loadRimePreferences()
            }
        }
    }

    private fun updateSchemeSetPreference(pref: ListPreference?, schemeSets: List<String>, currentSchemeSet: String) {
        if (pref == null) return
        if (schemeSets.isEmpty()) {
            pref.summary = getString(R.string.pref_rime_empty)
            pref.isEnabled = false
            return
        }
        pref.entries = schemeSets.toTypedArray()
        pref.entryValues = schemeSets.toTypedArray()
        pref.value = currentSchemeSet.ifBlank { schemeSets.first() }
        pref.summary = pref.value
        pref.isEnabled = true
    }

    private fun updateSchemaPreference(pref: ListPreference?, schemas: List<RimeSchemaEntry>, currentSchemaId: String) {
        if (pref == null) return
        if (schemas.isEmpty()) {
            pref.summary = getString(R.string.pref_rime_empty)
            pref.isEnabled = false
            return
        }
        pref.entries = schemas.map { "${it.name} (${it.id})" }.toTypedArray()
        pref.entryValues = schemas.map { it.id }.toTypedArray()
        pref.value = currentSchemaId.ifBlank {
            schemas.firstOrNull { it.selected }?.id ?: schemas.first().id
        }
        pref.summary = schemas.firstOrNull { it.id == pref.value }?.name ?: pref.value
        pref.isEnabled = true
    }
}