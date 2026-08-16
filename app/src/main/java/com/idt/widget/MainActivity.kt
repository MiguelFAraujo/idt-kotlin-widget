package com.idt.widget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.idt.widget.data.local.ConfigDataSource
import com.idt.widget.util.ChangelogHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    var onRefreshRequested: (() -> Unit)? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* aceito ou não */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        // Android 13+: permissão para mostrar notificações (alertas de serviço e updates)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Popup de notas da versão na primeira execução após uma atualização
        ChangelogHelper.showIfNew(this)

        val host = supportFragmentManager
            .findFragmentById(R.id.navHost) as NavHostFragment
        navController = host.navController

        val appBarConfig = AppBarConfiguration(
            setOf(R.id.dashboardFragment)
        )
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfig)

        // Check if connection is configured - wait for navController to be ready
        lifecycleScope.launch {
            try {
                val config = ConfigDataSource(this@MainActivity).getConfig()
                if (!config.connectionConfigured) {
                    // Stay on connection fragment (it's the start destination)
                } else if (navController.currentDestination?.id != R.id.dashboardFragment) {
                    // Navigate to dashboard only if not already there
                    navController.navigate(R.id.action_connection_to_dashboard)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Config check failed", e)
                // On error, stay on connection fragment
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d("IDT_MAIN", "onOptionsItemSelected: ${item.itemId}")
        return when (item.itemId) {
            R.id.action_refresh -> {
                Log.d("IDT_MAIN", "action_refresh: callback ${if (onRefreshRequested == null) "NULL" else "OK"}")
                onRefreshRequested?.invoke()
                true
            }
            R.id.action_settings -> {
                navController.navigate(R.id.action_dashboard_to_settings)
                true
            }
            R.id.action_endpoints -> {
                navController.navigate(R.id.action_dashboard_to_endpoints)
                true
            }
            R.id.action_diagnostics -> {
                navController.navigate(R.id.action_dashboard_to_diagnostics)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, AppBarConfiguration(setOf(R.id.dashboardFragment)))
                || super.onSupportNavigateUp()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Evita que o fragmento destruído receba chamadas de refresh
        onRefreshRequested = null
    }
}
