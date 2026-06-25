package com.examingrity.cbt

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.examingrity.cbt.network.ApiClient
import com.examingrity.cbt.network.ForgotPasswordRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ForgotPasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        val etForgotEmail = findViewById<EditText>(R.id.etForgotEmail)
        val btnSendResetLink = findViewById<Button>(R.id.btnSendResetLink)

        btnSendResetLink.setOnClickListener {
            val email = etForgotEmail.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(this, "Email tidak boleh kosong.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSendResetLink.isEnabled = false
            btnSendResetLink.text = "Mengirim..."

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // TAMBAHAN: Pancing Token CSRF dari server untuk halaman Registrasi
                    try {
                        ApiClient.instance.getCsrfToken()
                    } catch (e: Exception) {
                        // Abaikan error minor
                    }

                    val request = ForgotPasswordRequest(email)
                    val response = ApiClient.instance.forgotPassword(request)

                    withContext(Dispatchers.Main) {
                        btnSendResetLink.isEnabled = true
                        btnSendResetLink.text = "Kirim Tautan Pemulihan"

                        if (response.isSuccessful) {
                            Toast.makeText(this@ForgotPasswordActivity, "Tautan terkirim! Silakan cek email Anda.", Toast.LENGTH_LONG).show()
                            finish() // Kembali ke halaman Login
                        } else {
                            // Menangani error dari backend (misal: spam 3 menit atau email tidak ada)
                            Toast.makeText(this@ForgotPasswordActivity, "Gagal: Periksa kembali email Anda.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        btnSendResetLink.isEnabled = true
                        btnSendResetLink.text = "Kirim Tautan Pemulihan"
                        Toast.makeText(this@ForgotPasswordActivity, "Kesalahan jaringan.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}