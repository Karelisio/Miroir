package com.karelisio.miroir

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karelisio.miroir.data.crypto.PairCrypto
import com.karelisio.miroir.update.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PairCryptoTest {
    private val key = PairCrypto.randomBytes(32)
    private val crypto = PairCrypto(key, "pair-1234567890abcdef")

    @Test
    fun `aller-retour AES-GCM avec contexte`() {
        val c1 = crypto.encrypt("Ma réponse secrète 💜", "answer:2026-10-28:A")
        val c2 = crypto.encrypt("Ma réponse secrète 💜", "answer:2026-10-28:A")
        assertNotEquals("IV aléatoire", c1, c2)
        assertEquals("Ma réponse secrète 💜", crypto.decrypt(c1, "answer:2026-10-28:A"))
        assertTrue("base64url sans remplissage", c1.matches(Regex("^[A-Za-z0-9_-]+$")))
    }

    @Test
    fun `un chiffré déplacé vers un autre contexte est rejeté`() {
        val c = crypto.encrypt("A", "answer:2026-10-28:A")
        assertNull(crypto.decryptOrNull(c, "answer:2026-10-28:B"))
        assertNull(PairCrypto(key, "autre-paire-xxxxxxxx").decryptOrNull(c, "answer:2026-10-28:A"))
        assertNull(PairCrypto(PairCrypto.randomBytes(32), "pair-1234567890abcdef").decryptOrNull(c, "answer:2026-10-28:A"))
    }

    @Test
    fun `les étiquettes de question sont stables et opaques`() {
        assertEquals(crypto.tag("fun-001"), PairCrypto(key, "pair-1234567890abcdef").tag("fun-001"))
        assertNotEquals(crypto.tag("fun-001"), crypto.tag("fun-002"))
        assertNotEquals(crypto.tag("fun-001"), PairCrypto(PairCrypto.randomBytes(32), "p").tag("fun-001"))
        assertEquals(32, crypto.tag("fun-001").length)
    }

    @Test
    fun `comparaison des versions`() {
        assertTrue(UpdateManager.compareVersions("1.2.10", "1.2.9") > 0)
        assertTrue(UpdateManager.compareVersions("2.0.0", "1.9.9") > 0)
        assertEquals(0, UpdateManager.compareVersions("1.0", "1.0.0"))
        assertTrue(UpdateManager.compareVersions("1.0.0", "1.0.1-debug") < 0)
    }
}
