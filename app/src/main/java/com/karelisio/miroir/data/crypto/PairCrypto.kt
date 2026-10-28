package com.karelisio.miroir.data.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chiffrement de bout en bout du couple. La clé maître (32 octets) n'existe que dans le QR
 * d'appairage et sur les deux téléphones ; on en dérive (HKDF-SHA256) une clé AES-256-GCM et
 * une clé HMAC qui produit des étiquettes opaques de questions (comparables par le serveur,
 * mais impossibles à relier à une question sans la clé).
 */
class PairCrypto(masterKey: ByteArray, private val pairId: String) {

    private val encKey = SecretKeySpec(hkdf(masterKey, "miroir-enc-v1"), "AES")
    private val tagKey = SecretKeySpec(hkdf(masterKey, "miroir-tag-v1"), "HmacSHA256")

    /** [context] est lié au chiffré (AAD) : le serveur ne peut pas permuter deux réponses. */
    fun encrypt(plain: String, context: String): String {
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad(context))
        return b64(iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    fun decrypt(payload: String, context: String): String {
        val bytes = unb64(payload)
        require(bytes.size > IV_LEN) { "chiffré trop court" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encKey, GCMParameterSpec(128, bytes, 0, IV_LEN))
        cipher.updateAAD(aad(context))
        return String(cipher.doFinal(bytes, IV_LEN, bytes.size - IV_LEN), Charsets.UTF_8)
    }

    fun decryptOrNull(payload: String?, context: String): String? =
        payload?.let { runCatching { decrypt(it, context) }.getOrNull() }

    fun tag(questionId: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(tagKey)
        return b64(mac.doFinal("q:$questionId".toByteArray())).take(32)
    }

    private fun aad(context: String) = "miroir:v1:$pairId:$context".toByteArray()

    companion object {
        private const val IV_LEN = 12
        private val random = SecureRandom()

        fun randomBytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }

        fun b64(bytes: ByteArray): String =
            Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        fun unb64(text: String): ByteArray = Base64.decode(text, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        /** HKDF-SHA256 (RFC 5869) sans sel, 32 octets de sortie. */
        fun hkdf(ikm: ByteArray, info: String): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
            val prk = mac.doFinal(ikm)
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(info.toByteArray())
            mac.update(1)
            return mac.doFinal()
        }
    }
}
