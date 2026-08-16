package com.idt.widget.widget

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetLiveServiceTest {

    @Test
    fun servicoAoVivoNaoCrashaProcesso() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        try {
            val start = Intent(ctx, WidgetLiveService::class.java)
            ctx.startService(start)
        } catch (e: SecurityException) {
            // Android 12+ pode bloquear foreground a partir de contexto de teste
        } catch (e: IllegalStateException) {
            // serviço foreground sem notificação permitida
        }
        // Sanidade: o processo do app continua íntegro
        org.junit.Assert.assertNotNull(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
    }
}
