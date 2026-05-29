package com.examingrity.cbt.utils

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

object RsaHelper {
    /**
     * Fungsi untuk mengenkripsi teks menggunakan Kunci Publik RSA
     * dengan Padding OAEP SHA-256 (Sesuai dengan Backend Node.js)
     * * @param plainText Kata sandi asli (Plain text)
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

            // 4. Inisialisasi Cipher dengan algoritma OAEP
            val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")

            // 5. Konfigurasi Parameter OAEP agar persis sama dengan settingan Node.js (oaepHash: "sha256")
            val oaepParams = OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256, // Node.js secara default menggunakan SHA-256 untuk MGF1 juga
                PSource.PSpecified.DEFAULT
            )

            cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams)

            // 6. Enkripsi dan konversi hasilnya ke Base64 (NO_WRAP agar tidak ada enter ekstra)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}