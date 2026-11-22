package com.farcast.agent.darkalgorithm.features

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * #p-pq. The website's own panel already labels this a "demo layer" and
 * says real production PQ needs "audited libraries (liboqs, CIRCL...)" —
 * that honesty is worth keeping, not quietly dropping once it's "a real app."
 *
 * What's genuinely real below: AES-256-GCM sealing of case notes — that part
 * needs no PQ anything, it's standard symmetric crypto via the JCE, same
 * strength on Android as anywhere else.
 *
 * What's NOT faked: an actual ML-KEM (Kyber) keypair/encapsulation. Android's
 * platform JCE has no ML-KEM provider as of writing. The real library for
 * this is BouncyCastle, which does ship ML-KEM support — but pinning an exact
 * artifact coordinate I haven't verified against the current BC release just
 * to make this file "complete" would be exactly the kind of unverified-
 * dependency mistake the project's own Section 6 rules warn against. Add
 * BouncyCastle's provider, confirm its current ML-KEM API surface, and wire
 * generateMlKemKeyPair()/encapsulate() below to it — left as a real stub,
 * not a pretend implementation.
 */
object PqCryptoDemo {

    data class SealedNote(val ciphertextB64: String, val ivB64: String)

    fun generateAesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    fun seal(plaintext: String, key: SecretKey): SealedNote {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return SealedNote(Base64.encodeToString(ciphertext, Base64.NO_WRAP), Base64.encodeToString(iv, Base64.NO_WRAP))
    }

    fun unseal(sealed: SealedNote, key: SecretKey): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(sealed.ivB64, Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(sealed.ciphertextB64, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    /** INTEGRATION POINT — see file header. Not implemented with a fabricated
     *  algorithm; wire to BouncyCastle's verified ML-KEM API once confirmed. */
    fun generateMlKemKeyPair(): Nothing =
        throw NotImplementedError("Wire to BouncyCastle's ML-KEM provider — see file header for why this isn't stubbed with fake crypto.")
}
