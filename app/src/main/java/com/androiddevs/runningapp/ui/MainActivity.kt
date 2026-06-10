package com.androiddevs.runningapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
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

            bottomNavigationView.setOnNavigationItemReselectedListener { /* NO-OP */ }

            host.navController.addOnDestinationChangedListener { _, destination, _ ->
                when (destination.id) {
                    R.id.setupFragment2, R.id.trackingFragment -> bottomNavigationView.visibility = View.GONE
                    else -> bottomNavigationView.visibility = View.VISIBLE
                }
            }
        }

        navigateToTrackingFragmentIfNeeded(intent)

        if (name.isNotEmpty()) {
            val toolbarTitle = "Let's go, $name!"
            tvToolbarTitle?.text = toolbarTitle
        }
    }

    // Checks if we launched the activity from the notification
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        navigateToTrackingFragmentIfNeeded(intent)
    }

    private fun navigateToTrackingFragmentIfNeeded(intent: Intent?) {
        if (intent?.action == ACTION_SHOW_TRACKING_FRAGMENT) {
            navHostFragment?.navController?.navigate(R.id.action_global_trackingFragment)
        }
    }
}