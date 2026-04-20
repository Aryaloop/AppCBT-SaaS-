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
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        val tvRegisterLink = findViewById<TextView>(R.id.tvRegisterLink) // Tambahkan ini
        // Tautan ke Lupa Sandi
        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        // Tombol Login
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Email dan Password wajib diisi.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "Memverifikasi..."

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // 1. Ambil Public Key
                    val pkResponse = ApiClient.instance.getPublicKey()
                    if (!pkResponse.isSuccessful) throw Exception("Gagal mendapatkan kunci keamanan.")
                    val publicKeyStr = pkResponse.body()?.publicKey ?: throw Exception("Public key kosong.")

                    // 2. Enkripsi Password
                    val encryptedPassword = RsaHelper.encrypt(password, publicKeyStr)
                        ?: throw Exception("Gagal mengenkripsi kata sandi.")

                    // 3. Eksekusi Login
                    val request = LoginRequest(email, encryptedPassword)
                    val response = ApiClient.instance.loginSiswa(request)

                    withContext(Dispatchers.Main) {
                        btnLogin.isEnabled = true
                        btnLogin.text = "Masuk Ke Sistem"

                        // Cari bagian ini di dalam lifecycleScope.launch:
                        if (response.isSuccessful) {
                            val user = response.body()?.user
                            if (response.isSuccessful) {
                                val user = response.body()?.user
                                if (user?.role == "siswa") {
                                    Toast.makeText(this@LoginActivity, "Login Sukses!", Toast.LENGTH_SHORT).show()

                                    // 1. Buat tujuan intent
                                    val intent = Intent(this@LoginActivity, DashboardSiswaActivity::class.java)
                                    // 2. Bersihkan riwayat halaman
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    // 3. EKSEKUSI PINDAH HALAMAN (Pastikan baris ini ada!)
                                    startActivity(intent)
                                    finish()

                                } else {
                                    Toast.makeText(this@LoginActivity, "Aplikasi ini khusus Siswa.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        btnLogin.isEnabled = true
                        btnLogin.text = "Masuk Ke Sistem"
                        Toast.makeText(this@LoginActivity, e.message ?: "Koneksi Gagal.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        tvRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}