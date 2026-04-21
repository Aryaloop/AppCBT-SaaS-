package com.examingrity.cbt

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.examingrity.cbt.network.ApiClient
import com.examingrity.cbt.network.LoginRequest
import com.examingrity.cbt.utils.RsaHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        val tvRegisterLink = findViewById<TextView>(R.id.tvRegisterLink)

        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        tvRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Email dan kata sandi wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "Memverifikasi..."

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // 1. Ambil Public Key dari Server (Express.js)
                    val pkResponse = ApiClient.instance.getPublicKey()
                    if (!pkResponse.isSuccessful) throw Exception("Gagal mendapatkan kunci keamanan.")
                    val publicKeyStr = pkResponse.body()?.publicKey ?: throw Exception("Public key kosong dari server.")

                    // 2. Enkripsi Password menggunakan public key dari server
                    // Ini akan memperbaiki error "No value passed..." dan memastikan tidak null
                    val encryptedPassword = RsaHelper.encrypt(password, publicKeyStr)
                        ?: throw Exception("Gagal mengenkripsi kata sandi.")

                    // 3. Eksekusi Login
                    val request = LoginRequest(email, encryptedPassword)
                    val response = ApiClient.instance.loginSiswa(request)

                    withContext(Dispatchers.Main) {
                        btnLogin.isEnabled = true
                        btnLogin.text = "Masuk Ke Sistem"

                        if (response.isSuccessful) {
                            val user = response.body()?.user
                            if (user?.role == "siswa") {
                                Toast.makeText(this@LoginActivity, "Login Sukses!", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this@LoginActivity, DashboardSiswaActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this@LoginActivity, "Aplikasi ini khusus Siswa.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            // TANGKAP ERROR DARI BACKEND
                            val errorString = response.errorBody()?.string()
                            var errorMessage = "Email atau Kata Sandi salah."

                            if (errorString != null && errorString.isNotEmpty()) {
                                try {
                                    val jsonObject = JSONObject(errorString)
                                    errorMessage = jsonObject.getString("message")
                                } catch (e: Exception) {
                                    errorMessage = "Terjadi kesalahan (Code: ${response.code()})"
                                }
                            }

                            // TAMPILKAN UI DIALOG ERROR
                            MaterialAlertDialogBuilder(this@LoginActivity)
                                .setTitle("⚠️ Gagal Masuk")
                                .setMessage(errorMessage)
                                .setCancelable(false)
                                .setPositiveButton("Coba Lagi") { dialog, _ ->
                                    dialog.dismiss()
                                    etPassword.text.clear()
                                    etPassword.requestFocus()
                                }
                                .show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        btnLogin.isEnabled = true
                        btnLogin.text = "Masuk Ke Sistem"
                        Toast.makeText(this@LoginActivity, "Koneksi Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}