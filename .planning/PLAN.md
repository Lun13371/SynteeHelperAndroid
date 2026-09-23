# SYNTEE HELPER ANDROID — ПЛАН РАЗРАБОТКИ (БАЗА)

> Дата составления: 23 сентября 2026
> Версия плана: 1.1 (контракт проверен по коду Syntee-Gateway, прод-домен syntee.fun)
> Референс: десктоп-агент C:\Projects\SynteeHelper (Electron + React) + сам gateway C:\Projects\Syntee-Gateway

---

## ЧТО МЫ СТРОИМ

Syntee Helper Android — нативное мобильное приложение (Kotlin + Jetpack Compose),
мобильная версия персонального AI-ассистента Syntee Helper. На этом этапе строим
**БАЗУ**: чаты с моделями через Syntee-Gateway, авторизацию, настройки,
кастомизацию и гостевой режим. UI — красивый и современный (Material 3 +
фирменный стиль Syntee), производительность — приоритет с первого дня.

Дальше (за пределами базы): вложения/vision, агентные инструменты, голос,
память, уведомления-мессенджер и т.д. — база проектируется так, чтобы это
добавлялось без переделки архитектуры.

---

## СКОП БАЗЫ (зафиксировано решениями)

| Область | Решение |
|---|---|
| Чаты | Несколько чатов, стриминг ответов (SSE), выбор модели, история в Room, **Markdown-рендеринг + подсветка кода + копирование блоков** |
| Авторизация | Через браузер (Chrome Custom Tabs) → consent-страница gateway → **deep link** `synteehelper://auth/callback` → обмен кода на JWT (как на десктопе) |
| Дизайн | Гибрид: компоненты Material 3 + фирменная палитра/брендинг Syntee, кастомные детали и анимации |
| Кастомизация | Темы (тёмная/светлая/AMOLED/авто), акцентный цвет (+ Material You), фон чата, стиль пузырей сообщений, размер шрифта чата |
| Гостевой режим | Да — вход без аккаунта, свои провайдеры/API-ключи в настройках, без gateway-моделей и биллинга |
| Вложения | **НЕ в базе** (потом: фото/файлы, vision-модели) |
| UI-язык | Русский, все строки в strings.xml (готовность к локализации) |

---

## ТЕХНОЛОГИЧЕСКИЙ СТЕК

| Слой | Выбор | Комментарий |
|---|---|---|
| Язык | Kotlin 2.2+ (K2) | |
| UI | Jetpack Compose + Material 3 | BOM актуальный; Material You (dynamic color) на Android 12+ |
| Архитектура | MVVM + UDF (односторонний поток), single-activity | ViewModel + StateFlow, события через Channel |
| DI | Hilt | Стандарт для Android |
| Навигация | Navigation Compose (type-safe маршруты, kotlinx.serialization) | |
| Сеть | Retrofit + OkHttp + kotlinx.serialization | SSE через okhttp-sse (EventSource) |
| БД | Room (KSP) | чаты/сообщения/стриминг-буферы |
| Настройки | DataStore (Preferences) + kotlinx-serialization для типизированных моделей | |
| Токены | DataStore + шифрование Android Keystore (AES-GCM) | EncryptedSharedPreferences устарел |
| Markdown | multiplatform-markdown-renderer (mikepenz) + кастомные блоки кода с копированием | при проблемах — свой лёгкий рендерер |
| Изображения | Coil 3 | аватары и пр. |
| Сборка | Gradle version catalog (libs.versions.toml), KSP, R8 | |
| Тесты | JUnit5, Turbine (Flow), MockWebServer (SSE), Compose UI Test | |

**SDK:** minSdk 26 (Android 8.0, ~98% устройств), targetSdk/compileSdk 36.
**Пакет:** `ru.syntee.helper`. **Название приложения:** Syntee Helper.

---

## АРХИТЕКТУРА ПРОЕКТА

### Слои

```
UI (Compose)  →  ViewModel (StateFlow, UDF)  →  Repository  →  Data sources
                                                      ├─ Retrofit/OkHttp (gateway + свои провайдеры)
                                                      ├─ Room (чаты, сообщения)
                                                      └─ DataStore (настройки, токены, провайдеры)
```

