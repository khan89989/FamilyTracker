package com.family.tracker.utils

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"

    // Generates a 256-bit key from the password
    private fun getKey(password: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(bytes, "AES")
    }

    // Encrypts the JSON string. Returns Base64(IV + Ciphertext)
    fun encrypt(plainText: String, password: String): String {
        val key = getKey(password)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    // Decrypts the Base64 string back to JSON
    fun decrypt(cipherTextBase64: String, password: String): String? {
        return try {
            val combined = Base64.decode(cipherTextBase64, Base64.DEFAULT)
            val iv = combined.copyOfRange(0, 16)
            val cipherText = combined.copyOfRange(16, combined.size)

            val key = getKey(password)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))

            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            null // Return null if decryption fails (wrong password)
        }
    }
}