# СТАДИЯ 04 — НАСТРОЙКИ

> Цель: все базовые настройки. После этой стадии: профиль пользователя,
> параметры ИИ, управление провайдерами для гостевого режима, управление
> данными, экран «О приложении» + dev-переключатель gateway URL.

---

## ЧТО БУДЕТ ГОТОВО ПОСЛЕ СТАДИИ

- Экран настроек с секциями (навигация внутри, без нижнего бара)
- **Аккаунт**: аватар/имя/username, редактирование профиля, выход из аккаунта, смена пароля
- **Внешний вид**: вход в кастомизацию (реализация — стадия 05)
- **ИИ и модели**: модель по умолчанию, temperature, max_tokens, reasoning effort по умолчанию, «анонимный UA», статус моделей (uptime)
- **Биллинг**: баланс, лимиты плана, статистика за 24ч (кратко)
- **Провайдеры** (гость): список/добавление/редактирование/удаление своих провайдеров
- **Данные**: экспорт чатов (JSON), очистка истории, кэш моделей
- **О приложении**: версия, gateway URL (dev-режим — смена на LAN IP), ссылки

---

## ЗАДАЧИ

### 1. Каркас настроек (feature:settings)

```
SettingsScreen (список секций)
├── Аккаунт          → AccountSection
├── Внешний вид      → AppearanceSection   (стадия 05 — пока заглушка-ссылка)
├── ИИ и модели      → AiSection
├── Провайдеры       → ProvidersSection    (виден только гостю; в аккаунте — скрыт)
├── Данные           → DataSection
└── О приложении     → AboutSection
```

Каждая секция — отдельный маршрут (type-safe), top bar с «← Назад».
Единый паттерн: `SettingsItem(title, subtitle, icon)` → клик/switch/slider.

### 2. Аккаунт (AccountSection)

- Карточка профиля: аватар (Coil), display_name, @username, email, роль
- `GET/PATCH /api/user/auth/me` — редактирование display_name (+ username с
  проверкой `GET /api/user/auth/check-username?username=` — доступность live)
- Смена пароля: `POST /api/user/auth/change-password` (два поля + подтверждение)
- **Выход**: clearTokens + guest=false → LoginScreen
- Гость: вместо профиля — карточка «Вы в гостевом режиме» + кнопки «Войти» / «Настроить провайдеров»
- Аватар: загрузка фото → сжатие → PATCH avatar_url (если gateway принимает URL; иначе — только просмотр)

### 3. ИИ и модели (AiSection)

DataStore-модель (типизированная, kotlinx-serialization):

```kotlin
@Serializable
data class AiSettings(
    val defaultModelId: String? = null,
    val temperature: Double = 0.7,        // дефолт как на десктопе
    val maxTokens: Int = 16384,           // дефолт как на десктопе
    val reasoningEffort: String = "off",  // off|minimal|low|medium|high (levels из /v1/models)
    val anonymousUserAgent: Boolean = false,
)
```

- Слайдер temperature (0–2, шаг 0.1) с подписью и пресетами («Точный 0.2 / Сбалансированный 0.7 / Креативный 1.2»)
- max_tokens: поле + быстрые пресеты (4k/8k/16k/32k)
- reasoning: dropdown уровней (валидные — из capabilities выбранной модели)
- «Анонимный UA»: switch — убирает identity-заголовки (Privacy)
- **Статус моделей**: подсекция — список моделей с бейджами + mini-uptime bars
  (buckets из `/v1/model-status/buckets?hours=12`), обновление pull-to-refresh

### 4. Биллинг (подсекция Аккаунта)

- Карточка: **баланс** (`/api/user/billing/balance` → balance/held) + план
  (`/api/user/billing/plan` → name, лимиты, usage: used/limit/reset_at по 5ч/недельно/месячно)
- Прогресс-бары лимитов с подписями «сброс через …»
- Статистика за 24ч: запросы/токены/стоимость (`/summary?hours=24`)
- Кнопка «Пополнить» → Custom Tabs на `{WEB}/billing` (топап в web, как на десктопе)
- Гость: секция скрыта

### 5. Провайдеры — гостевой режим (ProvidersSection)

Структура как на десктопе (provider.ts), в DataStore:

```kotlin
@Serializable
data class Provider(
    val id: String,               // slug
    val type: ProviderType,       // OPENAI, ANTHROPIC, CUSTOM, OLLAMA
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val enabled: Boolean = true,
    val isCustom: Boolean = true,
    val modelsOverride: List<String> = emptyList(),  // ручной список моделей
)
```

- Список провайдеров: карточки (имя, тип, URL, кол-во моделей, вкл/выкл)
- Добавление/редактирование: форма (тип → поля; для OLLAMA baseUrl `http://10.0.2.2:11434`)
- Кнопка «Проверить»: GET `{baseUrl}/models` (или modelsEndpoint) → список моделей →
  выбор вручную (modelsOverride) — как customModels на десктопе
- Ключи хранить зашифрованно (Keystore, как токены)
- В селекторе моделей (стадия 03) модели провайдеров гостя появляются по `enabled`
- Отправка сообщений: OPENAI/CUSTOM/OLLAMA → OpenAI-совместимый SSE (тот же пайплайн,
  другой baseUrl + `Authorization: Bearer {apiKey}` без JWT-флоу);
  ANTHROPIC → нативный формат (x-api-key, anthropic-version, другой SSE-формат) —
  реализовать после OpenAI-совместимых, если срочно нужно

### 6. Данные (DataSection)

- Экспорт всех чатов: JSON-файл (структура: chats[] + messages[]) → SAF (SaveFileDialog)
- Очистка: «Удалить все чаты» (double-confirm), «Удалить кэш моделей»
- Статистика локально: кол-во чатов/сообщений/токенов (агрегация Room)

### 7. О приложении (AboutSection)

- Версия приложения, версия gateway, ссылка на web
- **Dev-настройка URL** (видна только в debug-сборке): поля API/Web URL →
  переопределяют BuildConfig в рантайме (для реального устройства: LAN IP ПК)
- Логи: кнопка «Скопировать диагностику» (версии, URL, последний AppError — без токенов!)

---

## КРИТЕРИИ ГОТОВНОСТИ

- [ ] Профиль грузится и редактируется, username проверяется на лету
- [ ] Выход из аккаунта → LoginScreen, токены стёрты
- [ ] Настройки ИИ применяются к следующему запросу (temperature/max_tokens/reasoning видны в теле запроса — проверить в логах)
- [ ] «Анонимный UA» реально убирает заголовки
- [ ] Статус моделей с uptime обновляется
- [ ] Гость: провайдер добавлен → его модели в селекторе → сообщение отправляется через его API
- [ ] Экспорт чатов в файл работает, «удалить все» — с подтверждением
- [ ] Dev-смена URL работает без пересборки (debug)
