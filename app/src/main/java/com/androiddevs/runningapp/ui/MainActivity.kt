package com.androiddevs.runningapp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.androiddevs.runningapp.R
import com.androiddevs.runningapp.other.Constants.Companion.ACTION_SHOW_TRACKING_FRAGMENT
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var name: String

    private var navHostFragment: NavHostFragment? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* El usuario puede activar/desactivar notificaciones desde Ajustes. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        val tvToolbarTitle = findViewById<TextView>(R.id.tvToolbarTitle)

        // Localizamos el host de navegación usando el ID de tu archivo XML 'activity_main.xml'
        navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment) as? NavHostFragment

        setSupportActionBar(toolbar)

        // Configuramos la navegación si el Host fue encontrado con éxito
        navHostFragment?.let { host ->
            bottomNavigationView.setupWithNavController(host.navController)

            bottomNavigationView.setOnNavigationItemReselectedListener { /* Sin accion al reseleccionar. */ }

            host.navController.addOnDestinationChangedListener { _, destination, _ ->
                when (destination.id) {
                    R.id.setupFragment2, R.id.trackingFragment, R.id.runDetailFragment -> bottomNavigationView.visibility = View.GONE
                    else -> bottomNavigationView.visibility = View.VISIBLE
                }
            }
        }

        navigateToTrackingFragmentIfNeeded(intent)

        if (name.isNotEmpty()) {
            val toolbarTitle = getString(R.string.toolbar_lets_go, name)
            tvToolbarTitle?.text = toolbarTitle
        }

        requestNotificationPermissionIfNeeded()
    }

    // Revisa si la actividad fue abierta desde la notificacion de seguimiento.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        navigateToTrackingFragmentIfNeeded(intent)
    }

    private fun navigateToTrackingFragmentIfNeeded(intent: Intent?) {
        if (intent?.action == ACTION_SHOW_TRACKING_FRAGMENT) {
            navHostFragment?.navController?.navigate(R.id.action_global_trackingFragment)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val permission = Manifest.permission.POST_NOTIFICATIONS
        val hasPermission = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            notificationPermissionLauncher.launch(permission)
        }
    }
}
