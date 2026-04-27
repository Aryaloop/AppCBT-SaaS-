package com.examingrity.cbt

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.examingrity.cbt.network.ApiClient
import com.examingrity.cbt.network.UpdateProfileRequest
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.examingrity.cbt.network.TautkanSekolahRequest
class DashboardSiswaActivity : AppCompatActivity() {

    private lateinit var layoutUjian: View
    private lateinit var layoutRiwayat: View
    private lateinit var layoutProfile: View
    private lateinit var rvRiwayat: RecyclerView

    private lateinit var tvProfilNama: TextView
    private lateinit var tvProfilEmail: TextView
    private lateinit var tvProfilSekolah: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard_siswa)

        // Init Layouts & Views
        layoutUjian = findViewById(R.id.layoutUjian)
        layoutRiwayat = findViewById(R.id.layoutRiwayat)
        layoutProfile = findViewById(R.id.layoutProfile)

        tvProfilNama = findViewById(R.id.tvProfilNama)
        tvProfilEmail = findViewById(R.id.tvProfilEmail)
        tvProfilSekolah = findViewById(R.id.tvProfilSekolah)

        rvRiwayat = findViewById(R.id.rvRiwayat)
        rvRiwayat.layoutManager = LinearLayoutManager(this)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        val btnMulaiUjian = findViewById<Button>(R.id.btnMulaiUjian)
        val etPinUjian = findViewById<EditText>(R.id.etPinUjian)
        val btnGantiPassword = findViewById<Button>(R.id.btnGantiPassword)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        // 1. TAMBAHKAN INIT ID TAUTKAN SEKOLAH DI SINI
        val etTokenSekolah = findViewById<EditText>(R.id.etTokenSekolah)
        val btnTautkanSekolah = findViewById<Button>(R.id.btnTautkanSekolah)
        // Tarik data profil saat pertama kali buka
        fetchProfileSiswa()

        // 🚀 LOGIKA TAB NAVIGATION (Sekarang memanggil riwayat saat diklik)
        bottomNav.setOnItemSelectedListener { item ->
            layoutUjian.visibility = View.GONE
            layoutRiwayat.visibility = View.GONE
            layoutProfile.visibility = View.GONE

            when (item.itemId) {
                R.id.nav_home -> {
                    layoutUjian.visibility = View.VISIBLE
                    true
                }
                R.id.nav_history -> {
                    layoutRiwayat.visibility = View.VISIBLE
                    fetchRiwayatUjian() // Panggil API Riwayat di sini!
                    true
                }
                R.id.nav_profile -> {
                    layoutProfile.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }

        btnMulaiUjian.setOnClickListener {
            val pin = etPinUjian.text.toString().trim()
            if (pin.isNotEmpty()) {
                cekKesiapanUjian(pin)
            } else {
                Toast.makeText(this, "Masukkan PIN Ujian!", Toast.LENGTH_SHORT).show()
            }
        }

        // 🚀 LOGIKA POP-UP GANTI PASSWORD KEMBALI
        btnGantiPassword.setOnClickListener {
            tampilkanDialogGantiPassword()
        }

        btnLogout.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
        // 2. TAMBAHKAN LISTENER KLIKNYA DI SINI
        btnTautkanSekolah.setOnClickListener {
            val token = etTokenSekolah.text.toString().trim()
            if (token.isNotEmpty()) {
                prosesTautkanSekolah(token)
            } else {
                Toast.makeText(this, "Masukkan Token Sekolah!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cekKesiapanUjian(token: String) {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            this.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = level * 100 / scale.toFloat()

        val alertMessage = StringBuilder()
        if (batteryPct < 20) alertMessage.append("🔴 PERINGATAN BATERAI: Sisa ${batteryPct.toInt()}%. Harap cas HP Anda!\n")
        else alertMessage.append("🟢 Status Baterai Aman (${batteryPct.toInt()}%).\n")

        MaterialAlertDialogBuilder(this)
            .setTitle("Konfirmasi Persiapan Ujian")
            .setMessage(alertMessage.toString())
            .setCancelable(false)
            .setNegativeButton("Batal") { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("SAYA SIAP") { _, _ ->
                val intent = Intent(this, ExamActivity::class.java)
                intent.putExtra("TOKEN_UJIAN", token)
                startActivity(intent)
            }
            .show()
    }

    // ==========================================
    // FUNGSI KEMBALI: TARIK RIWAYAT UJIAN
    // ==========================================
    private fun fetchRiwayatUjian() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.instance.getRiwayatUjian()
                if (response.isSuccessful && response.body() != null) {
                    // Buka komentar (uncomment) baris di bawah ini
                    val riwayatList = response.body()!!.data
                    withContext(Dispatchers.Main) {
                        rvRiwayat.adapter = RiwayatAdapter(riwayatList)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DashboardSiswaActivity, "Gagal memuat riwayat", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    // ==========================================
    // FUNGSI KEMBALI: POP-UP GANTI PASSWORD
    // ==========================================
    private fun tampilkanDialogGantiPassword() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 10)
        }

        val etOldPass = EditText(this).apply {
            hint = "Kata Sandi Lama"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(0, 20, 0, 40)
        }
        val etNewPass = EditText(this).apply {
            hint = "Kata Sandi Baru"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(0, 20, 0, 20)
        }

        layout.addView(etOldPass)
        layout.addView(etNewPass)

        MaterialAlertDialogBuilder(this)
            .setTitle("Ganti Kata Sandi")
            .setView(layout)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan Perubahan") { _, _ ->
                val oldPass = etOldPass.text.toString()
                val newPass = etNewPass.text.toString()

                if (oldPass.isEmpty() || newPass.isEmpty()) {
                    Toast.makeText(this, "Semua kolom harus diisi!", Toast.LENGTH_SHORT).show()
                } else {
                    prosesGantiPassword(oldPass, newPass)
                }
            }
            .show()
    }

    private fun prosesGantiPassword(old: String, new: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = UpdateProfileRequest(
                    password_lama = old,
                    password_baru = new
                )
                val response = ApiClient.instance.updatePassword(request)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@DashboardSiswaActivity, "Sandi berhasil diubah!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@DashboardSiswaActivity, "Sandi lama salah atau sesi habis.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DashboardSiswaActivity, "Gagal terhubung ke server.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchProfileSiswa() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.instance.getProfileSiswa()
                if (response.isSuccessful && response.body() != null) {
                    val profil = response.body()!!
                    withContext(Dispatchers.Main) {
                        tvProfilNama.text = profil.nama_lengkap
                        tvProfilEmail.text = profil.email
                        tvProfilSekolah.text = profil.sekolah

                        if (profil.sekolah.contains("Independen", ignoreCase = true)) {
                            tvProfilSekolah.setTextColor(android.graphics.Color.parseColor("#F59E0B"))
                        } else {
                            tvProfilSekolah.setTextColor(android.graphics.Color.parseColor("#0052FF"))
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DashboardSiswaActivity, "Gagal memuat profil", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun prosesTautkanSekolah(token: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = TautkanSekolahRequest(token)
                val response = ApiClient.instance.tautkanSekolah(request)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@DashboardSiswaActivity, "Sekolah berhasil ditautkan!", Toast.LENGTH_LONG).show()
                        // Refresh ulang data profil di layar
                        fetchProfileSiswa()
                        findViewById<EditText>(R.id.etTokenSekolah).text.clear()
                    } else {
                        Toast.makeText(this@DashboardSiswaActivity, "Token tidak valid / tidak ditemukan.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DashboardSiswaActivity, "Gagal terhubung ke server.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        outState.putInt("ACTIVE_TAB", bottomNav.selectedItemId)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val activeTabId = savedInstanceState.getInt("ACTIVE_TAB", R.id.nav_home)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = activeTabId
    }
}