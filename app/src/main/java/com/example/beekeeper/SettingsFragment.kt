package com.example.beekeeper

import android.os.Bundle
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.core.graphics.drawable.toDrawable

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        findPreference<Preference>("back_button")?.setOnPreferenceClickListener {
            requireActivity()
                .onBackPressedDispatcher
                .onBackPressed()
            true
        }

        findPreference<Preference>("information")?.setOnPreferenceClickListener {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle("Information")
                .setMessage("Scoring:\nMinimum-length words award 1 point. " +
                        "Otherwise, words award 1 point per letter. " +
                        "Pangrams award an additional 7-point bonus.\n\n" +
                        "Dictionaries:\n" +
                        "The Precise dictionary is the closest match to the official answers. "+
                        "The Mega dictionary almost certainly contains invalid words, " +
                        "the Gigantic one certainly does. "
                        )
                .setPositiveButton("OK", null)
                .show()
            dialog.window?.setBackgroundDrawable("#F7DA21".toColorInt().toDrawable())
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