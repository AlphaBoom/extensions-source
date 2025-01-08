package eu.kanade.tachiyomi.extension.zh.bilibilimanga

import android.util.Base64
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private fun InputStream.decodeForRawData(): ByteArray {
    return readBytes()
}

private fun InputStream.decodeForAES(size: Int, iv: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val data = ByteArray(size).apply { read(this) }
    val key = readBytes()
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    val encryptedSize = 20 * 1024 + 16
    val decryptedSegment =
        cipher.doFinal(data.copyOfRange(0, encryptedSize.coerceAtMost(data.size)))
    return if (encryptedSize < data.size) {
        // append remaining data
        decryptedSegment + data.copyOfRange(encryptedSize, data.size)
    } else {
        decryptedSegment
    }
}

/**
 * Guess
 */
private fun InputStream.decodeForECDH(size: Int, privateKeyBase64: String, iv: ByteArray): ByteArray {
    val data = ByteArray(size).apply { read(this) }
    val otherKey = readBytes()
    val sharedKey = KeyAgreement.getInstance("ECDH")
        .apply {
            val keyFactory = KeyFactory.getInstance("EC")
            val privateKey = Base64.decode(privateKeyBase64, Base64.DEFAULT)
            init(keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKey)))
            doPhase(keyFactory.generatePublic(X509EncodedKeySpec(otherKey)), true)
        }.generateSecret()
    val key = sharedKey.copyOfRange(0, 16)
    // just same as AES
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    val encryptedSize = 20 * 1024 + 16
    val decryptedSegment =
        cipher.doFinal(data.copyOfRange(0, encryptedSize.coerceAtMost(data.size)))
    return if (encryptedSize < data.size) {
        // append remaining data
        decryptedSegment + data.copyOfRange(encryptedSize, data.size)
    } else {
        decryptedSegment
    }
}

internal fun InputStream.decodeImage(iv: ByteArray? = null, privateKey: String? = null): ByteArray =
    use {
        val version = it.read()
        val size =
            ByteBuffer.wrap(ByteArray(4).apply { it.read(this) }).order(ByteOrder.BIG_ENDIAN)
                .getInt()
        when (version) {
            0 -> it.decodeForRawData()
            1, 2 -> it.decodeForAES(size, iv!!)
            else -> it.decodeForECDH(size, privateKey!!, iv!!)
        }
    }
