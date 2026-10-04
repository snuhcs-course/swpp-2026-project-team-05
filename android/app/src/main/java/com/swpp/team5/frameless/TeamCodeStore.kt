package com.swpp.team5.frameless

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class TeamCodeStore(context: Context) {
    private val preferences = context.getSharedPreferences("team_code", Context.MODE_PRIVATE)

    fun load(): String {
        val encoded = preferences.getString("code", null) ?: return ""
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > IV_LENGTH)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, bytes.copyOfRange(0, IV_LENGTH))
            )
            String(cipher.doFinal(bytes.copyOfRange(IV_LENGTH, bytes.size)), Charsets.UTF_8)
        }.getOrElse {
            clear()
            ""
        }
    }

    fun save(code: String) {
        if (code.isBlank()) {
            clear()
            return
        }
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val bytes = cipher.iv + cipher.doFinal(code.trim().toByteArray(Charsets.UTF_8))
            preferences.edit().putString("code", Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
        }.onFailure { clear() }
    }

    fun clear() {
        preferences.edit().remove("code").apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "frameless_team_code"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
