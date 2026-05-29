plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt") // <--- Tambahkan baris ini
}

android {
    namespace = "com.examingrity.cbt"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.examingrity.cbt"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // 1. Room Database (Untuk Local Persistence / Menyimpan Jawaban Sementara)
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version") // Gunakan ksp jika Anda mengaktifkan KSP
    // To use Kotlin annotation processing tool (kapt)
    kapt("androidx.room:room-compiler:$room_version")
    // implementation("androidx.room:room-ktx:$room_version") // Opsional, untuk dukungan Coroutines di Room

    // 2. Retrofit & GSON (Untuk komunikasi dengan API Backend Node.js Anda)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // 3. OkHttp Logging Interceptor (Sangat berguna untuk melihat log request/response API saat debugging)
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // 4. Coroutines (Untuk menjalankan tugas di latar belakang (background) agar aplikasi tidak freeze)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Library yang membawa fitur 'viewModelScope' untuk ExamViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Library yang membawa fitur 'repeatOnLifecycle' untuk ExamActivity
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // Glide untuk memuat gambar dari URL URL Supabase
    implementation("com.github.bumptech.glide:glide:4.16.0")
// PhotoView untuk fitur cubit & zoom
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
}