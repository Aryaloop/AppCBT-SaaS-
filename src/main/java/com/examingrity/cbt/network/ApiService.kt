package com.examingrity.cbt.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.PUT
import okhttp3.MultipartBody
import retrofit2.http.Part
import retrofit2.http.Multipart
// ==========================================
// MODEL DATA (DATA CLASSES)
// ==========================================

data class CsrfResponse(val csrfToken: String)

data class LoginRequest(val email: String, val kata_sandi: String)

data class RegisterRequest(
    val nama_lengkap: String,
    val email: String,
    val kata_sandi: String,
    val role: String = "siswa" // Otomatis diset siswa
)

data class AuthResponse(val message: String, val user: UserData?)


// Model Data untuk Log Pelanggaran
data class LogRequest(
    val jenis_log: String,
    val deskripsi: String,
    val waktu_kejadian_lokal: String
)
data class UserData(
    val id: Int,
    val nama_lengkap: String,
    val email: String,
    val role: String,
    val school_id: Int?
)

data class PublicKeyResponse(val publicKey: String)

data class ResetPasswordRequest(val password_baru: String)

data class ForgotPasswordRequest(val email: String)
// Model untuk memulai sesi ujian
data class MulaiUjianResponse(val message: String, val data: UjianSessionData)
data class UjianSessionData(
    val participant_id: Int,
    val judul_ujian: String,
    val durasi_menit: Int,
    val sisa_waktu_menit: Int
)

// Model untuk daftar soal
data class DaftarSoalResponse(val message: String, val data: List<SoalItem>)
data class SoalItem(
    val id: Int,
    val tipe_soal: String,
    val isi_soal: String,
    val pilihan_ganda: Map<String, String>?,
    // 🚀 TAMBAHKAN INI:
    val file_gambar: String?
)

// --- MODEL DATA RIWAYAT ---
data class RiwayatResponse(val message: String, val disclaimer: String, val data: List<RiwayatItem>)
data class RiwayatItem(
    val participant_id: Int,
    val judul_ujian: String,
    val waktu_login: String,
    val waktu_selesai: String?,
    val status: String,
    val nilai_akhir: String?
)

// --- MODEL DATA PROFIL ---
// --- MODEL DATA PROFIL ---
data class TautkanSekolahRequest(
    val token_sekolah: String,
    val nisn: String
)
data class UpdateProfileRequest(
    val nama_lengkap: String? = null,
    val password_lama: String? = null, // Set to nullable because they might only update NISN
    val password_baru: String? = null,
    val nisn: String? = null // NEW: Add NISN field
)
data class GeneralResponse(
    val message: String
)

// Model untuk mengirim jawaban
data class SubmitUjianRequest(val answers: List<AnswerItem>)
data class AnswerItem(val question_id: Int, val jawaban: String, val file_jawaban: String? = null)
data class SubmitResponse(val message: String, val nilai_akhir: String, val total_benar: Int)

data class AutoSaveRequest(val question_id: Int, val jawaban_siswa: String)

data class ProfilResponse(
    val nama_lengkap: String,
    val email: String,
    val sekolah: String,
    val nisn: String? // NEW: Add NISN field
)
// ==========================================
// DAFTAR ENDPOINT API (INTERFACE)
// ==========================================
interface ApiService {

//        Tambahkan endpoint ini di dalam interface ApiService
    @GET("https://api.examingrity.my.id/csrf-token")
    suspend fun getCsrfToken(): retrofit2.Response<CsrfResponse>
    @GET("auth/public-key")
    suspend fun getPublicKey(): Response<PublicKeyResponse>

    @POST("auth/login")
    suspend fun loginSiswa(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun registerSiswa(@Body request: RegisterRequest): Response<AuthResponse>

    @GET("auth/verify-email/{token}")
    suspend fun verifyEmailToken(@Path("token") token: String): Response<AuthResponse>

    @POST("auth/reset-password/{token}")
    suspend fun resetPassword(
        @Path("token") token: String,
        @Body request: ResetPasswordRequest
    ): Response<AuthResponse>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<AuthResponse>

    @POST("siswa/ujian/{pin}/mulai")
    suspend fun mulaiSesiUjian(@Path("pin") pin: String): retrofit2.Response<MulaiUjianResponse>

    @GET("siswa/ujian/{participant_id}/soal")
    suspend fun getDaftarSoal(@Path("participant_id") participantId: Int): retrofit2.Response<DaftarSoalResponse>

    @POST("siswa/ujian/{participant_id}/submit")
    suspend fun submitJawaban(
        @Path("participant_id") participantId: Int,
        @Body request: SubmitUjianRequest
    ): retrofit2.Response<SubmitResponse>

    @GET("siswa/riwayat")
    suspend fun getRiwayatUjian(): retrofit2.Response<RiwayatResponse>

    @POST("siswa/profile/tautkan-sekolah")
    suspend fun tautkanSekolah(@Body request: TautkanSekolahRequest): retrofit2.Response<GeneralResponse>

    @PUT("siswa/profile/update")
    suspend fun updatePassword(@Body request: UpdateProfileRequest): retrofit2.Response<GeneralResponse>

    // Endpoint Kirim Log Pelanggaran
    @POST("siswa/ujian/{participant_id}/log")
    suspend fun sendLogPelanggaran(
        @Path("participant_id") participantId: Int,
        @Body request: LogRequest
    ): retrofit2.Response<GeneralResponse>

    @POST("siswa/ujian/{participant_id}/jawaban")
    suspend fun autoSaveJawaban(
        @Path("participant_id") participantId: Int,
        @Body request: AutoSaveRequest
    ): retrofit2.Response<GeneralResponse> // GeneralResponse bisa disesuaikan jika kamu pakai nama lain

    @GET("siswa/profile")
    suspend fun getProfileSiswa(): retrofit2.Response<ProfilResponse>

    data class UploadResponse(val url: String)
    @Multipart
    @POST("siswa/ujian/{participant_id}/upload")
    suspend fun uploadFotoJawaban(
        @Path("participant_id") participantId: Int,
        @Part foto_jawaban: MultipartBody.Part
    ): retrofit2.Response<UploadResponse>
}