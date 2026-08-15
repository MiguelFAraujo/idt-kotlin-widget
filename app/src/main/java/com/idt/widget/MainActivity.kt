package com.idt.widget

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnRefresh: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnRefresh = findViewById(R.id.btnRefresh)

        btnRefresh.setOnClickListener { refreshStatus() }

        refreshStatus()
    }

    private fun refreshStatus() {
        tvStatus.text = "Verificando..."
        lifecycleScope.launch(Dispatchers.IO) {
            val services = listOf(
                Service("OmniRoute", "127.0.0.1", 20128),
                Service("Ollama", "127.0.0.1", 11434),
                Service("Prometheus", "127.0.0.1", 9091),
                Service("Netdata", "127.0.0.1", 19999),
                Service("n8n", "127.0.0.1", 5678),
                Service("ntfy", "127.0.0.1", 2586),
                Service("Portainer", "127.0.0.1", 9443),
                Service("Filebrowser", "127.0.0.1", 8083),
                Service("Nextcloud", "127.0.0.1", 8081),
                Service("Grafana", "127.0.0.1", 3030),
                Service("MinIO", "127.0.0.1", 9001),
                Service("Paperless", "127.0.0.1", 8000),
            )

            val results = services.map { it.check() }
            val overall = if (results.all { it.ok }) "TUDO OK ✅" else "ALGUNS DOWN ⚠️"
            val text = results.joinToString("\n") { "${if (it.ok) "●" else "○"} ${it.name}: ${if (it.ok) "ONLINE" else "OFFLINE"}" }

            withContext(Dispatchers.Main) {
                tvStatus.text = "$overall\n\n$text"
            }
        }
    }

    data class Service(val name: String, val host: String, val port: Int) {
        data class CheckResult(val name: String, val ok: Boolean)
        fun check(): CheckResult {
            return try {
                Socket().use { it.connect(InetSocketAddress(host, port), 2000) }
                CheckResult(name, true)
            } catch (e: Exception) {
                CheckResult(name, false)
            }
        }
    }
}
