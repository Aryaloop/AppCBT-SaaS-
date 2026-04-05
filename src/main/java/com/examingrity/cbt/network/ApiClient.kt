package com.examingrity.cbt.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Sesuaikan URL ini dengan alamat DevTunnel atau Localhost Anda saat testing dari HP.
    // Jika testing menggunakan Emulator Android Studio, gunakan "http://10.0.2.2:3000/api/"
    private const val BASE_URL = "https://tqbn4sng-3000.asse.devtunnels.ms/api/"

    // RAHASIA APLIKASI (Sama dengan APP_INTERNAL_SECRET di backend)
    private const val APP_SECRET = "Rahas1a_Sistem_Ujian_SaaS_2026!"

    val instance: ApiService by lazy {
        // Interceptor untuk menyisipkan header keamanan di setiap request
        val headerInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("x-app-signature", APP_SECRET)
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }

        // Interceptor untuk melihat log error di Logcat Android Studio
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(headerInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}