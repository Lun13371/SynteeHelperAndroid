plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
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

ksp {
    // Схемы Room в VCS — миграции с первого дня.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // api: core:data работает с entity-типами напрямую.
    api(libs.room.runtime)
    api(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
