package com.examingrity.cbt

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.examingrity.cbt.network.ApiClient
import com.examingrity.cbt.network.ResetPasswordRequest
import com.examingrity.cbt.utils.RsaHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResetPasswordActivity : AppCompatActivity() {

    private var resetToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        // 1. Tangkap Token dari DeepLinkHandlerActivity
        resetToken = intent.getStringExtra("RESET_TOKEN")

        if (resetToken.isNullOrEmpty()) {
            Toast.makeText(this, "Token tidak valid atau hilang.", Toast.LENGTH_LONG).show()
            finishAndGoToLogin()
            return
        }

        val etNewPassword = findViewById<EditText>(R.id.etNewPassword)
        val etConfirmNewPassword = findViewById<EditText>(R.id.etConfirmNewPassword)
        val btnSavePassword = findViewById<Button>(R.id.btnSavePassword)

        btnSavePassword.setOnClickListener {
            val newPass = etNewPassword.text.toString().trim()
            val confirmPass = etConfirmNewPassword.text.toString().trim()

            // Validasi Dasar
            if (newPass.length < 6) {
                Toast.makeText(this, "Kata sandi minimal 6 karakter.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPass != confirmPass) {
                Toast.makeText(this, "Kata sandi tidak cocok.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSavePassword.isEnabled = false
            btnSavePassword.text = "Menyimpan..."

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    //  TAMBAHAN: Pancing Token CSRF dari server untuk halaman Registrasi
                    try {
                        ApiClient.instance.getCsrfToken()
                    } catch (e: Exception) {
                        // Abaikan error minor
                    }

                    // 2. Ambil Public Key dari Server
                    val pubKeyResponse = ApiClient.instance.getPublicKey()
                    if (!pubKeyResponse.isSuccessful || pubKeyResponse.body() == null) {
                        throw Exception("Gagal mendapatkan kunci keamanan.")
                    }
                    val publicKey = pubKeyResponse.body()!!.publicKey

                    // 3. Enkripsi Password Baru
                    val encryptedPassword = RsaHelper.encrypt(newPass, publicKey)
                        ?: throw Exception("Gagal mengenkripsi kata sandi.")

                    // 4. Kirim Request Reset
                    val request = ResetPasswordRequest(password_baru = encryptedPassword)
                    val response = ApiClient.instance.resetPassword(resetToken!!, request)

                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Toast.makeText(this@ResetPasswordActivity, "Sandi berhasil diubah! Silakan login.", Toast.LENGTH_LONG).show()
                            finishAndGoToLogin()
                        } else {
                            btnSavePassword.isEnabled = true
                            btnSavePassword.text = "Simpan Kata Sandi"
                            Toast.makeText(this@ResetPasswordActivity, "Gagal mereset. Token mungkin kadaluwarsa.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        btnSavePassword.isEnabled = true
                        btnSavePassword.text = "Simpan Kata Sandi"
                        Toast.makeText(this@ResetPasswordActivity, "Kesalahan Jaringan: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun finishAndGoToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}