package com.examingrity.cbt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examingrity.cbt.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.withContext
class ExamViewModel(private val repository: ExamRepository) : ViewModel() {

    // State: Daftar Soal & Navigasi
    private val _soalList = MutableStateFlow<List<SoalItem>>(emptyList())
    val soalList: StateFlow<List<SoalItem>> = _soalList

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex

    // State: Jawaban & Pelanggaran
    val jawabanSiswa = mutableMapOf<Int, String>()

    val fotoSiswa = mutableMapOf<Int, String>() // Menampung Map<SoalID, PathFotoLokal>
    private val _jumlahPelanggaran = MutableStateFlow(0)
    val jumlahPelanggaran: StateFlow<Int> = _jumlahPelanggaran

    // State: Timer & Info Sesi
    private val _timerText = MutableStateFlow("00:00")
    val timerText: StateFlow<String> = _timerText

    private val _isTimeUrgent = MutableStateFlow(false)
    val isTimeUrgent: StateFlow<Boolean> = _isTimeUrgent

    // 2. UBAH DEKLARASI participantId MENJADI SEPERTI INI (Fungsi Cerdas)
    var participantId: Int = -1
        set(value) {
            field = value
            // Saat ID Peserta dimasukkan dari API, langsung tarik riwayat pelanggaran dari memori HP!
            if (value != -1) {
                val riwayatPelanggaran = sharedPrefs?.getInt("VIOLATIONS_$value", 0) ?: 0
                _jumlahPelanggaran.value = riwayatPelanggaran
            }
        }
    private var isTimerRunning = false


    private var dbHelper: LocalDBHelper? = null

    // 1. TAMBAHKAN VARIABEL INI
    private var sharedPrefs: android.content.SharedPreferences? = null
    private val _isExamFinished = MutableStateFlow(false)
    val isExamFinished: StateFlow<Boolean> = _isExamFinished

// Fungsi: Ambil Data Ujian
// Fungsi: Ambil Data Ujian
fun loadExamData(token: String, onError: (String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            // 🚀 PERBAIKAN: Tarik token CSRF terlebih dahulu agar masuk ke CookieJar
            try {
                repository.fetchCsrfToken()
            } catch (e: Exception) {
                android.util.Log.e("CBT_DEBUG", "Gagal fetch CSRF Token: ${e.message}")
                // Lanjut saja, biarkan backend yang menolak jika memang token wajib
            }

            // Setelah token tersimpan di memori Android, baru eksekusi POST mulai sesi
            val sesiRes = repository.mulaiSesi(token)

            if (sesiRes.isSuccessful) {
                // ✅ BARIS YANG KEMBALI DITAMBAHKAN (Sempat Terhapus)
                val data = sesiRes.body()?.data!!
                participantId = data.participant_id

                // Ambil daftar soal berdasarkan ID Peserta
                val soalRes = repository.getSoal(participantId)

                if (soalRes.isSuccessful) {
                    val daftarSoal = soalRes.body()?.data ?: emptyList()

                    if (daftarSoal.isEmpty()) {
                        withContext(Dispatchers.Main) { onError("Daftar soal dari server kosong.") }
                    } else {
                        // 🛡️ RECOVERY SQLite di jalur Background (Aman)
                        val savedAnswers = getLocalDb()?.getSemuaJawabanTeks(participantId) ?: emptyMap()
                        val savedPhotos = getLocalDb()?.getSemuaPathFoto(participantId) ?: emptyMap()

                        jawabanSiswa.putAll(savedAnswers)
                        fotoSiswa.putAll(savedPhotos)

                        // 🚀 PERBAIKAN RACE CONDITION:
                        // Lempar UI Update & Timer WAJIB ke Main Thread
                        withContext(Dispatchers.Main) {
                            _soalList.value = daftarSoal
                            // Menggunakan 'data' yang sudah dikembalikan di atas
                            startTimer(data.sisa_waktu_menit * 60)
                        }
                    }
                } else {
                    // ❌ Gagal Tarik Soal
                    val errSoal = soalRes.errorBody()?.string()
                    val pesanSpesifik = try {
                        org.json.JSONObject(errSoal!!).getString("message")
                    } catch (e: Exception) { "Gagal menarik daftar soal dari server." }
                    withContext(Dispatchers.Main) { onError(pesanSpesifik) }
                }
            } else {
                // ❌ Gagal Mulai Sesi
                val errSesi = sesiRes.errorBody()?.string()
                val pesanSpesifik = try {
                    org.json.JSONObject(errSesi!!).getString("message")
                } catch (e: Exception) { "Sesi ditolak. PIN salah atau ujian belum dimulai." }
                withContext(Dispatchers.Main) { onError(pesanSpesifik) }
            }
        } catch (e: Exception) {
            // Tangkap error jaringan agar tidak Force Close
            android.util.Log.e("CBT_DEBUG", "💥 CRASH/EXCEPTION: ${e.message}", e)
            withContext(Dispatchers.Main) { onError("Sistem mendeteksi error jaringan/server: ${e.message}") }
        }
    }
}


    // Fungsi: Timer (Looping Coroutine)
    private fun startTimer(totalDetik: Int) {
        if (isTimerRunning) return
        isTimerRunning = true
        var sisa = totalDetik

        viewModelScope.launch {
            while (sisa >= 0) {
                val m = sisa / 60
                val s = sisa % 60
                _timerText.value = String.format("%02d:%02d", m, s)
                _isTimeUrgent.value = m < 5

                // LOGIC WAKTU HABIS
                if (sisa == 0) {
                    submitUjianOtomatis()
                }

                delay(1000)
                sisa--
            }
        }
    }

    // Fungsi Submit saat Waktu Habis (Atau saat ditekan tombol Selesai manual)
    fun submitUjianOtomatis() {
        if (participantId == -1) return

        // Cukup beritahu Activity bahwa waktu sudah habis.
        // Biarkan ExamActivity yang mengumpulkan foto, teks, dan menghapus SQLite.
        _isExamFinished.value = true
    }

    fun nextSoal() { if (_currentIndex.value < _soalList.value.size - 1) _currentIndex.value++ }
    fun prevSoal() { if (_currentIndex.value > 0) _currentIndex.value-- }

    fun simpanJawaban(soalId: Int, jawaban: String) {
        jawabanSiswa[soalId] = jawaban

        // 1. Simpan ke SQLite Lokal (Kecepatan & Tahan Banting)
        dbHelper?.simpanJawaban(participantId, soalId, jawaban)

        // 2. Tembak ke Server di Balik Layar (Asinkron)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.autoSave(participantId, AutoSaveRequest(soalId, jawaban))
            } catch (e: Exception) {
                // Jika gagal (Misal internet mati), abaikan saja!
                // Kenapa? Karena data sudah aman di SQLite lokal dan bisa dikirim nanti.
            }
        }
    }

    // 3. TAMBAHKAN PARAMETER isKecurangan = true
