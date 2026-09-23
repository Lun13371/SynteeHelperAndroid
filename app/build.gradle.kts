plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "ru.syntee.helper"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ru.syntee.helper"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // Прод: API и веб Syntee-Gateway на одном домене syntee.fun
        // (nginx пользовательского фронта проксирует /api/* и /v1/* на backend).
        buildConfigField("String", "SYNTEE_API_URL", "\"https://syntee.fun\"")
        buildConfigField("String", "SYNTEE_WEB_URL", "\"https://syntee.fun\"")
    }

    buildTypes {
        debug {
            // Debug тоже ходит на ПРОД syntee.fun (юзер тестирует прод-гейтвей
            // и на эмуляторе, и на телефоне). Локальный dev-гейтвей при
            // необходимости включается в настройках: О приложении → dev URL.
        }
        release {
            // Стадия 06: включить R8 (optimization { enable = true }) + релизный keystore.
            optimization {
                enable = false
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Модули приложения
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:settings"))

    // AndroidX + Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.viewmodel.compose)

    // Корутины
    implementation(libs.coroutines.android)

    // Tooling
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)
}
