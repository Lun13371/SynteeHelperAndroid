# СТАДИЯ 02 — ИНТЕГРАЦИЯ С SYNTEE-GATEWAY

> Цель: сетевой слой и авторизация. После этой стадии: вход через браузер по
> deep link работает, JWT хранится и авто-обновляется, каталог моделей со
> статусами/uptime загружается, гостевой режим переключается, профиль
> пользователя доступен. Чата ещё нет — только данные.

---

## ЧТО БУДЕТ ГОТОВО ПОСЛЕ СТАДИИ

- Логин: Chrome Custom Tabs → consent (с формой логина email+пароль, если юзер не залогинен) → deep link `synteehelper://auth/callback` → JWT в зашифрованном DataStore
- Фолбэк: повтор браузерного флоу (код одноразовый, живёт 10 секунд — ручной ввод невозможен)
- AuthInterceptor: Bearer JWT на все запросы + авто-refresh на 401 (singleton-promise, JSON body, одноразовый токен)
- Guest-режим: флаг в DataStore, gateway не дёргается
- Каталог моделей: `/v1/models` + `/v1/model-status/buckets` + `/v1/provider-status` — репозиторий + кэш
- Профиль: `/api/user/auth/me` (GET/PATCH)
- Identity-заголовки на все запросы + опция «анонимный UA»
- SSE-клиент (okhttp-sse) — инфраструктура для стриминга (сам чат в стадии 03)
- Unit-тесты: refresh-флоу, 401-ретрай, парсер моделей (MockWebServer)

---

## ТЕХНОЛОГИИ ЭТОЙ СТАДИИ

```
Retrofit + OkHttp + kotlinx-serialization-converter
okhttp-sse (EventSource)
DataStore + Android Keystore (AES-GCM) для токенов
Chrome Custom Tabs (androidx.browser)
```

---

## ЗАДАЧИ

### 1. Сетевой слой (core:network)

```
core/network/
├── GatewayApi.kt            // Retrofit-интерфейс: auth, models, billing, user
├── dto/                     // DTO с @Serializable, мапперы в доменные модели
│   ├── AuthDto.kt           // TokenResponse, GatewayUser
│   ├── ModelsDto.kt         // GatewayModel, status buckets, provider status
│   └── BillingDto.kt
├── interceptors/
│   ├── AuthInterceptor.kt   // Bearer + refresh-on-401
│   └── IdentityInterceptor.kt // UA/X-Client-Name/X-Client-Version
├── sse/
│   ├── SseClient.kt         // POST + EventSource (для стадии 03)
│   └── ChatSseParser.kt     // парсинг delta (content, reasoning 3 формата, thinking)
├── TokenStore.kt            // интерфейс (реализация в datastore)
└── NetworkModule.kt         // Hilt: OkHttpClient, Retrofit, Json
```

OkHttp: `connectTimeout 15s`, `readTimeout 0` (SSE!), `callTimeout 4 мин`,
`retryOnConnectionFailure true`. Один OkHttp на всё приложение.

**Network security config** (`app/src/main/res/xml/network_security_config.xml`):
- debug: cleartext разрешён только для `10.0.2.2` и LAN-подсетей (dev-гейтвей на ПК)
- release: только HTTPS (`cleartextTrafficPermitted="false"`) — прод `syntee.fun` за Caddy/Let's Encrypt
- В манифесте: `android:networkSecurityConfig="@xml/network_security_config"`

**IdentityInterceptor** (на ВСЕ запросы к gateway):
```
User-Agent: syntee-agent-android/{BuildConfig.VERSION_NAME}
X-Client-Name: syntee-agent-android
X-Client-Version: {BuildConfig.VERSION_NAME}
```
При включённой опции `anonymousUserAgent` (настройки) — заголовки не ставятся.

### 2. Токены: хранение и refresh (core:datastore)

**TokenStore** — DataStore + Keystore:

```kotlin
interface TokenStore {
    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?
    suspend fun setTokens(access: String, refresh: String)
    suspend fun clear()
}
```

- Шифрование: Android Keystore, ключ AES-256-GCM, шифруем оба токена перед записью
- `isAccessTokenExpired(token)`: decode JWT payload (Base64 URL-safe), exp с запасом 30с; не-JWT → считаем валидным (401-ретрай подстрахует)

