plugins {
    alias(libs.plugins.android.library)
}

// Стадия 02: Retrofit/OkHttp, SSE-клиент, AuthInterceptor, DTO gateway.
android {
    namespace = "ru.syntee.helper.core.network"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
