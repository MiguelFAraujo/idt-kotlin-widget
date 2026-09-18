package com.idt.widget.ui.agents

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.idt.widget.R
import com.idt.widget.data.remote.ServiceCatalog
import com.idt.widget.databinding.FragmentAgentsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class AgentsFragment : Fragment(R.layout.fragment_agents) {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private var _binding: FragmentAgentsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAgentsBinding.bind(view)
        binding.tvWall.setOnClickListener { loadWall() }
        loadWall()
    }

    private fun host(): String = ServiceCatalog.LAB_HOST

    private fun loadWall() {
        binding.tvWallStatus.text = "Baixando mural de $host()..."
        val url = "http://${host()}:8288/wall"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val resp = client.newCall(Request.Builder().url(url).build()).execute()
                    if (!resp.isSuccessful) {
                        "HTTP ${resp.code}"
                    } else {
                        resp.body?.string()?.takeIf { it.isNotBlank() } ?: "Mural vazio."
                    }
                } catch (e: Exception) {
                    e.message ?: e.javaClass.simpleName
                }
            }
            binding.tvWall.text = result
            binding.tvWallStatus.text =
                if (result.startsWith("HTTP") || result.startsWith("Connect") ||
                    result.startsWith("Failed") || result.startsWith("cleartext") ||
                    result.startsWith("timeout")
                ) {
                    "Erro ao buscar mural (servidor em :8288)."
                } else {
                    "Mural atualizado."
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}