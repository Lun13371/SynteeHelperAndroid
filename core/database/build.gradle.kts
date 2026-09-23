plugins {
    alias(libs.plugins.android.library)
}

// Стадия 03: Room — чаты, сообщения, стриминг-буферы.
android {
    namespace = "ru.syntee.helper.core.database"
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
