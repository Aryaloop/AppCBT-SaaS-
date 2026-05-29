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
    fun loadExamData(token: String, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sesiRes = repository.mulaiSesi(token)

                if (sesiRes.isSuccessful) {
                    val data = sesiRes.body()?.data!!
                    participantId = data.participant_id

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
                                // Ini tidak akan crash lagi karena berjalan di Main Thread
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Array dikosongkan karena backend menghitung mandiri dari auto-save
                val res = repository.submitUjian(participantId, SubmitUjianRequest(emptyList()))
                if (res.isSuccessful) {
                    dbHelper?.hapusSesi(participantId) // Bersihkan sampah SQLite
                    _isExamFinished.value = true // Beritahu Activity untuk menutup layar
                }
            } catch (e: Exception) {
                android.util.Log.e("CBT_DEBUG", "Gagal auto-submit: ${e.message}")
            }
        }
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
    fun catatPelanggaran(jenis: String, deskripsi: String, isKecurangan: Boolean = true) {
        if (participantId == -1) return

        // HANYA TAMBAH ANGKA & SIMPAN KE MEMORI JIKA ITU BENAR-BENAR KECURANGAN (Bukan internet)
        if (isKecurangan) {
            _jumlahPelanggaran.value++
            // Simpan permanen ke memori HP agar tidak hilang saat aplikasi Crash
            sharedPrefs?.edit()?.putInt("VIOLATIONS_$participantId", _jumlahPelanggaran.value)?.apply()
        }

        val waktu = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.kirimLog(participantId, LogRequest(jenis, deskripsi, waktu))
            } catch (e: Exception) {
                android.util.Log.e("CBT_DEBUG", "Gagal kirim log CCTV (Siswa Offline)")
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