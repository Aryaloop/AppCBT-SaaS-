package com.examingrity.cbt.utils

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

object RsaHelper {
    /**
     * Fungsi untuk mengenkripsi teks menggunakan Kunci Publik RSA
     * @param plainText Kata sandi asli (Plain text)
     * @param publicKeyString Kunci publik dari server (Format PEM)
     * @return String terenkripsi dalam format Base64, atau null jika gagal
     */
    fun encrypt(plainText: String, publicKeyString: String): String? {
        return try {
            // 1. Bersihkan String Kunci Publik dari header, footer, dan spasi/enter
            val publicKeyPEM = publicKeyString
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")

            // 2. Decode Base64 menjadi byte array
            val decodedBytes = Base64.decode(publicKeyPEM, Base64.DEFAULT)

            // 3. Bangun kembali objek Kunci Publik Android
            val spec = X509EncodedKeySpec(decodedBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(spec)

            // 4. Inisialisasi Cipher untuk Enkripsi (Gunakan padding yang sama dengan backend)
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)

            // 5. Enkripsi dan konversi hasilnya ke Base64 (NO_WRAP agar tidak ada enter ekstra)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}