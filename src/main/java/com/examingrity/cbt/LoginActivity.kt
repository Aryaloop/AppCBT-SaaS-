package com.examingrity.cbt

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.examingrity.cbt.network.ApiClient
import com.examingrity.cbt.network.LoginRequest
import com.examingrity.cbt.utils.RsaHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvRegisterLink = findViewById<TextView>(R.id.tvRegisterLink)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)

        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Email dan Password wajib diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Memanggil API menggunakan Coroutines (Berjalan di Background Thread)
            btnLogin.isEnabled = false
            btnLogin.text = "Memproses..."

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // 1. Ambil Public Key dari Server
                    val pubKeyResponse = ApiClient.instance.getPublicKey()
                    if (!pubKeyResponse.isSuccessful || pubKeyResponse.body() == null) {
                        throw Exception("Gagal mendapatkan kunci keamanan dari server.")
                    }
                    val publicKey = pubKeyResponse.body()!!.publicKey

                    // 2. Enkripsi Kata Sandi
                    val encryptedPassword = RsaHelper.encrypt(password, publicKey)
                        ?: throw Exception("Gagal mengenkripsi kata sandi secara lokal.")

                    // 3. Kirim Request Login dengan kata sandi yang SUDAH TERENKRIPSI
                    val request = LoginRequest(email = email, kata_sandi = encryptedPassword)
                    val response = ApiClient.instance.loginSiswa(request)

                    withContext(Dispatchers.Main) {
                        btnLogin.isEnabled = true
                        btnLogin.text = "Masuk"

                        if (response.isSuccessful) {
                            val user = response.body()?.user
                            if (user?.role == "siswa") {
                                Toast.makeText(this@LoginActivity, "Login Sukses!", Toast.LENGTH_SHORT).show()
                                // Lanjut ke Dashboard...
                            } else {
                                Toast.makeText(this@LoginActivity, "Aplikasi ini khusus Siswa.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(this@LoginActivity, "Email atau kata sandi salah.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        btnLogin.isEnabled = true
                        btnLogin.text = "Masuk"
                        Toast.makeText(this@LoginActivity, e.message ?: "Koneksi Gagal.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Tautan ke Halaman Registrasi
        tvRegisterLink.setOnClickListener {
            // Kita akan buat activity ini setelah ini
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}