**AuthInterceptor — refresh-on-401** (по АКТУАЛЬНОМУ контракту gateway; ⚠️ десктоп до сих пор шлёт query-param — это устарело, gateway принимает только JSON body):

```
запрос → 401?
  ├─ да → refreshPromise (singleton: параллельные 401 ждут ОДИН refresh)
  │        POST /api/user/auth/refresh  body: {"refresh": "{token}"}   ← JSON body, НЕ query-param
  │        ├─ ок → НЕМЕДЛЕННО setTokens (новую пару) → повтор запроса (1 раз)
  │        ├─ 401 "Refresh token already used" → clearTokens → logout (ретрай ЗАПРЕЩЁН — токен одноразовый)
  │        └─ другой fail → clearTokens → GatewayAuthError → logout-событие
  └─ нет → пропускаем
```

⚠️ Refresh-токен **одноразовый** (gateway атомарно блэклистит через Redis SET NX):
- новую пару сохранять в DataStore НЕМЕДЛЕННО, до любых других действий — крэш после consume, но до save = логаут
- singleton-promise критичен: два параллельных refresh одним токеном → второй получит 401 "already used" → ложный логаут
- таймаут refresh — 10с

### 3. Deep link авторизация (feature:auth)

**Манифест** (app):
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="synteehelper" android:host="auth" android:path="/callback" />
</intent-filter>
```
Схема `synteehelper` — та же, что у десктопа (gateway валидирует redirect, менять ничего не надо).

**AuthRepository**:
```kotlin
suspend fun startLogin()      // Custom Tabs: {WEB}/auth/desktop?redirect=synteehelper%3A%2F%2Fauth%2Fcallback
suspend fun exchangeCode(code: String): Tokens   // POST /api/user/auth/desktop/exchange {"code": "..."}
suspend fun getMe(): GatewayUser                 // GET /api/user/auth/me
fun setGuestMode(enabled: Boolean)
```

**Флоу в LoginScreen**:
1. Кнопка «Войти» → `startLogin()` (Custom Tabs: `{WEB}/auth/desktop?redirect=synteehelper://auth/callback` — схема валидируется по префиксу `synteehelper://`, менять gateway не нужно)
2. Consent-страница самодостаточна: форма логина (email+пароль), если не залогинен → экран «Authorize access» → кнопка Authorize → браузер редиректит на deep link. Весь флоу — внутри Custom Tab.
3. Deep link → `onNewIntent` (приложение запущено) / `onCreate` (cold start) в MainActivity → intent парсится → `code` → **НЕМЕДЛЕННО** `exchangeCode(code)` (код одноразовый, TTL 10 секунд!) → токены → `getMe()` → переход на ChatList
4. Состояние «ожидаем возврата из браузера»: спиннер + таймаут 120с + кнопка отмены
5. Ошибка кода: 401 "Invalid or expired code" (не успели за 10с / replay) → понятное сообщение + кнопка «Повторить вход» (заново открывает браузер — будет выдан новый код)
6. Кнопка «Продолжить как гость» → `setGuestMode(true)` → ChatList (провайдеры настраиваются в настройках)

**AuthViewModel** состояние:
```kotlin
data class AuthUiState(
    val isLoading: Boolean = false,
    val waitingForBrowser: Boolean = false,
    val error: String? = null,      // человекочитаемое (маппинг AppError)
    val isGuest: Boolean = false,
)
```

### 4. Gateway API — интерфейсы

```kotlin
interface GatewayApi {
    // Auth (без Bearer)
    @POST("api/user/auth/desktop/exchange") suspend fun exchangeCode(@Body body: CodeRequest): TokenResponse
    @POST("api/user/auth/refresh") suspend fun refreshToken(@Body body: RefreshRequest): TokenResponse
    // ⚠️ RefreshRequest = {"refresh": "..."} — JSON body (НЕ query-param!), токен одноразовый
    @GET("api/user/auth/me") suspend fun getMe(): GatewayUserDto
    @PATCH("api/user/auth/me") suspend fun updateProfile(@Body body: ProfilePatch): GatewayUserDto
    @POST("api/user/auth/change-password") suspend fun changePassword(@Body body: ChangePasswordRequest)

    // Models (Bearer)
    @GET("v1/models") suspend fun getModels(): ModelsResponse
    @GET("v1/model-status/buckets") suspend fun getStatusBuckets(@Query("hours") hours: Int = 12): List<StatusBucketsDto>
    @GET("v1/provider-status") suspend fun getProviderStatus(@Query("hours") hours: Int = 1): List<ProviderStatusDto>

    // Billing (Bearer) — для экрана настроек/статистики
    @GET("api/user/billing/balance") suspend fun getBalance(): BalanceDto
    @GET("api/user/billing/summary") suspend fun getSummary(@Query("hours") hours: Int): BillingSummaryDto
    @GET("api/user/billing/plan") suspend fun getPlan(): PlanDto
}
```