// 🚀 PERBAIKAN: Fungsi catatPelanggaran
    fun catatPelanggaran(jenis: String, deskripsi: String, isKecurangan: Boolean = true) {
        if (participantId == -1) return

        if (isKecurangan) {
            _jumlahPelanggaran.value++
            sharedPrefs?.edit()?.putInt("VIOLATIONS_$participantId", _jumlahPelanggaran.value)?.apply()
        }

        val waktu = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        viewModelScope.launch(Dispatchers.IO) {
            // 1. Amankan ke brankas SQLite dulu!
            val logId = dbHelper?.simpanLogTertunda(participantId, jenis, deskripsi, waktu) ?: -1L

            try {
                // 2. Coba kirim langsung ke server
                val response = repository.kirimLog(participantId, LogRequest(jenis, deskripsi, waktu))

                // 3. Jika berhasil sampai ke server, hapus dari antrean lokal
                if (response.isSuccessful && logId != -1L) {
                    dbHelper?.hapusLog(logId)
                }
            } catch (e: Exception) {
                // Jika error (koneksi putus), biarkan saja. Data sudah aman di SQLite.
                android.util.Log.e("CBT_DEBUG", "Log gagal dikirim, tertahan aman di SQLite: ${e.message}")
            }
        }
    }

    // 🚀 TAMBAHAN: Mesin Sinkronisasi Log Tertunda
    fun syncLogTertunda() {
        if (participantId == -1) return

        viewModelScope.launch(Dispatchers.IO) {
            val pendingLogs = dbHelper?.getLogTertunda(participantId) ?: emptyList()

            for (log in pendingLogs) {
                try {
                    val req = LogRequest(
                        jenis_log = log["jenis_log"]!!,
                        deskripsi = log["deskripsi"]!!,
                        waktu_kejadian_lokal = log["waktu"]!!
                    )
                    val res = repository.kirimLog(participantId, req)

                    if (res.isSuccessful) {
                        dbHelper?.hapusLog(log["id"]!!.toLong()) // Bersihkan dari antrean
                    }
                } catch (e: Exception) {
                    break // Berhenti me-looping jika internet masih mati
                }
            }
        }
    }

    // Inisialisasi SQLite dari Activity
    // Inisialisasi SQLite dan SharedPreferences dari Activity
    fun initLocalDb(context: android.content.Context) {
        if (dbHelper == null) {
            dbHelper = LocalDBHelper(context)
        }
        if (sharedPrefs == null) {
            sharedPrefs = context.getSharedPreferences("CBT_PREFS", android.content.Context.MODE_PRIVATE)
        }
    }
    // 🚀 FUNGSI PINTU: Memberikan akses dbHelper ke Activity tanpa membuka akses private
    fun getLocalDb(): LocalDBHelper? {
        return dbHelper
    }
}