# СТАДИЯ 03 — ЯДРО ЧАТА

> Цель: полноценный чат. После этой стадии приложение можно использовать
> ежедневно: несколько чатов с историей, стриминг ответов в реальном времени,
> Markdown с блоками кода и копированием, выбор модели, пин/архив чатов,
> восстановление после краша во время стриминга.

---

## ЧТО БУДЕТ ГОТОВО ПОСЛЕ СТАДИИ

- Список чатов: создание, пин, архив, удаление (свайпы), переименование, поиск по заголовкам
- Экран чата: история сообщений, стриминг «живого» ответа, автоскролл, «стоп-генерация»
- Отправка сообщения → SSE-стрим → чекпоинты в Room каждые 3с → финализация с usage
- Markdown-рендеринг ответов: заголовки, списки, таблицы, **блоки кода с подсветкой и кнопкой копирования**, инлайн-код
- Reasoning-панель: сворачиваемый блок «размышления» (thinking) с таймингом
- Селектор модели: bottom sheet — поиск, статусы (operational/degraded/down), закрепление, недавние
- Авто-заголовок чата из первого сообщения (первые ~40 символов, как на десктопе)
- Крэш-рекавери: недописанный стриминговый ответ восстанавливается из буфера
- Стриминг переживает поворот экрана и свёртывание приложения

---

## ТЕХНОЛОГИИ ЭТОЙ СТАДИИ

```
Room 2.7 (KSP), kotlinx-serialization для JSON-полей
okhttp-sse (из стадии 02)
multiplatform-markdown-renderer (mikepenz) + кастомный CodeBlock
```

---

## ЗАДАЧИ

### 1. Схема Room (core:database)

По схеме десктопа (initialSchema.ts), упрощено до базы:

```kotlin
@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,              // UUID
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val color: String? = null,               // кастомный цвет чата (стадия 05)
    val modelId: String? = null,             // последняя модель чата
    val modelProvider: String? = null,
    val reasoningValue: String? = null,      // reasoning_effort последнего запроса
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(ChatEntity, parentColumns=["id"], childColumns=["chatId"], onDelete = CASCADE)],
    indices = [Index("chatId"), Index("createdAt"), Index(value = ["chatId", "createdAt", "id"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,              // UUID
    val chatId: String,
    val role: String,                        // user | assistant | system
    val content: String,
    val createdAt: Long,
    val model: String? = null,
    val thinking: String? = null,            // reasoning-текст
    val thinkingMs: Int? = null,
    val durationMs: Int? = null,
    val streamingMs: Int? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val finishReason: String? = null,        // stop | length | error | cancelled
    val replyTo: String? = null,
)

@Entity(tableName = "streaming_buffers")
data class StreamingBufferEntity(
    @PrimaryKey val messageId: String,
    val chatId: String,
    val payload: String,                     // JSON полного MessageEntity
    val updatedAt: Long,
)
```

DAO: `ChatDao` (список с сортировкой `isPinned DESC, updatedAt DESC`, поиск по
LIKE, архив-фильтр), `MessageDao` (пагинация по курсору `chatId+createdAt DESC`,
`LIMIT 50`), `StreamingBufferDao` (upsert, get by chat, delete).

Миграции: с первого дня `exportSchema = true`, схема в VCS.

### 2. ChatRepository (core:data)

```kotlin
class ChatRepository @Inject constructor(...) {
    fun observeChats(includeArchived: Boolean): Flow<List<Chat>>
    fun observeMessages(chatId: String): Flow<List<ChatMessage>>
    suspend fun createChat(): Chat
    suspend fun renameChat(id: String, title: String)
    suspend fun setPinned(id: String, pinned: Boolean) / setArchived(...)
    suspend fun deleteChat(id: String)

    suspend fun sendMessage(chatId: String, text: String, model: ChatModel): Flow<StreamingUpdate>
    suspend fun cancelGeneration(chatId: String)
    fun recoverStreaming(): Flow<ChatMessage>   // при старте: буферы → финализация
}
```

**Пайплайн sendMessage** (ядро стадии):

```
1. Записать MessageEntity(user) в Room → UI мгновенно показывает
2. Создать MessageEntity(assistant, пустое) + стриминг-флаг в VM
3. POST /v1/chat/completions (stream: true, stream_options.include_usage)
   - messages = последние N сообщений чата (лимит контекста по max_tokens модели)
   - temperature/max_tokens из настроек; reasoning_effort если модель поддерживает
4. Flow<SseEvent> → аккумулировать content/thinking в StreamingState (VM)
5. Каждые 3с — чекпоинт: streaming_buffers.upsert(payload)
6. [DONE] + usage → финальный MessageEntity в Room + удалить буфер
7. Ошибка/отмена → финализация с finishReason = error|cancelled
8. Ретраи (из стадии 02): 429/5xx — backoff; 401 — форс-рефреш + 1 ретрай
```

