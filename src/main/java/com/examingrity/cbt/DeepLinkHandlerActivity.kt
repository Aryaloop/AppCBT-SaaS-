package com.examingrity.cbt

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.examingrity.cbt.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeepLinkHandlerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kita tidak memakai setContentView karena halaman ini hanya logika transisi (Blank Screen sejenak)

        val data: Uri? = intent?.data
        if (data != null && data.scheme == "examingrity") {
            val host = data.host // Akan berisi "verify" atau "reset-password"
            val token = data.lastPathSegment // Mengambil bagian paling ujung dari URL

            if (token.isNullOrEmpty()) {
                finishWithError("Tautan rusak atau tidak valid.")
                return
            }

            when (host) {
                "verify" -> handleEmailVerification(token)
                "reset-password" -> openResetPasswordScreen(token)
                else -> finishWithError("Perintah tidak dikenali.")
            }
        } else {
            finishWithError("Link tidak valid.")
        }
    }

    private fun handleEmailVerification(token: String) {
        Toast.makeText(this, "Memverifikasi akun...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Panggil API GET /verify-email/:token
                val response = ApiClient.instance.verifyEmailToken(token)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@DeepLinkHandlerActivity, "Verifikasi sukses! Silakan login.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@DeepLinkHandlerActivity, "Token tidak valid atau kadaluwarsa.", Toast.LENGTH_LONG).show()
                    }
                    // Lempar kembali ke halaman Login
                    startActivity(Intent(this@DeepLinkHandlerActivity, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    finishWithError("Gagal terhubung ke server.")
                }
            }
        }
    }

    private fun openResetPasswordScreen(token: String) {
        // Arahkan siswa ke layar UI Reset Password (Halaman baru yang harus dibuat)
        // Kirimkan token ini ke activity tersebut
        val intent = Intent(this, ResetPasswordActivity::class.java)
        intent.putExtra("RESET_TOKEN", token)
        startActivity(intent)
        finish()
    }

    private fun finishWithError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}