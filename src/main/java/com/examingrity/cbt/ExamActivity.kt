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
import android.widget.ImageView
import android.widget.LinearLayout
import com.bumptech.glide.Glide
import android.net.Uri
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import androidx.core.widget.addTextChangedListener
import androidx.core.content.FileProvider
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

class ExamActivity : AppCompatActivity() {

    // ==========================================
    // 1. DEKLARASI UI & VIEWMODEL
    // ==========================================
    private lateinit var viewModel: ExamViewModel

    private lateinit var tvTimer: TextView
    private lateinit var tvNomorSoal: TextView
    private lateinit var tvPertanyaan: TextView
    private lateinit var layoutGambarSoal: LinearLayout
    private lateinit var ivSoalGambar: ImageView
    private lateinit var tvPelanggaran: TextView
    private lateinit var rgOpsiJawaban: RadioGroup

    //  Tambahan untuk Mode Esai
    private lateinit var layoutEssay: LinearLayout
    private lateinit var etJawabanEssay: EditText
    private lateinit var btnUploadGambarJawaban: Button
    private lateinit var ivPreviewJawaban: ImageView
    private lateinit var btnHapusGambarJawaban: Button

    // Path sementara untuk menampung lokasi file di internal storage
    private var localImagePathTemp: String? = null

    private var photoUriTemp: Uri? = null

    // Tambahkan variabel ini untuk mencegah CCTV salah paham
    private var isMembukaPanelJaringan = false
    private var isMemprosesKeluar = false
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
        layoutGambarSoal = findViewById(R.id.layoutGambarSoal)
        ivSoalGambar = findViewById(R.id.ivSoalGambar)
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

    // 4. OBSERVER (Mendengarkan Perubahan Data secara Agresif)
    // ==========================================
    private fun setupObservers() {
        // Pantau Daftar Soal (Langsung render saat data dari server tiba)
        lifecycleScope.launch {
            viewModel.soalList.collectLatest { list ->
                if (list.isNotEmpty()) {
                    renderSoalUI()
                }
            }
        }

        // Pantau Perubahan Navigasi (Nomor Soal)
        lifecycleScope.launch {
            viewModel.currentIndex.collectLatest {
                if (viewModel.soalList.value.isNotEmpty()) {
                    renderSoalUI()
                }
            }
        }

        // Pantau Perhitungan Mundur Timer
        lifecycleScope.launch {
            viewModel.timerText.collectLatest { waktu ->
                tvTimer.text = waktu
            }
        }

        // Pantau Peringatan Waktu Kritis (< 5 Menit)
        lifecycleScope.launch {
            viewModel.isTimeUrgent.collectLatest { isUrgent ->
                if (isUrgent) {
                    tvTimer.setTextColor(android.graphics.Color.WHITE)
                    tvTimer.setBackgroundColor(android.graphics.Color.parseColor("#DC2626")) // Merah solid
                } else {
                    tvTimer.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                    tvTimer.setBackgroundColor(android.graphics.Color.parseColor("#FEE2E2")) // Merah muda/pudar
                }
            }
        }

        // Pantau Sistem CCTV (Jumlah Pelanggaran)
        lifecycleScope.launch {
            viewModel.jumlahPelanggaran.collectLatest { count ->
                tvViolation.text = "Pelanggaran: $count"

                if (count > 0) {
                    tvViolation.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                    tvPelanggaran.visibility = View.VISIBLE
                    tvPelanggaran.text = "Pelanggaran: $count"
                }
            }
        }

        // Pantau Kiamat Ujian (Waktu Habis / Force Close oleh Guru)
        lifecycleScope.launch {
            viewModel.isExamFinished.collectLatest { selesai ->
                if (selesai) {
                    Toast.makeText(this@ExamActivity, "Waktu habis! Memproses pengumpulan otomatis...", Toast.LENGTH_LONG).show()
                    prosesKumpulJawaban() // 🚀 SEKARANG DIA AKAN OTOMATIS MENGUNGGAH MESKI WAKTU HABIS
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
//        rgOpsi = findViewById(R.id.rgOpsiJawaban)
        rbA = findViewById(R.id.rbA)
        rbB = findViewById(R.id.rbB)
        rbC = findViewById(R.id.rbC)
        rbD = findViewById(R.id.rbD)
        // 🚀 Tambahan untuk Gambar
        layoutGambarSoal = findViewById(R.id.layoutGambarSoal)
        ivSoalGambar = findViewById(R.id.ivSoalGambar)

        layoutEssay = findViewById(R.id.layoutEssay)
        etJawabanEssay = findViewById(R.id.etJawabanEssay)
        btnUploadGambarJawaban = findViewById(R.id.btnUploadGambarJawaban)
        ivPreviewJawaban = findViewById(R.id.ivPreviewJawaban)
        btnHapusGambarJawaban = findViewById(R.id.btnHapusGambarJawaban)

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

        // Buka Kamera HP
        btnUploadGambarJawaban.setOnClickListener {
            bukaKamera()
        }

        btnHapusGambarJawaban.setOnClickListener {
            val list = viewModel.soalList.value
            val idx = viewModel.currentIndex.value
            if (list.isNotEmpty() && idx in list.indices) {
                val soalId = list[idx].id
                val teksJawaban = etJawabanEssay.text.toString()

                // Hapus berkas fisik dari storage internal HP
                localImagePathTemp?.let { path -> java.io.File(path).delete() }

                localImagePathTemp = null
                viewModel.fotoSiswa.remove(soalId)
                ivPreviewJawaban.visibility = View.GONE
                btnHapusGambarJawaban.visibility = View.GONE

                // Update kembali status baris di SQLite menjadi tanpa gambar
                viewModel.getLocalDb()?.simpanJawabanLengkap(viewModel.participantId, soalId, teksJawaban, null)
            }
        }

        //  KECERDASAN FAULT TOLERANCE: Auto-save esai saat siswa sedang mengetik
        etJawabanEssay.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val list = viewModel.soalList.value
                val idx = viewModel.currentIndex.value
                if (list.isNotEmpty() && idx in list.indices) {
                    val soalId = list[idx].id
                    val teksJawaban = s.toString()

                    // Simpan ke runtime memori ViewModel
                    viewModel.jawabanSiswa[soalId] = teksJawaban
                    // Amankan fisik datanya langsung ke SQLite lokal
                    viewModel.getLocalDb()?.simpanJawabanLengkap(viewModel.participantId, soalId, teksJawaban, localImagePathTemp)
                }
            }
        })
    }

