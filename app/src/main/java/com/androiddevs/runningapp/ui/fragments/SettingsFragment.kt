package com.androiddevs.runningapp.ui.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button // 🌟 NUEVO
import android.widget.EditText // 🌟 NUEVO
import android.widget.TextView // 🌟 NUEVO
import androidx.appcompat.widget.Toolbar // 🌟 NUEVO
import androidx.fragment.app.Fragment
import com.androiddevs.runningapp.R
import com.androiddevs.runningapp.other.Constants.Companion.KEY_NAME
import com.androiddevs.runningapp.other.Constants.Companion.KEY_WEIGHT
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    @Inject
    lateinit var sharedPref: SharedPreferences

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 🌟 CORREGIDO: Encontrar las vistas locales pasándole la raíz 'view'
        val etName = view.findViewById<EditText>(R.id.etName)
        val etWeight = view.findViewById<EditText>(R.id.etWeight)
        val btnApplyChanges = view.findViewById<Button>(R.id.btnApplyChanges)

        loadFieldsFromSharedPref(etName, etWeight)

        btnApplyChanges.setOnClickListener {
            val success = applyChangesToSharedPref(etName, etWeight)
            if(success) {
                Snackbar.make(requireView(), getString(R.string.save_changes_success), Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(requireView(), getString(R.string.fill_all_fields), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    // 🌟 CORREGIDO: Ahora recibe las referencias explícitas de los EditText
    private fun loadFieldsFromSharedPref(etName: EditText, etWeight: EditText) {
        val name = sharedPref.getString(KEY_NAME, "")
        val weight = sharedPref.getFloat(KEY_WEIGHT, 80f)
        etName.setText(name)
        etWeight.setText(weight.toString())
    }

    // 🌟 CORREGIDO: Ahora procesa las vistas de forma segura e independiente
    private fun applyChangesToSharedPref(etName: EditText, etWeight: EditText): Boolean {
        val nameText = etName.text.toString()
        val weightText = etWeight.text.toString()
        if(nameText.isEmpty() || weightText.isEmpty()) {
            return false
        }

        val weightValue = weightText.toFloatOrNull() ?: return false

        sharedPref.edit()
            .putString(KEY_NAME, nameText)
            .putFloat(KEY_WEIGHT, weightValue)
            .apply()

        val toolbarText = getString(R.string.toolbar_lets_go, nameText)

        // 🌟 CORREGIDO: Encontramos la Toolbar y su TextView de manera segura y sin Synthetics
        activity?.findViewById<Toolbar>(R.id.toolbar)?.let { toolbar ->
            val tvToolbarTitle = toolbar.findViewById<TextView>(R.id.tvToolbarTitle)
            tvToolbarTitle?.text = toolbarText
        }

        return true
    }
}
