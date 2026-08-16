package com.idt.widget.ui.dashboard

import com.idt.widget.data.model.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Agenda verificações periódicas enquanto a configuração mantiver
 * [AppConfig.autoRefresh] ativo. A cada mudança de config reinicia o
 * ciclo (respeitando o intervalo em segundos). O loop roda indefinidamente,
 * corrigindo o comportamento de "atualiza uma vez e para".
 */
class RefreshScheduler(
    private val configFlow: Flow<AppConfig>,
    private val scope: CoroutineScope,
    private val minIntervalSeconds: Long = 10L,
    private val onRefresh: suspend () -> Unit,
) {
    fun start() {
        scope.launch {
            configFlow.collectLatest { cfg ->
                while (cfg.autoRefresh && isActive) {
                    delay(cfg.refreshIntervalSeconds.coerceAtLeast(minIntervalSeconds) * 1000L)
                    if (cfg.autoRefresh && isActive) onRefresh()
                }
            }
        }
    }
}