Правила:
- UI не знает о сети/БД напрямую — только ViewModel → Repository
- Repository отдаёт `Flow` / suspend, доменные модели — без Retrofit/Room типов
- Ошибки сети → sealed `AppError` → UI-состояния (снекбары/пустые экраны)

### Модули

```
SynteeHelperAndroid/
├── .planning/                      # Планы (не в сборку)
│   ├── PLAN.md                     # Этот файл
│   ├── stage-01-foundation.md
│   ├── stage-02-gateway.md
│   ├── stage-03-chat.md
│   ├── stage-04-settings.md
│   ├── stage-05-customization.md
│   └── stage-06-polish.md
│
├── app/                            # Навигация, DI-обвязка, MainActivity
├── core/
│   ├── common/                     # Result/AppError, утилиты, дисппетчер
│   ├── designsystem/               # Тема Syntee, компоненты, анимации
│   ├── network/                    # OkHttp/Retrofit, SSE, auth-интерсепторы, gateway API
│   ├── database/                   # Room: entities, DAO, миграции
│   ├── datastore/                  # Настройки, токены, провайдеры
│   └── data/                       # Repository-реализации, доменные модели, мапперы
├── feature/
│   ├── auth/                       # Login screen, deep link, guest mode
│   ├── chat/                       # Список чатов, экран чата, стриминг, model selector
│   └── settings/                   # Все разделы настроек
└── gradle/libs.versions.toml       # Version catalog
```

### Поток данных чата (стриминг)

```mermaid
flowchart LR
    A[Инпут пользователя] --> B[ChatViewModel]
    B --> C[ChatRepository]
    C --> D[(Room: message user)]
    C --> E[OkHttp SSE POST /v1/chat/completions]
    E -->|data: delta.content| F[Парсер SSE]
    F --> G[StreamingState в VM]
    G --> H[Compose: живой текст]
    F -->|чекпоинт каждые 3с| D
    E -->|[DONE] + usage| I[(Room: message assistant)]
```

---

## ИНТЕГРАЦИЯ С SYNTEE-GATEWAY (контракт)

Зафиксировано по коду gateway (C:\Projects\Syntee-Gateway, проверено 23.09.2026). Базовые URL — через BuildConfig (dev/prod):
- **API**: `SYNTEE_API_URL` — dev: `http://10.0.2.2:8000` (эмулятор → localhost ПК); прод: `https://syntee.fun`
- **Web**: `SYNTEE_WEB_URL` — dev: `http://10.0.2.2:3000`; прод: `https://syntee.fun`
- ⚠️ Прод: API и веб на **одном домене** `syntee.fun` — nginx пользовательского фронта проксирует `/api/*` и `/v1/*` на backend:8000 (Caddy → frontend-user:80 → backend). Отдельного api-поддомена НЕТ.

### Авторизация (deep link, как на десктопе)

1. Кнопка «Войти» → Chrome Custom Tabs: `{WEB}/auth/desktop?redirect=synteehelper%3A%2F%2Fauth%2Fcallback`
   (redirect валидируется на веб-странице по префиксу `synteehelper://` — схема та же, что у десктопа, gateway менять не нужно)
2. Consent-страница самодостаточна: если юзер не залогинен — форма email+пароль прямо на ней, затем экран «Authorize access». Весь флоу проходит внутри Custom Tab.
3. Кнопка «Authorize» → веб вызывает `POST /api/user/desktop/authorize` (JWT web-сессии) → `{code, expires_in: 10}` → браузер редиректит на `synteehelper://auth/callback?code=...` → ловим intent-filter'ом
4. ⚠️ Код **одноразовый, TTL 10 секунд** — обмен начинать немедленно при получении intent. `POST /api/user/auth/desktop/exchange` body `{"code": "..."}` (без авторизации) → `{access_token, refresh_token, token_type: "bearer"}`
5. Токены → DataStore (шифрование Keystore AES-GCM). JWT: access **7 дней**, refresh **7 дней**
6. Refresh: `POST /api/user/auth/refresh` body `{"refresh": "{token}"}` — **JSON body, НЕ query-param!** ⚠️ Refresh-токен **одноразовый**: при использовании атомарно блэклистится (Redis SET NX), гонка → 401 "Refresh token already used". Поэтому: singleton-promise обязателен (параллельные 401 → ОДИН refresh), новую пару сохранять в DataStore НЕМЕДЛЕННО до любых других запросов, "already used" → logout без ретрая.
7. Авто-refresh на 401 во всех запросах.
8. Фолбэк если deep link не сработал: повторить весь браузерный флоу (новый код). Ручной ввод кода НЕВОЗМОЖЕН — 10с TTL.

