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
import org.json.JSONObject // Pastikan import ini ditambahkan

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

            // Cegah klik ganda / spamming dari sisi UI
            btnSendResetLink.isEnabled = false
            btnSendResetLink.text = "Mengirim..."

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // DIBUANG: getCsrfToken() tidak diperlukan karena rute ini public

                    val request = ForgotPasswordRequest(email)
                    val response = ApiClient.instance.forgotPassword(request)

                    withContext(Dispatchers.Main) {
                        btnSendResetLink.isEnabled = true
                        btnSendResetLink.text = "Kirim Tautan Pemulihan"

                        if (response.isSuccessful) {
                            Toast.makeText(this@ForgotPasswordActivity, "Tautan terkirim! Silakan cek email Anda.", Toast.LENGTH_LONG).show()
                            finish() // Kembali ke halaman Login
                        } else {
                            // PERBAIKAN: Tangkap pesan error dari Node.js (misal 404 Email tidak ada, atau 429 Rate Limit)
                            val errorMessage = try {
                                val errorString = response.errorBody()?.string()
                                if (!errorString.isNullOrEmpty()) {
                                    JSONObject(errorString).getString("message")
                                } else {
                                    "Gagal: Periksa kembali email Anda."
                                }
                            } catch (e: Exception) {
                                "Terjadi kesalahan pada server."
                            }

                            Toast.makeText(this@ForgotPasswordActivity, errorMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        btnSendResetLink.isEnabled = true
                        btnSendResetLink.text = "Kirim Tautan Pemulihan"
                        Toast.makeText(this@ForgotPasswordActivity, "Kesalahan jaringan: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}