DTO 1:1 с контрактом (см. PLAN.md — «ИНТЕГРАЦИЯ С SYNTEE-GATEWAY»).
Json: `ignoreUnknownKeys = true` (gateway расширяет ответы).

### 5. Репозиторий моделей (core:data)

**ModelsRepository**:
```kotlin
suspend fun getModels(forceRefresh: Boolean = false): Result<List<ChatModel>>
fun observeModels(): Flow<List<ChatModel>>   // кэш в памяти + stale-при-сети-вниз (как десктоп)
fun observeStatuses(): Flow<Map<String, ModelStatus>>  // operational/degraded/down
suspend fun refreshStatuses()
```

- Только `modality == "chat"` модели в базовом селекторе
- Гость → пустой список + флаг «gateway недоступен в гостевом режиме»
- Кэш: in-memory + сериализация в DataStore (stale-данные при ошибке сети, как `{models, error}` на десктопе)
- Периодический рефреш статусов (при открытии селектора + каждые 5 мин, пока экран активен)

### 6. SSE-инфраструктура (для стадии 03)

`SseClient` на okhttp-sse:
```kotlin
fun streamChatCompletion(request: ChatCompletionRequest): Flow<SseEvent>
sealed interface SseEvent {
    data class Delta(val content: String?, val reasoning: String?, ...) : SseEvent
    data class Usage(val promptTokens: Int, val completionTokens: Int) : SseEvent
    data object Done : SseEvent
    data class Error(val throwable: Throwable) : SseEvent
}
```

`ChatSseParser` — покрыть **все форматы дельт** (из AIClient.ts десктопа):
- `delta.content` — обычный текст
- `delta.reasoning_content`: строка | `{text}` | `{thinking}` | `{content}` | массив блоков
- `delta.thinking` (Anthropic-стиль)
- теги `</think>` внутри content
- финальный чанк с `usage` + `[DONE]`

⚠️ Это самая коварная часть контракта — писать тесты на каждый формат сразу.

### 7. Тесты

- `AuthInterceptorTest`: 401 → refresh (JSON body!) → ретрай; refresh-fail → logout; параллельные 401 → ОДИН refresh; "already used" → logout без ретрая; новая пара сохраняется ДО ретрая исходного запроса (Turbine + MockWebServer)
- `ChatSseParserTest`: все 5+ форматов дельт, `[DONE]`, usage
- `TokenStoreTest`: шифрование/расшифровка, expiry-проверка JWT
- `AuthRepositoryTest`: exchange по deep link (успех за <10с, истёкший код → ошибка + повторный флоу)

---

## КРИТЕРИИ ГОТОВНОСТИ

- [ ] Логин через браузер на эмуляторе: Custom Tabs → consent (dev gateway) → возврат в приложение → токены сохранены → UI перешёл на ChatList
- [ ] Весь флоу логина проходит в Custom Tab (форма email+пароль на consent-странице, если не залогинен)
- [ ] Код истёк (не успели за 10с) → понятная ошибка + «Повторить вход» открывает браузер заново
- [ ] Токен протух (подменить exp) → любой запрос сам обновил → 200
- [ ] Refresh невалиден / "already used" → автоматический logout → LoginScreen
- [ ] Гостевой режим: вход без сети, gateway не дёргается
- [ ] Список моделей с бейджами статусов грузится и кэшируется
- [ ] Identity-заголовки видны в логах gateway (debug, client_type = syntee-helper)
- [ ] Dev: cleartext на 10.0.2.2 работает; release-конфиг запрещает HTTP
- [ ] Unit-тесты зелёные, покрытие парсера SSE ≥ 90%