### Identity-заголовки (обязательны для статистики gateway)

```
User-Agent: syntee-agent-android/{version}
X-Client-Name: syntee-agent-android
X-Client-Version: {version}
```
Опция «анонимный UA» (как на десктопе) убирает их.

Гейтвей определяет тип клиента по подстроке `syntee` в User-Agent → запросы в логах помечаются как `syntee-helper`. Белого списка клиентов нет — заголовки информационные. (Опционально: добавить в gateway паттерн `syntee-agent-android` → отдельный client_type `syntee-helper-android` до общего `syntee`-паттерна, чтобы статистика Android-трафика была раздельной.)

### Эндпоинты

| Метод | Путь | Назначение |
|---|---|---|
| POST | `/api/user/auth/desktop/exchange` | код → JWT (код одноразовый, TTL 10с) |
| POST | `/api/user/auth/refresh` | refresh JWT — body `{"refresh": "..."}`, токен одноразовый |
| GET/PATCH | `/api/user/auth/me` | профиль |
| POST | `/api/user/auth/change-password` | смена пароля |
| GET | `/api/user/apikeys` (+POST, DELETE /{id}) | API-ключи |
| GET | `/v1/models` | каталог моделей (Bearer JWT) |
| GET | `/v1/model-status/buckets?hours=12` | uptime по моделям |
| GET | `/v1/provider-status?hours=1` | статусы провайдеров |
| POST | `/v1/chat/completions` | чат (SSE, OpenAI-формат) |
| GET | `/api/user/billing/balance, /summary, /plan, /usage/*` | биллинг |

### Chat completions (SSE)

Запрос: `POST {API}/v1/chat/completions`, Bearer JWT:
```json
{
  "model": "...",
  "messages": [{"role": "user", "content": "..."}],
  "stream": true,
  "stream_options": {"include_usage": true},
  "temperature": 0.7,
  "max_tokens": 16384,
  "reasoning_effort": "medium"
}
```
- Дефолты десктопа: temperature 0.7, max_tokens 16384
- `reasoning_effort` — только если модель поддерживает (levels из `/v1/models`), 'off' → не шлём
- Стрим: стандартный OpenAI SSE (`data: {...}`, `[DONE]`)
- Дельты: `delta.content` (текст), `delta.reasoning_content` (**3 формата**: строка \| `{text}` \| `{thinking}` \| `{content}` \| массив блоков), `delta.thinking` (Anthropic), теги `<think>...</think>` внутри content — **поддержать все**
- Usage в финальном чанке: `{prompt_tokens, completion_tokens, total_tokens}`
- Ретраи: 429/5xx/сеть — backoff `1s·2^n + jitter`, максимум 6 попыток; 401 → форс-рефреш + 1 ретрай

### GatewayModel (из /v1/models)

`{id, object, created, owned_by, prefix, display_name, supports_streaming, supports_tools, supports_vision, max_tokens, reasoning: {supported, levels[]}, pricing: {input_price, output_price, unit} (за 1k токенов — UI умножает ×1000), modality, description, providers[], status: operational|degraded|down|available|unavailable}`

### Гостевой режим / свои провайдеры

Гость = локальный флаг (gateway не дёргается). Провайдер (как на десктопе):
`{id, type, name, baseUrl, apiKey, apiKeys[], enabled, isCustom, modelsOverride[], modelsEndpoint}`
Типы для базы: `openai` (совместимые), `anthropic`, `custom`, `ollama`, `syntee-gateway`.

