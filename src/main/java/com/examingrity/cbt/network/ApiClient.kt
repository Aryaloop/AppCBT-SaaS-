package com.examingrity.cbt.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Sesuaikan dengan URL DevTunnels Anda
//    private const val BASE_URL = "https://tqbn4sng-3000.asse.devtunnels.ms/api/"
    private const val BASE_URL = "https://api.examingrity.my.id/api/"
    private const val APP_SECRET = "Rahas1a_Sistem_Ujian_SaaS_2026!"

    // KODE BARU: Penyimpanan Cookie Sementara di Memori Android (DIperbaiki)
    private val cookieStore = HashMap<String, MutableList<Cookie>>()
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            // Hanya proses jika server memang mengirimkan Cookie (Jangan timpa dengan list kosong!)
            if (cookies.isNotEmpty()) {
                val existingCookies = cookieStore[url.host] ?: mutableListOf()

                // Tambahkan cookie baru, atau timpa cookie lama jika namanya sama (misal JWT diperbarui)
                for (newCookie in cookies) {
                    existingCookies.removeAll { it.name == newCookie.name }
                    existingCookies.add(newCookie)
                }
                cookieStore[url.host] = existingCookies
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: ArrayList()
        }
    }
    val instance: ApiService by lazy {
        val headerInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
                .addHeader("x-app-signature", APP_SECRET)
                .addHeader("Content-Type", "application/json")

            // 🚀 LOGIKA BARU: Ekstrak CSRF Token dari Cookie yang tersimpan
            val urlHost = chain.request().url.host
            val cookies = cookieStore[urlHost]
            val csrfCookie = cookies?.find { it.name == "XSRF-TOKEN" }

            // Jika token CSRF ada di memori, masukkan ke header
            if (csrfCookie != null) {
                requestBuilder.addHeader("X-XSRF-TOKEN", csrfCookie.value)
            }

            chain.proceed(requestBuilder.build())
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar) // <--- KUNCI PERBAIKAN ERROR 401 ADA DI SINI
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