package com.example.androidapp.security.voice

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.text.Normalizer
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class VoicePhraseKind(val label: String) {
    ACCESS("Access"), DURESS("Duress"), CANCEL("Cancel")
}

data class VoicePhrases(
    val access: String = "",
    val duress: String = "",
    val cancel: String = ""
) {
    fun phrase(kind: VoicePhraseKind): String = when (kind) {
        VoicePhraseKind.ACCESS -> access
        VoicePhraseKind.DURESS -> duress
        VoicePhraseKind.CANCEL -> cancel
    }

    fun validationError(): String? {
        val values = VoicePhraseKind.values().map { normalizeVoicePhrase(phrase(it)) }
        if (values.any { it.split(' ').size < 3 || it.length > 120 }) {
            return "Use 3 or more words per phrase, up to 120 characters."
        }
        if (values.distinct().size != values.size) return "Choose three different phrases."
        return null
    }
}

fun normalizeVoicePhrase(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

/** Local encrypted storage, excluded from Android backup. Never logs phrases. */
class VoicePhraseStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "voice_phrases.enc"))
    private val alias = "deals_voice_phrases_v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    fun load(): VoicePhrases {
        val bytes = try { file.readFully() } catch (_: java.io.FileNotFoundException) {
            return VoicePhrases()
        }
        val envelope = JSONObject(bytes.toString(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128,
            Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)))
        val plain = cipher.doFinal(Base64.decode(envelope.getString("data"), Base64.NO_WRAP))
        val json = JSONObject(plain.toString(Charsets.UTF_8))
        return VoicePhrases(json.getString("access"), json.getString("duress"), json.getString("cancel"))
    }

    fun save(phrases: VoicePhrases) {
        require(phrases.validationError() == null)
        val plain = JSONObject().put("access", phrases.access.trim())
            .put("duress", phrases.duress.trim()).put("cancel", phrases.cancel.trim())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.doFinal(plain.toString().toByteArray(Charsets.UTF_8))
        val envelope = JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(data, Base64.NO_WRAP))
        val stream = file.startWrite()
        try {
            stream.write(envelope.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    fun clear() { file.delete() }
}