---

## КОНВЕНЦИИ

- **Kotlin**: официальный style guide, именование `ChatRepository`, `ChatViewModel`, `ChatScreen`
- **Compose**: state hoisting, `Immutable`/`Stable` модели для списков, `key()` в LazyColumn, без бизнес-логики в composable
- **Потоки**: `StateFlow` для состояния, `Channel/SharedFlow` для one-shot событий
- **Ошибки**: sealed `AppError { Network, Auth, RateLimit, Server(message), Unknown }` — маппинг в UI-строки
- **Ресурсы**: ВСЕ строки в `strings.xml`; цвета темы — в designsystem, не хардкод
- **Секреты**: никогда в логах; refresh-токен не логировать (одноразовый — утечка = логаут)
- **Версии**: только через `libs.versions.toml`
- **Ветки**: `dev` — разработка (пушить туда), `master`/`prod` — релизы

---

## СТАДИИ

| # | Файл | Цель | Результат |
|---|---|---|---|
| 01 | stage-01-foundation.md | Скелет проекта | Собираемое приложение: модули, DI, навигация, тема Syntee, пустые экраны |
| 02 | stage-02-gateway.md | Сеть и авторизация | Вход через deep link, JWT с авто-refresh, каталог моделей со статусами, гость |
| 03 | stage-03-chat.md | Ядро чата | Чаты с историей в Room, стриминг SSE, Markdown+код, выбор модели |
| 04 | stage-04-settings.md | Настройки | Профиль, параметры ИИ, провайдеры (гость), данные, о приложении |
| 05 | stage-05-customization.md | Кастомизация | Темы/акцент/фон/пузыри/шрифт — с живым превью, всё персистится |
| 06 | stage-06-polish.md | Полировка | Оптимизация (baseline profiles, R8), анимации, тесты, релизная сборка |

---

## ВЕХИ ГОТОВНОСТИ БАЗЫ

1. **M1 (после 01–02)**: приложение запускается, логин через gateway работает, список моделей с uptime виден
2. **M2 (после 03)**: полноценный чат со стримингом и историей — «ежедневный драйв»
3. **M3 (после 04–05)**: настройки + кастомизация — база закончена
4. **M4 (после 06)**: оптимизировано, протестировано, релизная сборка (APK/AAB)

---

## РИСКИ И ЗАМЕТКИ

- **Deep link на Android**: Chrome может не отдавать intent из Custom Tabs при редиректе на кастомный scheme — тестируем сразу; фолбэк — повтор всего браузерного флоу (ручной ввод кода невозможен: код живёт 10 секунд)
- **Одноразовый refresh-токен**: крэш между consume старого и сохранением нового токена = логаут. Новую пару сохранять немедленно; "already used" обрабатывать как logout, без ретрая
- **⚠️ Десктоп не в курсе нового контракта**: SynteeHelper (десктоп) до сих пор шлёт refresh в query-param (`?refresh=`), а gateway принимает ТОЛЬКО JSON body `{"refresh": ...}` — рефреш на десктопе сломан (после 7 дней сессия умрёт с логаутом). Поправить gatewayClient.ts десктопа: body вместо query
- **Cleartext в dev**: `10.0.2.2` — HTTP; в debug — network security config с cleartext для dev-хостов, в release — только HTTPS
- **3 формата reasoning-дельт**: парсер SSE обязан покрыть все (взять логику из десктопа AIClient.ts)
- **localhost на устройстве**: эмулятор — `10.0.2.2`, реальное устройство — LAN IP ПК (или prod URL); URL настраивается в BuildConfig + dev-переключатель в настройках
- **Pricing за 1k токенов**: в UI показываем за 1M (×1000), как на десктопе
- **Compose + SSE**: долгий стриминг должен переживать поворот экрана/свёртывание — стрим живёт в ViewModel (Retained), Room — источник истины
- **HTTP/2/3 у Caddy**: prod работает по h2/h3 + HTTPS (Let's Encrypt) — OkHttp ок (h2), HTTP/3 не нужен
