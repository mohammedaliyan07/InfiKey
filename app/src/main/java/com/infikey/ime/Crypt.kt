package com.infikey.ime

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts cloud API keys at rest with a hardware-backed Android Keystore
 * AES-GCM key. Stored form: "enc:<iv_b64>:<data_b64>". Anything else passes
 * through unchanged, so old plain values keep working until re-saved.
 */
object Crypt {

    private const val ALIAS = "infikey_keys"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String = runCatching {
        if (plain.startsWith("enc:")) return plain
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val data = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(c.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(data, Base64.NO_WRAP)
        "enc:$iv:$body"
    }.getOrDefault(plain)

    fun decrypt(stored: String): String = runCatching {
        if (!stored.startsWith("enc:")) return stored
        val parts = stored.split(":", limit = 3)
        if (parts.size != 3) return stored
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(
            Cipher.DECRYPT_MODE, key(),
            GCMParameterSpec(128, Base64.decode(parts[1], Base64.NO_WRAP))
        )
        String(c.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)))
    }.getOrDefault(stored)
}