    //  PELUNCUR KAMERA LANGSUNG
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
        val list = viewModel.soalList.value
        val idx = viewModel.currentIndex.value

        if (isSuccess && localImagePathTemp != null && list.isNotEmpty() && idx in list.indices) {
            val soalId = list[idx].id
            val teksJawaban = etJawabanEssay.text.toString()

            // 1. Tampilkan Gambar di UI Preview secara Instan
            ivPreviewJawaban.visibility = View.VISIBLE
            btnHapusGambarJawaban.visibility = View.VISIBLE
            Glide.with(this).load(localImagePathTemp).into(ivPreviewJawaban)

            // 2. Kunci ke memori ViewModel & SQLite agar tidak hilang saat mati daya
            viewModel.fotoSiswa[soalId] = localImagePathTemp!!
            viewModel.getLocalDb()?.simpanJawabanLengkap(viewModel.participantId, soalId, teksJawaban, localImagePathTemp)

            Toast.makeText(this, "Foto tersimpan di enkripsi lokal!", Toast.LENGTH_SHORT).show()
        } else {
            localImagePathTemp = null
            photoUriTemp = null
        }
    }
    //  FUNGSI MEMPERSIAPKAN FILE & MEMBUKA KAMERA
    private fun bukaKamera() {
        // PENTING: Beritahu sistem CBT agar tidak mencatat ini sebagai pelanggaran "Keluar Aplikasi"
        isMembukaPanelJaringan = true

        // Buat file kosong di folder internal
        val photoFile = File(filesDir, "jawaban_${System.currentTimeMillis()}.jpg")
        localImagePathTemp = photoFile.absolutePath // Simpan path-nya untuk SQLite

        // Buat URI aman untuk aplikasi Kamera
        photoUriTemp = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            photoFile
        )

        // Luncurkan kamera bawaan HP! (Gunakan ?.let untuk memastikan tidak null)
        photoUriTemp?.let { uri ->
            takePictureLauncher.launch(uri)
        }
    }
    //  MESIN KLONING FILE (Kunci dari Fault Tolerance)
    private fun simpanKeInternalStorage(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)

        // Buat file baru di folder internal (Aman meskipun HP mati)
        val fileName = "jawaban_${System.currentTimeMillis()}.jpg"
        val file = File(filesDir, fileName)
        val outputStream = FileOutputStream(file)

        inputStream?.copyTo(outputStream)

        inputStream?.close()
        outputStream.close()

        return file.absolutePath // Contoh output: /data/user/0/com.examingrity.cbt/files/jawaban_123.jpg
    }

    private fun renderSoalUI() {
        val list = viewModel.soalList.value
        val idx = viewModel.currentIndex.value

        if (list.isEmpty() || idx !in list.indices) return

        val soal = list[idx]

        // Update Teks
        tvNomorSoal.text = "SOAL ${idx + 1} DARI ${list.size}"
        tvPertanyaan.text = soal.isi_soal

        // 🚀 LOGIKA MENAMPILKAN GAMBAR
        if (!soal.file_gambar.isNullOrEmpty()) {
            layoutGambarSoal.visibility = View.VISIBLE

            // Muat gambar pakai Glide
            Glide.with(this@ExamActivity)
                .load(soal.file_gambar)
                .into(ivSoalGambar)

            // Pasang sensor klik untuk Zoom
            ivSoalGambar.setOnClickListener {
                tampilkanZoomGambar(soal.file_gambar)
            }
        } else {
            // Sembunyikan jika soal tidak punya gambar
            layoutGambarSoal.visibility = View.GONE
        }

        // Bersihkan centangan radio sementara (agar tidak salah trigger listener)
        rgOpsiJawaban.setOnCheckedChangeListener(null)
        rgOpsiJawaban.clearCheck()

        // Render Opsi (PG atau Essay)
        if (soal.tipe_soal == "pilihan_ganda" || soal.tipe_soal == "PG") {
            // 1. Tampilkan UI PG, Sembunyikan UI Essay
            rgOpsiJawaban.visibility = View.VISIBLE
            layoutEssay.visibility = View.GONE

            val opsi = soal.pilihan_ganda
            if (opsi != null) {
                rbA.text = "A. ${opsi["A"] ?: ""}"
                rbB.text = "B. ${opsi["B"] ?: ""}"
                rbC.text = "C. ${opsi["C"] ?: ""}"

                // Cek Opsi D
                if (!opsi["D"].isNullOrEmpty()) {
                    rbD.visibility = View.VISIBLE
                    rbD.text = "D. ${opsi["D"]}"
                } else {
                    rbD.visibility = View.GONE // Sembunyikan opsi D jika memang tidak ada
                }
            }

            // 2. Tandai jawaban jika sudah ada di memori/SQLite
            when (viewModel.jawabanSiswa[soal.id]) {
                "A" -> rbA.isChecked = true
                "B" -> rbB.isChecked = true
                "C" -> rbC.isChecked = true
                "D" -> rbD.isChecked = true
                else -> rgOpsiJawaban.clearCheck() // 🚀 WAJIB ADA: Bersihkan jika belum dijawab
            }

        } else {
            // ==========================================
            // 🚀 RENDER UI SISI ESSAY (DENGAN RECOVERY PREVIEW FOTO)
            // ==========================================
            rgOpsiJawaban.visibility = View.GONE
            layoutEssay.visibility = View.VISIBLE

            // 1. Pulihkan Teks Jawaban Lama dari database lokal
            val jawabanLama = viewModel.jawabanSiswa[soal.id] ?: ""
            etJawabanEssay.setText(jawabanLama)

            // 2. Pulihkan Preview Gambar Jawaban Lama dari database lokal
            val pathFotoLokal = viewModel.fotoSiswa[soal.id]
            if (!pathFotoLokal.isNullOrEmpty() && java.io.File(pathFotoLokal).exists()) {
                localImagePathTemp = pathFotoLokal
                ivPreviewJawaban.visibility = View.VISIBLE
                btnHapusGambarJawaban.visibility = View.VISIBLE

                Glide.with(this@ExamActivity)
                    .load(pathFotoLokal)
                    .into(ivPreviewJawaban)
            } else {
                // Sembunyikan container preview jika data bersih dari lampiran
                localImagePathTemp = null
                ivPreviewJawaban.visibility = View.GONE
                btnHapusGambarJawaban.visibility = View.GONE
            }
        }

        // Nyalakan ulang listener
        setupListeners()

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
        btnSelanjutnya.text = "Mempersiapkan data..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val finalAnswers = mutableListOf<AnswerItem>()

                // 1. Tarik semua jawaban teks & foto dari brankas SQLite
                val savedAnswers = viewModel.getLocalDb()?.getSemuaJawabanTeks(viewModel.participantId) ?: emptyMap()
                val savedPhotos = viewModel.getLocalDb()?.getSemuaPathFoto(viewModel.participantId) ?: emptyMap()

                // 2. Loop semua soal untuk diunggah fotonya (jika ada)
                for ((soalId, jawabanTeks) in savedAnswers) {
                    var finalFileUrl: String? = null

                    val localPath = savedPhotos[soalId]
                    if (localPath != null) {
                        val file = File(localPath)
                        if (file.exists()) {
                            withContext(Dispatchers.Main) { btnSelanjutnya.text = "Mengunggah Foto Soal..." }

                            // Bungkus foto menjadi bentuk Multipart
                            val reqFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                            val body = MultipartBody.Part.createFormData("foto_jawaban", file.name, reqFile)

                            // Tembak foto ke Server Node.js
                            val uploadRes = ApiClient.instance.uploadFotoJawaban(viewModel.participantId, body)
                            if (uploadRes.isSuccessful) {
                                finalFileUrl = uploadRes.body()?.url // Dapatkan link URL publiknya
                            }
                        }
                    }
                    // Gabungkan teks dan Link foto (jika ada)
                    finalAnswers.add(AnswerItem(soalId, jawabanTeks, finalFileUrl))
                }

                // 3. Setelah semua foto terupload, kirim lembar jawaban finalnya!
                withContext(Dispatchers.Main) { btnSelanjutnya.text = "Menyerahkan Ujian..." }
                val request = SubmitUjianRequest(finalAnswers)
                val res = ApiClient.instance.submitJawaban(viewModel.participantId, request)

                withContext(Dispatchers.Main) {
                    if (res.isSuccessful) {
                        viewModel.getLocalDb()?.hapusSesi(viewModel.participantId) // Bersihkan SQLite
                        Toast.makeText(this@ExamActivity, "Lembar jawaban berhasil dikirim!", Toast.LENGTH_LONG).show()
                        keluarModeAman()
                    } else {
                        Toast.makeText(this@ExamActivity, "Gagal terkirim. Data aman di HP.", Toast.LENGTH_LONG).show()
                        btnSelanjutnya.isEnabled = true
                        btnSelanjutnya.text = "Kumpulkan Ulang"
                        btnSebelumnya.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                // 🛡️ RECOVERY SYSTEM: Jika internet mati saat sedang mengunggah
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
// ==========================================
    // 7. SIKLUS HIDUP & KEAMANAN LAYAR (SCREEN PINNING)
    // ==========================================

    // 🚀 TAMBAHAN: Paksa sembunyikan navigasi dan kunci layar saat aplikasi kembali fokus
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // 1. Kunci kembali layarnya (Immersive Mode)
            hideSystemUI()

            // 2. Pastikan Screen Pinning tetap aktif
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            if (activityManager.lockTaskModeState == android.app.ActivityManager.LOCK_TASK_MODE_NONE) {
                try {
                    startLockTask()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // 🚀 TAMBAHAN: Panggil kembali fungsi hide UI setiap kali activity dilanjutkan
    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    // 🚀 TAMBAHAN: Fungsi untuk menyembunyikan tombol navigasi bawah (Home, Back, Recent)
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
    }

    // Tangkap jika siswa mencoba membelah layar (Split screen) atau keluar aplikasi
// Tangkap jika siswa mencoba membelah layar (Split screen) atau keluar aplikasi
    override fun onPause() {
        super.onPause()

        // CEGAH FALSE ALARM: Jika siswa keluar karena sudah submit, hentikan pencatatan log!
        if (isMemprosesKeluar) return

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
        isMemprosesKeluar = true
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

    // 🚀 FUNGSI ZOOM GAMBAR (Letakkan sebelum tanda '}' terakhir di file ini)
    private fun tampilkanZoomGambar(imageUrl: String) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val photoView = com.github.chrisbanes.photoview.PhotoView(this)

        Glide.with(this).load(imageUrl).into(photoView)

        photoView.setOnClickListener {
            dialog.dismiss() // Tutup saat diketuk lagi
        }

        dialog.setContentView(photoView)
        dialog.show()
    }
}