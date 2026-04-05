package com.examingrity.cbt.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// ==========================================
// MODEL DATA (DATA CLASSES)
// ==========================================
data class LoginRequest(val email: String, val kata_sandi: String)

data class RegisterRequest(
    val nama_lengkap: String,
    val email: String,
    val kata_sandi: String,
    val role: String = "siswa" // Otomatis diset siswa
)

data class AuthResponse(val message: String, val user: UserData?)

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

// ==========================================
// DAFTAR ENDPOINT API (INTERFACE)
// ==========================================
interface ApiService {

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
}