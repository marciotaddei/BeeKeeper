package com.example.beekeeper

import android.os.Bundle
import androidx.core.content.edit
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        findPreference<Preference>("back_button")?.setOnPreferenceClickListener {
            requireActivity()
                .onBackPressedDispatcher
                .onBackPressed()
            true
        }

        findPreference<Preference>("reset_button")?.setOnPreferenceClickListener {
            (activity as? MainActivity)?.clearTableLetters()
            preferenceManager.sharedPreferences!!.edit{ clear()?.apply()}

            parentFragmentManager.popBackStack(
                null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .addToBackStack(null)
                .commit()
            true
        }

        findPreference<Preference>("quit_button")?.setOnPreferenceClickListener {
            requireActivity().finishAffinity() // Closes the app (or at least all activities)
            true
        }

    }


}