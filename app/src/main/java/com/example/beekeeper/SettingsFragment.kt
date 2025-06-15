package com.example.beekeeper

import android.os.Bundle
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
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
            preferenceManager.sharedPreferences!!.edit{ clear()?.apply()}
            //undo and redo menu to insta-update
            parentFragmentManager.popBackStack(
                null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .addToBackStack(null)
                .commit()
            true
        }

        findPreference<Preference>("clear_button")?.setOnPreferenceClickListener {
            (activity as? MainActivity)?.clearTableLetters()
            true
        }

        findPreference<Preference>("quit_button")?.setOnPreferenceClickListener {
            Toast.makeText(requireActivity(), "See you later", Toast.LENGTH_SHORT).show()
            requireActivity().finishAffinity() // Closes the app (or at least all activities)
            true
        }

        findPreference<Preference>("about_scoring")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Scoring")
                .setMessage("Minimum-length words award 1 point; otherwise words award 1 point per letter. Pangrams award an additional 7-point bonus")
                .setPositiveButton("OK", null)
                .show()
            true
        }

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // AndroidX ID for the PreferenceFragmentCompat RecyclerView
        val rvId = androidx.preference.R.id.recycler_view

        view.findViewById<RecyclerView>(rvId)?.apply {
            setPaddingRelative(0, paddingTop, paddingEnd, paddingBottom)
            clipToPadding = false
        }
    }

}