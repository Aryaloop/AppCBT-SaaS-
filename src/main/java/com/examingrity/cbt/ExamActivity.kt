package com.examingrity.cbt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.examingrity.cbt.network.ApiClient
import com.examingrity.cbt.network.AnswerItem
import com.examingrity.cbt.network.ExamRepository
import com.examingrity.cbt.network.SubmitUjianRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExamActivity : AppCompatActivity() {

    // ==========================================
    // 1. DEKLARASI UI & VIEWMODEL
    // ==========================================
    private lateinit var viewModel: ExamViewModel

    private lateinit var tvTimer: TextView
    private lateinit var tvNomorSoal: TextView
    private lateinit var tvPertanyaan: TextView
    private lateinit var tvPelanggaran: TextView
    private lateinit var rgOpsiJawaban: RadioGroup

    // Tambahkan variabel ini untuk mencegah CCTV salah paham
    private var isMembukaPanelJaringan = false
    private lateinit var rbA: RadioButton
    private lateinit var rbB: RadioButton
    private lateinit var rbC: RadioButton
    private lateinit var rbD: RadioButton
    private lateinit var btnSelanjutnya: Button
    private lateinit var btnSebelumnya: Button

    private val TAG = "ExamActivity"

    private lateinit var tvViolation: TextView // <--- TAMBAHKAN INI

    // ==========================================
    // 2. DEKLARASI SENSOR (JARINGAN & BATERAI)
    // ==========================================
    private lateinit var connectivityManager: ConnectivityManager
    private var isNetworkLost = false

    // Sensor Jaringan
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            super.onLost(network)
            runOnUiThread {
                Toast.makeText(this@ExamActivity, "Koneksi Terputus!", Toast.LENGTH_SHORT).show()
            }
            // isKecurangan = false -> Lapor ke guru, tapi jangan hukum siswanya!
            if (viewModel.participantId != -1) {
                viewModel.catatPelanggaran("LOST_CONNECTION", "Koneksi terputus.", isKecurangan = false)
            }
        }

        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            runOnUiThread {
                Toast.makeText(this@ExamActivity, "Koneksi Dipulihkan", Toast.LENGTH_SHORT).show()
            }
            // isKecurangan = false -> Lapor ke guru, tapi jangan hukum siswanya!
            if (viewModel.participantId != -1) {
                viewModel.catatPelanggaran("RESTORED_CONNECTION", "Koneksi internet kembali normal.", isKecurangan = false)
            }
        }
    }

    // Sensor Baterai Lemah & HP Mati
    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (viewModel.participantId == -1) return // Jangan rekam jika belum masuk sesi ujian

            when (intent?.action) {
                Intent.ACTION_BATTERY_LOW -> {
                    viewModel.catatPelanggaran("BATT_LOW", "Baterai perangkat kritis/lemah.")
                    Toast.makeText(context, "Baterai Lemah! Segera cas HP Anda.", Toast.LENGTH_LONG).show()
                }
                Intent.ACTION_SHUTDOWN -> {
                    viewModel.catatPelanggaran("SYS_SHUTDOWN", "Sistem Android mendeteksi perintah mematikan daya (HP Mati).")
                }
            }
        }
    }

    // ==========================================
    // 3. LIFECYCLE (Mulai Aplikasi)
    // ==========================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A. Setup Keamanan Native Android (Mencegah Screenshot & Kunci Layar)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        startLockTask() // Screen Pinning aktif

        setContentView(R.layout.activity_exam)

        // B. Inisialisasi UI
        initViews()

        // C. Setup ViewModel dengan Factory
        val repository = ExamRepository(ApiClient.instance)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ExamViewModel(repository) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[ExamViewModel::class.java]
        // Pasangkan konteks ke ViewModel untuk SQLite
        viewModel.initLocalDb(this)
        // D. Setup Listener Tombol & Observer Data
        setupListeners()
        setupObservers()

        // E. Aktifkan Sensor
        aktifkanSensor()

        // F. Mulai Ambil Data Ujian
        val tokenUjian = intent.getStringExtra("TOKEN_UJIAN") ?: ""
        if (tokenUjian.isEmpty()) {
            tampilkanErrorFatal("Token Ujian tidak valid!")
            return
        }

        // Tampilkan loading awal
        tvPertanyaan.text = "Mengunduh naskah soal yang aman. Mohon tunggu..."
        rgOpsiJawaban.visibility = View.GONE
        disableNavigation()

        // Panggil fungsi load di ViewModel
        viewModel.loadExamData(tokenUjian) { errorMessage ->
            runOnUiThread { tampilkanErrorFatal(errorMessage) }
        }
    }

    private fun aktifkanSensor() {
        // Registrasi Sensor Jaringan
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // Registrasi Sensor Baterai & Shutdown
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_SHUTDOWN)
        }
        registerReceiver(systemReceiver, filter)
    }

    // 4. OBSERVER (Mendengarkan Perubahan Data)
    // ==========================================
    private fun setupObservers() {
        lifecycleScope.launch {
            // Memastikan observer hanya aktif saat Activity tampil di layar
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                // KODE BARU: Pantau Daftar Soal (Agar loading hilang saat data masuk)
                launch {
                    viewModel.soalList.collectLatest { list ->
                        if (list.isNotEmpty()) {
                            renderSoalUI() // Gambar soalnya ke layar!
                        }
                    }
                }

                // Pantau Perubahan Index Soal (Navigasi Next/Prev)
                launch {
                    viewModel.currentIndex.collectLatest {
                        renderSoalUI()
                    }
                }

                // Pantau Timer (Teks)
                launch {
                    viewModel.timerText.collectLatest { waktu ->
                        tvTimer.text = waktu
                    }
                }

                // Pantau Status Timer Kritis (< 5 Menit)
                launch {
                    viewModel.isTimeUrgent.collectLatest { isUrgent ->
                        if (isUrgent) {
                            tvTimer.setTextColor(android.graphics.Color.WHITE)
                            tvTimer.setBackgroundColor(android.graphics.Color.parseColor("#DC2626")) // Merah
                        } else {
                            tvTimer.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                            tvTimer.setBackgroundColor(android.graphics.Color.parseColor("#FEE2E2")) // Merah Muda
                        }
                    }
                }

                // Pantau Jumlah Pelanggaran
                launch {
                    viewModel.jumlahPelanggaran.collectLatest { count ->
                        // UPDATE TEKS tvViolation YANG SELALU TAMPIL
                        tvViolation.text = "Pelanggaran: $count"

                        // JIKA INGIN MENGUBAH WARNANYA MENJADI MERAH SAAT ADA PELANGGARAN:
                        if (count > 0) {
                            tvViolation.setTextColor(android.graphics.Color.parseColor("#DC2626"))

                            // (Opsional) Tetap tampilkan tvPelanggaran yang icon warning
                            tvPelanggaran.visibility = View.VISIBLE
                            tvPelanggaran.text = "⚠️ Pelanggaran: $count"
                        }
                    }
                }

                // KODE BARU: Pantau Kiamat Ujian (Waktu Habis)
                launch {
                    viewModel.isExamFinished.collectLatest { selesai ->
                        if (selesai) {
                            Toast.makeText(this@ExamActivity, "Waktu habis! Ujian dikumpulkan otomatis.", Toast.LENGTH_LONG).show()
                            keluarModeAman() // Lempar siswa keluar
                        }
                    }
                }
            }
        }
    }
    // ==========================================
    // 5. RENDER UI & LISTENER TOMBOL
    // ==========================================
    private fun initViews() {
        tvTimer = findViewById(R.id.tvTimer)
        tvNomorSoal = findViewById(R.id.tvNomorSoal)
        tvPertanyaan = findViewById(R.id.tvPertanyaan)
        tvPelanggaran = findViewById(R.id.tvPelanggaran)
        tvViolation = findViewById(R.id.tvViolation)

        rgOpsiJawaban = findViewById(R.id.rgOpsiJawaban)
        rbA = findViewById(R.id.rbA)
        rbB = findViewById(R.id.rbB)
        rbC = findViewById(R.id.rbC)
        rbD = findViewById(R.id.rbD)

        btnSelanjutnya = findViewById(R.id.btnSelanjutnya)
        btnSebelumnya = findViewById(R.id.btnSebelumnya)
    }

    private fun setupListeners() {
        // Logika RadioButton (Jawaban Siswa)
        rgOpsiJawaban.setOnCheckedChangeListener { _, checkedId ->
            val list = viewModel.soalList.value
            val idx = viewModel.currentIndex.value

            if (list.isNotEmpty() && idx in list.indices) {
                val jawaban = when (checkedId) {
                    R.id.rbA -> "A"; R.id.rbB -> "B"; R.id.rbC -> "C"; R.id.rbD -> "D"
                    else -> ""
                }
                if (jawaban.isNotEmpty()) {
                    viewModel.simpanJawaban(list[idx].id, jawaban)
                }
            }
        }

        // Tombol Selanjutnya / Kumpul
        btnSelanjutnya.setOnClickListener {
            val totalSoal = viewModel.soalList.value.size
            val idx = viewModel.currentIndex.value

            if (idx < totalSoal - 1) {
                viewModel.nextSoal()
            } else {
                tampilkanKonfirmasiSelesai()
            }
        }

        // Tombol Sebelumnya
        btnSebelumnya.setOnClickListener {
            viewModel.prevSoal()
        }
    }

    private fun renderSoalUI() {
        val list = viewModel.soalList.value
        val idx = viewModel.currentIndex.value

        if (list.isEmpty() || idx !in list.indices) return

        val soal = list[idx]

        // Update Teks
        tvNomorSoal.text = "SOAL ${idx + 1} DARI ${list.size}"
        tvPertanyaan.text = soal.isi_soal

        // Bersihkan centangan radio sementara (agar tidak salah trigger listener)
        rgOpsiJawaban.setOnCheckedChangeListener(null)
        rgOpsiJawaban.clearCheck()

        // Render Opsi PG
        if (soal.tipe_soal == "pilihan_ganda" || soal.tipe_soal == "PG") {
            rgOpsiJawaban.visibility = View.VISIBLE
            val opsi = soal.pilihan_ganda
            if (opsi != null) {
                rbA.text = "A. ${opsi["A"] ?: ""}"
                rbB.text = "B. ${opsi["B"] ?: ""}"
                rbC.text = "C. ${opsi["C"] ?: ""}"

                if (!opsi["D"].isNullOrEmpty()) {
                    rbD.visibility = View.VISIBLE
                    rbD.text = "D. ${opsi["D"]}"
                } else {
                    rbD.visibility = View.GONE
                }
            }

            // Kembalikan jawaban siswa (jika sudah dijawab sebelumnya)
            when (viewModel.jawabanSiswa[soal.id]) {
                "A" -> rbA.isChecked = true
                "B" -> rbB.isChecked = true
                "C" -> rbC.isChecked = true
                "D" -> rbD.isChecked = true
            }

            // Nyalakan ulang listener
            setupListeners()
        } else {
            rgOpsiJawaban.visibility = View.GONE
            tvPertanyaan.text = "${soal.isi_soal}\n\n[Mode Essay belum didukung di UI versi ini]"
        }

        // Update Status Tombol Bawah
        btnSebelumnya.isEnabled = idx > 0

        if (idx == list.size - 1) {
            btnSelanjutnya.text = "Kumpulkan"
            btnSelanjutnya.setBackgroundColor(android.graphics.Color.parseColor("#10B981")) // Hijau
            btnSelanjutnya.isEnabled = true
        } else {
            btnSelanjutnya.text = "Selanjutnya"
            btnSelanjutnya.setBackgroundColor(android.graphics.Color.parseColor("#0052FF")) // Biru
            btnSelanjutnya.isEnabled = true
        }
    }

    // ==========================================
    // 6. LOGIKA SUBMIT & KEAMANAN TAMBAHAN
    // ==========================================
    private fun tampilkanKonfirmasiSelesai() {
        val totalDijawab = viewModel.jawabanSiswa.size
        val totalSoal = viewModel.soalList.value.size

        val pesan = if (totalDijawab < totalSoal) {
            "PERINGATAN: Anda baru menjawab $totalDijawab dari $totalSoal soal.\nYakin ingin mengakhiri ujian sekarang?"
        } else {
            "Anda sudah menjawab semua soal ($totalSoal/$totalSoal).\nKumpulkan jawaban sekarang?"
        }

        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Pengumpulan")
            .setMessage(pesan)
            .setCancelable(false)
            .setPositiveButton("Ya, Kumpulkan") { _, _ -> prosesKumpulJawaban() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun prosesKumpulJawaban() {
        disableNavigation()
        btnSelanjutnya.text = "Mengirim..."

        val answersList = viewModel.jawabanSiswa.map { AnswerItem(it.key, it.value) }
        val request = SubmitUjianRequest(answersList)

        // API Call langsung dari Activity untuk submit (Bisa juga dipindah ke ViewModel)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val res = ApiClient.instance.submitJawaban(viewModel.participantId, request)
                withContext(Dispatchers.Main) {
                    if (res.isSuccessful) {
                        Toast.makeText(this@ExamActivity, "Lembar jawaban berhasil dikirim!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@ExamActivity, "Gagal terkirim. Data tersimpan lokal.", Toast.LENGTH_LONG).show()
                    }
                    keluarModeAman()
                }
            } catch (e: Exception) {
                // ==========================================
                // PERBAIKAN: JIKA GAGAL KARENA TIDAK ADA INTERNET
                // ==========================================
                withContext(Dispatchers.Main) {
                    tampilkanDialogInternetMati()
                    btnSelanjutnya.isEnabled = true
                    btnSelanjutnya.text = "Kumpulkan Ulang"
                    btnSebelumnya.isEnabled = true
                }
            }
        }
    }

    private fun tampilkanDialogInternetMati() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Koneksi Internet Terputus 📡")
            .setMessage("Jangan panik! Jawaban Anda aman di penyimpanan HP. Namun, kami butuh internet untuk mengirimkannya ke server guru.\n\nSilakan minta Hotspot ke teman atau nyalakan Wi-Fi Anda sekarang.")
            .setCancelable(false)
            .setPositiveButton("Buka Pengaturan Wi-Fi") { _, _ ->
                // Beritahu CCTV agar merem sebentar
                isMembukaPanelJaringan = true

                // Panggil pop-up Wi-Fi bawaan Android
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val intent = Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                    startActivity(intent)
                } else {
                    val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                    startActivity(intent)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
    private fun tampilkanErrorFatal(pesan: String) {
        tvPertanyaan.text = pesan
        tvPertanyaan.setTextColor(android.graphics.Color.RED)
        rgOpsiJawaban.visibility = View.GONE

        btnSelanjutnya.isEnabled = true
        btnSelanjutnya.text = "Tutup Layar"
        btnSelanjutnya.setBackgroundColor(android.graphics.Color.RED)
        btnSelanjutnya.setOnClickListener { keluarModeAman() }
        btnSebelumnya.isEnabled = false
    }

    private fun disableNavigation() {
        btnSelanjutnya.isEnabled = false
        btnSebelumnya.isEnabled = false
    }

    // Tangkap jika siswa mencoba membelah layar (Split screen) atau keluar aplikasi
    override fun onPause() {
        super.onPause()

        if (isMembukaPanelJaringan) {
            isMembukaPanelJaringan = false
            return
        }

        if (viewModel.participantId != -1) {
            // INI KECURANGAN ASLI! Teks merah di layar akan bertambah dan tersimpan permanen
            viewModel.catatPelanggaran("APP_SWITCH", "Siswa meminimalkan aplikasi atau membuka notifikasi/split screen.")
            Toast.makeText(this, "Aktivitas mencurigakan terekam CCTV Sistem!", Toast.LENGTH_LONG).show()
        }
    }

    // Blokir tombol fisik "Back"
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        Toast.makeText(this, "Aksi diblokir! Gunakan tombol di layar.", Toast.LENGTH_SHORT).show()
    }

    private fun keluarModeAman() {
        try {
            stopLockTask() // Lepaskan kuncian
        } catch (e: Exception) {
            Log.e(TAG, "Gagal melepas screen pinning.")
        }
        finish() // Kembali ke Dashboard
    }

    // Lepas sensor saat Activity hancur agar tidak bocor memori
    override fun onDestroy() {
        super.onDestroy()
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            unregisterReceiver(systemReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal melepas sensor: ${e.message}")
        }
    }
}