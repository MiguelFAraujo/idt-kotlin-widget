package com.idt.widget.data.remote

import com.idt.widget.data.model.ServiceEndpoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceCatalogTest {

    @Test
    fun `default catalog has core lab services`() {
        val catalog = ServiceCatalog.defaultServices
        assertTrue(catalog.isNotEmpty())
        val names = catalog.map { it.name }
        assertTrue(names.contains("OmniRoute"))
        assertTrue(names.contains("Ollama"))
        assertTrue(names.contains("Nextcloud"))
        assertTrue(names.contains("Grafana"))
    }

    @Test
    fun `withHost overrides host for all`() {
        val mapped = ServiceCatalog.withHost("192.168.1.9")
        assertEquals("192.168.1.9", mapped.first().host)
        assertTrue(mapped.all { it.host == "192.168.1.9" })
    }
}