Стрим живёт в **ChatViewModel** (SurviveConfigurationChange): OkHttp call
отменяется в `onCleared()` только если VM реально умирает. Room — источник
истины для UI: экран читает `observeMessages()`, стриминговое сообщение
подмешивается из состояния VM.

### 3. Список чатов (feature:chat)

- `LazyColumn` + `key(chat.id)`, стабильные модели (`@Immutable`)
- Свайп-действия: пин/архив (влево), удаление с undo-снекбаром (вправо)
- Долгое нажатие → контекстное меню: переименовать, пин, архив, удалить
- Поиск по заголовкам (топбар разворачивается в поиск)
- Пустое состояние: иллюстрация + «Начните первый чат»
- FAB «+» → новый чат (или первый в списке, как в мессенджерах)

### 4. Экран чата (feature:chat)

**Структура:**
```
ChatScreen
├── TopBar: назад, название (клик → переименование), селектор модели (клик)
├── LazyColumn (reverseLayout = false, автоскролл вниз)
│   ├── MessageBubble(user)   — свой стиль (кастомизация в стадии 05)
│   ├── MessageBubble(assistant) — Markdown + reasoning-блок + footer (модель, время, токены)
│   └── StreamingIndicator    — курсор-«пульс» на конце стримящегося текста
└── InputBar: TextField (multiline, до 5 строк), кнопка отправки/стоп
```

- **Автоскролл**: если пользователь внизу — едем за стримом; если проскроллил вверх — не дёргаем, показываем chip «↓ Вниз»
- **Стоп-генерация**: кнопка превращается в «■» во время стрима → `cancelGeneration()`
- **Копирование**: долгий тап по пузырю → копировать всё / выделенное
- **Reasoning-блок**: сворачиваемый («💭 Размышления · 4.2с»), по умолчанию свёрнут
- Footer ассистента: имя модели + токены (prompt/completion) + длительность
- Повтор последнего сообщения при ошибке (кнопка «Повторить» на упавшем сообщении)

### 5. Markdown-рендеринг

Основа: `multiplatform-markdown-renderer-m3` (mikepenz) с кастомизацией:
- Кастомный `CodeBlock`: тёмная плашка, язык в заголовке, **кнопка «копировать»**, моно-шрифт (JetBrains Mono), горизонтальный скролл
- Подсветка синтаксиса: лёгкий regex/tokenizer-хайлайтер (Kotlin/Kotlin DSL/JSON/Python/JS/TS/HTML/CSS/SQL/Bash) — без тяжёлых зависимостей; если качество не устроит — Prism4j
- Инлайн-код, таблицы, списки, заголовки, цитаты, ссылки (открывать в Custom Tabs)
- Рендерить **инкрементально**: во время стриминга — упрощённый рендер (плоский текст + код-блоки), полный Markdown — после финализации (перфоманс)

### 6. Селектор модели (bottom sheet)

- Поиск по id/display_name
- Секции: «Закреплённые», «Недавние» (DataStore, последние 5), «Все»
- Бейдж статуса: 🟢 operational / 🟡 degraded / 🔴 down (из ModelsRepository)
- В карточке модели: display_name, id, контекст (max_tokens), pricing за 1M (×1000 из pricing за 1k!), иконки capabilities (streaming/tools/vision/reasoning)
- Выбор модели: сохраняется в чат (modelId) + в «недавние»
- Гость: список из его провайдеров (провайдеры — стадия 04; до этого — пусто)

### 7. Крэш-рекавери

При открытии приложения: `recoverStreaming()` — все записи streaming_buffers
финализируются в messages с `finishReason = "interrupted"`, буферы чистятся.
Пользователь видит недописанный ответ + может «Повторить».

### 8. Тесты

- `ChatRepositoryTest`: пайплайн sendMessage с MockWebServer (SSE-поток), отмена, чекпоинты
- `ChatViewModelTest` (Turbine): состояния стриминга, автоскролл-флаги
- Compose UI-тест: список чатов, отправка (фейковый репозиторий)

---

## КРИТЕРИИ ГОТОВНОСТИ

- [ ] Создать чат → отправить сообщение → ответ застримился → история сохранена (перезапуск приложения — всё на месте)
- [ ] Стриминг не рвётся при повороте экрана и свёртывании приложения
- [ ] Стоп-генерация работает мгновенно, недописанный ответ сохраняется
- [ ] Kill приложения во время стрима → ответ восстановлен из буфера
- [ ] Markdown: код с подсветкой и копированием, таблицы, списки — рендерятся
- [ ] Reasoning-блок показывается для reasoning-моделей
- [ ] Селектор моделей: поиск, статусы, закрепление, недавние
- [ ] Пин/архив/удаление/переименование чатов работают, удаление с undo
- [ ] Плавность: стриминг без лагов на среднебюджетном устройстве (профилирование)
