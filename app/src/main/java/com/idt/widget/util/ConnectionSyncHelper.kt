package com.idt.widget.util

import android.content.Context
import com.idt.widget.IDTApplication
import com.idt.widget.widget.StatusData
import com.idt.widget.widget.WidgetScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-sincroniza os endpoints com o host configurado pelo usuário.
 *
 * Sequência: scan REAL de rede no host atual -> persiste só o que respondeu ->
 * limpa o cache de status antigo (nunca mostrar resultado de outro host) ->
 * dispara um check real imediato do widget.
 */
object ConnectionSyncHelper {

    fun resyncAndRefresh(context: Context) {
        val app = context.applicationContext as? IDTApplication ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.container.serviceRepository.resyncWithConfiguredHost()
            } catch (_: Exception) {
                // Scan falhou (host inacessível): segue para cache limpo + refresh honesto
            } finally {
                StatusData.clear(context)
                WidgetScheduler.refreshNow(context)
            }
        }
    }
}
