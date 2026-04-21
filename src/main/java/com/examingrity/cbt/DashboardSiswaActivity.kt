package com.examingrity.cbt

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.examingrity.cbt.network.ApiClient
import com.examingrity.cbt.network.TautkanSekolahRequest
import com.examingrity.cbt.network.UpdateProfileRequest
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardSiswaActivity : AppCompatActivity() {

    private lateinit var layoutUjian: View
    private lateinit var layoutRiwayat: View
    private lateinit var layoutProfile: View
    private lateinit var rvRiwayat: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard_siswa)

        // Init Layouts
        layoutUjian = findViewById(R.id.layoutUjian)
        layoutRiwayat = findViewById(R.id.layoutRiwayat)
        layoutProfile = findViewById(R.id.layoutProfile)
        rvRiwayat = findViewById(R.id.rvRiwayat)
        rvRiwayat.layoutManager = LinearLayoutManager(this)

        // Init Tab Ujian
        val btnMulaiUjian = findViewById<Button>(R.id.btnMulaiUjian)
        val etTokenUjian = findViewById<EditText>(R.id.etTokenUjian)

        // Init Tab Profil
        val etTokenSekolah = findViewById<EditText>(R.id.etTokenSekolah)
        val btnTautkanSekolah = findViewById<Button>(R.id.btnTautkanSekolah)
        val etPassLama = findViewById<EditText>(R.id.etPassLama)
        val etPassBaru = findViewById<EditText>(R.id.etPassBaru)
        val btnGantiPassword = findViewById<Button>(R.id.btnGantiPassword)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        // Navigasi Bawah
        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { showTab(layoutUjian); true }
                R.id.nav_history -> { showTab(layoutRiwayat); fetchRiwayat(); true }
                R.id.nav_profile -> { showTab(layoutProfile); true }
                else -> false
            }
        }

        // =====================================
        // LOGIKA TAB UJIAN
        // =====================================
        btnMulaiUjian.setOnClickListener {
            val token = etTokenUjian.text.toString().trim()
            if (token.isEmpty()) {
                Toast.makeText(this, "Masukkan token ujian!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showPreExamSecurityAlert(token)
        }

        // =====================================
        // LOGIKA TAB PROFIL
        // =====================================
        btnTautkanSekolah.setOnClickListener {
            val tokenSekolah = etTokenSekolah.text.toString().trim()
            if(tokenSekolah.isEmpty()) return@setOnClickListener

            btnTautkanSekolah.text = "Loading..."
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val res = ApiClient.instance.tautkanSekolah(TautkanSekolahRequest(tokenSekolah))
                    withContext(Dispatchers.Main) {
                        btnTautkanSekolah.text = "Tautkan Akun"
                        if(res.isSuccessful) Toast.makeText(this@DashboardSiswaActivity, res.body()?.message, Toast.LENGTH_LONG).show()
                        else Toast.makeText(this@DashboardSiswaActivity, "Gagal menautkan token.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e:Exception) {
                    withContext(Dispatchers.Main) { btnTautkanSekolah.text = "Tautkan Akun" }
                }
            }
        }

        btnGantiPassword.setOnClickListener {
            val lama = etPassLama.text.toString()
            val baru = etPassBaru.text.toString()
            if(lama.isEmpty() || baru.isEmpty()) return@setOnClickListener

            btnGantiPassword.text = "Menyimpan..."
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val res = ApiClient.instance.updatePassword(UpdateProfileRequest(lama, baru))
                    withContext(Dispatchers.Main) {
                        btnGantiPassword.text = "Simpan Sandi Baru"
                        if(res.isSuccessful) {
                            Toast.makeText(this@DashboardSiswaActivity, "Password berhasil diubah", Toast.LENGTH_LONG).show()
                            etPassLama.text.clear()
                            etPassBaru.text.clear()
                        } else Toast.makeText(this@DashboardSiswaActivity, "Password lama salah.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e:Exception) {
                    withContext(Dispatchers.Main) { btnGantiPassword.text = "Simpan Sandi Baru" }
                }
            }
        }

        btnLogout.setOnClickListener {
            // Arahkan kembali ke Login
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun showTab(activeLayout: View) {
        layoutUjian.visibility = View.GONE
        layoutRiwayat.visibility = View.GONE
        layoutProfile.visibility = View.GONE
        activeLayout.visibility = View.VISIBLE
    }

    private fun fetchRiwayat() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val res = ApiClient.instance.getRiwayatUjian()
                withContext(Dispatchers.Main) {
                    if(res.isSuccessful) {
                        val data = res.body()?.data ?: emptyList()
                        rvRiwayat.adapter = RiwayatAdapter(data)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DashboardSiswaActivity, "Gagal memuat riwayat", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showPreExamSecurityAlert(token: String) {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            this.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = level * 100 / scale.toFloat()

        val alertMessage = StringBuilder()
        alertMessage.append("Anda akan memasuki mode ujian aman (Screen Pinning) untuk Token: $token\n\n")
        alertMessage.append("⚠️ ATURAN KEAMANAN:\n")
        alertMessage.append("1. Anda TIDAK BISA keluar dari aplikasi ini selama ujian berlangsung.\n")
        alertMessage.append("2. Notifikasi akan diblokir.\n")
        alertMessage.append("3. Keluar layar akan otomatis merekam pelanggaran.\n\n")

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
    // MENYIMPAN POSISI TAB TERAKHIR
    // ==========================================
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Simpan ID tab yang sedang aktif
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        outState.putInt("ACTIVE_TAB", bottomNav.selectedItemId)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Kembalikan ke tab terakhir saat activity dirender ulang
        val activeTabId = savedInstanceState.getInt("ACTIVE_TAB", R.id.nav_home)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = activeTabId
    }
}