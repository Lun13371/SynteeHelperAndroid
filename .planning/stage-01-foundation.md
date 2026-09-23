# СТАДИЯ 01 — ФУНДАМЕНТ

> Цель: собираемый скелет Android-приложения. После этой стадии приложение
> запускается на эмуляторе/устройстве, показывает каркас навигации с пустыми
> экранами, тема Syntee применена, DI работает. Никакой сети и чатов ещё нет.

---

## ЧТО БУДЕТ ГОТОВО ПОСЛЕ СТАДИИ

- Проект собирается: `./gradlew :app:assembleDebug` без ошибок
- Модульная структура создана: app, core:{common,designsystem,network,database,datastore,data}, feature:{auth,chat,settings}
- MainActivity + Compose + Navigation (type-safe маршруты) — пустые экраны-заглушки
- Hilt настроен во всех модулях
- Тема Syntee: Material 3 + фирменная палитра, тёмная/светлая, edge-to-edge
- Version catalog (libs.versions.toml) — все зависимости централизованно
- BuildConfig: SYNTEE_API_URL / SYNTEE_WEB_URL для debug/release
- Kotlin 2.2, KSP, Compose compiler plugin — всё через catalog

---

## ТЕХНОЛОГИИ ЭТОЙ СТАДИИ

```
Kotlin 2.2+, AGP 8.10+, KSP 2
Compose BOM (актуальный), Material 3
Hilt 2.56+, hilt-navigation-compose
Navigation Compose 2.9+ (type-safe)
DataStore 1.1+, Room 2.7+, Retrofit 2.11+/OkHttp 5 (зависимости заведены, используются со стадии 02)
```

---

## ЗАДАЧИ

### 1. Инициализация проекта

```bash
cd C:\Projects\SynteeHelperAndroid
# Android Studio → New Project → Empty Activity (Compose)
# Package: ru.syntee.helper, Name: Syntee Helper, minSdk 26
```

`settings.gradle.kts` — все модули. `gradle/libs.versions.toml` — version catalog:

```toml
[versions]
kotlin = "2.2.x"
agp = "8.10.x"
composeBom = "2026.xx.xx"
hilt = "2.56.x"
ksp = "2.2.x-x"
room = "2.7.x"
retrofit = "2.11.x"
okhttp = "5.x.x"
datastore = "1.1.x"
navigation = "2.9.x"
kotlinxSerialization = "1.8.x"
coil = "3.x.x"
turbine = "1.x.x"
mockwebserver = "5.x.x"
```

Общие настройки в root `build.gradle.kts` + convention: каждый модуль
`android { compileSdk 36, defaultConfig minSdk 26 }`, `kotlinOptions jvmTarget = 17`,
Compose только в ui-модулях (designsystem, features, app).

### 2. Модули и базовые зависимости

| Модуль | Содержимое | Зависимости |
|---|---|---|
| `:core:common` | AppError, Result-хелперы, time/dispacher утилиты | — |
| `:core:designsystem` | Тема, токены, компоненты | compose-bom |
| `:core:network` | (стадия 02) | okhttp, retrofit, kotlinx-serialization |
| `:core:database` | (стадия 03) | room |
| `:core:datastore` | (стадия 02) | datastore, security |
| `:core:data` | (стадии 02–03) | все core, hilt |
| `:feature:auth` | LoginScreen (заглушка) | designsystem, hilt-nav |
| `:feature:chat` | ChatListScreen, ChatScreen (заглушки) | designsystem |
| `:feature:settings` | SettingsScreen (заглушка) | designsystem |
| `:app` | MainActivity, NavHost, DI | все |

### 3. MainActivity + навигация

```kotlin
// app/src/main/kotlin/ru/syntee/helper/MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()  // edge-to-edge с первого дня
        super.onCreate(savedInstanceState)
        setContent { SynteeApp() }
    }
}
```

Type-safe маршруты (kotlinx.serialization):

```kotlin
@Serializable data object LoginRoute
@Serializable data object ChatListRoute
@Serializable data class ChatRoute(val chatId: String)
@Serializable data object SettingsRoute

// NavHost: Login → ChatList → (Chat | Settings)
// Стартовый маршрут решает AuthViewModel: есть токен → ChatList, нет → Login
```

Нижняя навигация: **Чаты** / **Настройки** (2 таба — минимум для базы).
Bottom bar скрывается на экране чата (immersive-режим).

### 4. Тема Syntee (core:designsystem)

Гибрид Material 3 + фирменный стиль. Файлы:

```
designsystem/
├── theme/
│   ├── SynteeTheme.kt      // вход: SynteeTheme(mode, accent) { content }
│   ├── SynteeColors.kt     // палитра Syntee: бренд-акцент, фоны, тёмная/светлая/AMOLED
│   ├── SynteeTypography.kt // шрифты (Inter/Manrope), масштаб
│   └── SynteeShapes.kt     // скругления (крупные, «дружелюбные»)
├── components/             // SynteeButton, SynteeCard, SynteeTextField, Loading, EmptyState
└── anim/                   // spring-пресеты (как motionPresets на десктопе)
```

- Брендовая палитра: за основу взять акцент десктопа (посмотреть Logo/цвета в
  C:\Projects\SynteeHelper\src\renderer\src\components\ui\Logo) — единый бренд
- `darkColorScheme()` / `lightColorScheme()` + AMOLED-вариант (фон #000000)
- Динамические цвета (Material You) — хук есть, включается в кастомизации (стадия 05)
- Все цвета/типографика — токены, никаких хардкодов в фичах

### 5. BuildConfig и окружение

```kotlin
// app/build.gradle.kts
buildTypes {
    debug {
        buildConfigField("String", "SYNTEE_API_URL", "\"http://10.0.2.2:8000\"")
        buildConfigField("String", "SYNTEE_WEB_URL", "\"http://10.0.2.2:3000\"")
    }
    release {
        // Прод: API и веб на ОДНОМ домене syntee.fun (nginx фронта проксирует /api/* и /v1/* на backend)
        buildConfigField("String", "SYNTEE_API_URL", "\"https://syntee.fun\"")
        buildConfigField("String", "SYNTEE_WEB_URL", "\"https://syntee.fun\"")
        isMinifyEnabled = true; isShrinkResources = true
    }
}
```

Dev-переключатель URL появится в настройках (стадия 04) для реального устройства (LAN IP ПК).

### 6. Заглушки экранов

- **LoginScreen**: логотип Syntee, заглушки кнопок «Войти» и «Продолжить как гость»
- **ChatListScreen**: пустое состояние «Чатов пока нет»
- **ChatScreen**: каркас (шапка, тело, инпут-бар) без логики
- **SettingsScreen**: список секций-заглушек

Каждый экран — `Screen()` composable + `ViewModel` (Hilt) с пустым `StateFlow`.

---

## КРИТЕРИИ ГОТОВНОСТИ

- [ ] `./gradlew :app:assembleDebug` — успешно
- [ ] Приложение запускается на эмуляторе (API 26+ и API 36)
- [ ] Тёмная/светлая тема переключается системно, edge-to-edge работает
- [ ] Навигация между заглушками работает, bottom bar скрывается в чате
- [ ] Hilt-граф собирается, ViewModel инжектятся во всех фичах
- [ ] Нет хардкода цветов/строк — всё в designsystem/strings.xml
- [ ] Lint (`./gradlew lint`) без критичных ошибок
