package com.idt.widget.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private val checker = UpdateChecker()

    @Test
    fun `parse extrai campos do manifest`() {
        val body = """{"versionName":"0.0.6","versionCode":6,"apkUrl":"http://x/a.apk","changelog":"nova","minVersionCode":1}"""
        val info = checker.parse(body)
        assertEquals("0.0.6", info!!.versionName)
        assertEquals(6, info.versionCode)
        assertEquals("http://x/a.apk", info.apkUrl)
        assertEquals("nova", info.changelog)
    }

    @Test
    fun `parse tolera campos ausentes`() {
        val info = checker.parse("""{"versionCode":7}""")
        assertEquals(7, info!!.versionCode)
        assertEquals("", info.versionName)
    }

    @Test
    fun `parse retorna null para json invalido`() {
        assertNull(checker.parse("isso nao é json"))
    }

    @Test
    fun `isNewerThan verdadeiro quando versionCode maior e nome diferente`() {
        val info = checker.parse("""{"versionName":"0.0.6","versionCode":6}""")!!
        assertTrue(info.isNewerThan("0.0.5", 5))
    }

    @Test
    fun `isNewerThan falso quando nome igual`() {
        val info = checker.parse("""{"versionName":"0.0.6","versionCode":6}""")!!
        assertFalse(info.isNewerThan("0.0.6", 6))
    }

    @Test
    fun `isNewerThan falso quando versionCode menor`() {
        val info = checker.parse("""{"versionName":"0.0.4","versionCode":4}""")!!
        assertFalse(info.isNewerThan("0.0.6", 6))
    }
}
