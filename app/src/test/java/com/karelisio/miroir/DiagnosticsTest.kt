package com.karelisio.miroir

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karelisio.miroir.diagnostics.Diagnostics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DiagnosticsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun reset() = Diagnostics.clear()

    @Test
    fun `les erreurs sont horodatées et comptées`() {
        Diagnostics.record("synchro", "réseau indisponible")
        Diagnostics.record("maj", "vérification impossible", IOException("timeout"))
        assertEquals(2, Diagnostics.entryCount())
        val journal = Diagnostics.journal()
        assertTrue(journal.contains("[maj] vérification impossible : IOException – timeout"))
        assertTrue(journal.contains("\tat ")) // début de la pile
    }

    @Test
    fun `le journal reste borné`() {
        repeat(3_000) { Diagnostics.record("synchro", "ligne $it " + "x".repeat(80)) }
        assertTrue(Diagnostics.journal().length <= 192 * 1024)
        assertTrue(Diagnostics.journal().contains("ligne 2999"))
        assertFalse(Diagnostics.journal().contains("ligne 0 "))
    }

    @Test
    fun `rapport sans donnees personnelles`() = runBlocking {
        context.container.prefs.update { myProfile("Léa", "1996-03-14"); partnerProfile("Sam", null) }
        Diagnostics.record("synchro", "erreur serveur 500")
        val report = Diagnostics.report(context)
        assertTrue(report.contains("Version : "))
        assertTrue(report.contains("[synchro] erreur serveur 500"))
        assertFalse(report.contains("Léa"))
        assertFalse(report.contains("Sam"))
        assertFalse(report.contains("1996"))
    }

    @Test
    fun `un plantage n'est signalé qu'une fois`() {
        assertFalse(Diagnostics.consumeCrash())
        java.io.File(context.filesDir, "diagnostics/crash.flag").writeText("1")
        assertTrue(Diagnostics.consumeCrash())
        assertFalse(Diagnostics.consumeCrash())
    }
}
