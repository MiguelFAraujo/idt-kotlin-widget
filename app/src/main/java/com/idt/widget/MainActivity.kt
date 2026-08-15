package com.idt.widget

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        val host = supportFragmentManager
            .findFragmentById(R.id.navHost) as NavHostFragment
        navController = host.navController

        val appBarConfig = AppBarConfiguration(
            setOf(R.id.dashboardFragment)
        )
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfig)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                navController.navigate(R.id.action_dashboard_to_endpoints)
                true
            }
            R.id.action_settings -> {
                navController.navigate(R.id.action_dashboard_to_settings)
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
}
