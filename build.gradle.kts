// Корневой build-файл. Версии плагинов централизованы в gradle/libs.versions.toml.
//
// AGP 9: Kotlin встроен (built-in) — плагин org.jetbrains.kotlin.android НЕ применяется.
// Compose-компилятор и kotlinx-serialization подключаются как плагины Kotlin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
