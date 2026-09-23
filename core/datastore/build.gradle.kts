plugins {
    alias(libs.plugins.android.library)
}

// Стадия 02: DataStore — настройки, токены (Keystore AES-GCM), провайдеры гостя.
android {
    namespace = "ru.syntee.helper.core.datastore"
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
