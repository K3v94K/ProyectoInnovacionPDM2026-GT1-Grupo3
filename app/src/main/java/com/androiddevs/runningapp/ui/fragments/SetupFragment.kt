package com.androiddevs.runningapp.ui.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.androiddevs.runningapp.R
import com.androiddevs.runningapp.other.Constants.Companion.KEY_FIRST_TIME_TOGGLE
import com.androiddevs.runningapp.other.Constants.Companion.KEY_NAME
import com.androiddevs.runningapp.other.Constants.Companion.KEY_WEIGHT
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SetupFragment : Fragment(R.layout.fragment_setup) {

    @Inject
    lateinit var sharedPref: SharedPreferences

    @set:Inject
    var firstTimeAppOpen: Boolean = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Obtiene los campos necesarios para registrar los datos iniciales del usuario.
        val btnContinue = view.findViewById<Button>(R.id.btnContinue)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etWeight = view.findViewById<EditText>(R.id.etWeight)

        if (!firstTimeAppOpen) {
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.setupFragment2, true)
                .build()
            findNavController().navigate(
                R.id.action_setupFragment2_to_runFragment2,
                savedInstanceState,
                navOptions
            )
        }

        // Valida y guarda los datos personales antes de entrar al historial.
        btnContinue.setOnClickListener {
            val success = writePersonalDataToSharedPref(etName, etWeight)
            if (success) {
                findNavController().navigate(R.id.action_setupFragment2_to_runFragment2)
            } else {
                Snackbar.make(requireView(), getString(R.string.fill_all_fields), Snackbar.LENGTH_SHORT)
                    .show()
            }
        }
    }

    /**
     * Guarda el nombre y el peso del usuario en SharedPreferences.
     */
    private fun writePersonalDataToSharedPref(etName: EditText, etWeight: EditText): Boolean {
        val name = etName.text.toString()
        val weightText = etWeight.text.toString()
        if (name.isEmpty() || weightText.isEmpty()) {
            return false
        }

        val weightValue = weightText.toFloatOrNull() ?: return false

        sharedPref.edit()
            .putString(KEY_NAME, name)
            .putFloat(KEY_WEIGHT, weightValue)
            .putBoolean(KEY_FIRST_TIME_TOGGLE, false)
            .apply()

        val toolbarText = getString(R.string.toolbar_lets_go, name)

        activity?.findViewById<Toolbar>(R.id.toolbar)?.let { toolbar ->
            val tvToolbarTitle = toolbar.findViewById<TextView>(R.id.tvToolbarTitle)
            tvToolbarTitle?.text = toolbarText
        }

        return true
    }
}
