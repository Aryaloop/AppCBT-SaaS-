package com.examingrity.cbt

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.examingrity.cbt.network.ApiClient
import com.examingrity.cbt.network.RegisterRequest
import com.examingrity.cbt.utils.RsaHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etNamaLengkap = findViewById<EditText>(R.id.etNamaLengkap)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        btnRegister.setOnClickListener {
            val nama = etNamaLengkap.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirm = etConfirmPassword.text.toString().trim()

            if (nama.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Semua bidang wajib diisi.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ==========================================
            // 🚀 PERBAIKAN: VALIDASI LAPIS PERTAMA (CLIENT-SIDE)
            // ==========================================

            // 1. Validasi Karakter Nama (Hanya Huruf & Spasi/Tanda Baca Wajar)
            val nameRegex = Regex("^[a-zA-Z\\s.,']+$")
            if (!nama.matches(nameRegex)) {
                Toast.makeText(this, "Nama tidak valid. Hindari penggunaan angka atau simbol unik.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // 2. Validasi Format Email
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Format email tidak valid. Pastikan ada simbol @ dan domain.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // ==========================================

            if (password != confirm) {
                Toast.makeText(this, "Kata sandi tidak cocok.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnRegister.isEnabled = false
            btnRegister.text = "Mendaftarkan..."

            lifecycleScope.launch(Dispatchers.IO) {
                try {

                    //  TAMBAHAN: Pancing Token CSRF dari server untuk halaman Registrasi
                    try {
                        ApiClient.instance.getCsrfToken()
                    } catch (e: Exception) {
                        // Abaikan error minor
                    }
                    // 1. Ambil Public Key
                    val pubKeyResponse = ApiClient.instance.getPublicKey()
                    if (!pubKeyResponse.isSuccessful || pubKeyResponse.body() == null) {
                        throw Exception("Gagal mendapatkan kunci keamanan.")
                    }
                    val publicKey = pubKeyResponse.body()!!.publicKey

                    // 2. Enkripsi Kata Sandi
                    val encryptedPassword = RsaHelper.encrypt(password, publicKey)
                        ?: throw Exception("Gagal mengenkripsi kata sandi.")

                    // 3. Kirim Request
                    // Role otomatis "siswa" seperti yang didefinisikan di RegisterRequest
                    val request = RegisterRequest(nama_lengkap = nama, email = email, kata_sandi = encryptedPassword)
                    val response = ApiClient.instance.registerSiswa(request)

                    withContext(Dispatchers.Main) {
                        btnRegister.isEnabled = true
                        btnRegister.text = "Daftar Sekarang"

                        if (response.isSuccessful) {
                            Toast.makeText(this@RegisterActivity, "Registrasi berhasil! Silakan cek email Anda.", Toast.LENGTH_LONG).show()
                            finish() // Tutup halaman registrasi, kembali ke halaman Login
                        } else {
                            Toast.makeText(this@RegisterActivity, "Registrasi gagal. Email mungkin sudah terdaftar.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        btnRegister.isEnabled = true
                        btnRegister.text = "Daftar Sekarang"
                        Toast.makeText(this@RegisterActivity, e.message ?: "Terjadi kesalahan koneksi.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}