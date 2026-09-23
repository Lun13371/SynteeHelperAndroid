package ru.syntee.helper.core.data.models

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.syntee.helper.core.data.model.ChatModel
import ru.syntee.helper.core.data.model.ModelStatus
import ru.syntee.helper.core.data.model.ModelsState
import ru.syntee.helper.core.datastore.SettingsPreferences
import ru.syntee.helper.core.network.api.GatewayApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Каталог моделей gateway: /v1/models + кэш (stale-при-сети-вниз, как десктоп).
 *
 * В базе — только chat-модели (modality == "chat" или null).
 */
@Singleton
class ModelsRepository @Inject constructor(
    private val gatewayApi: GatewayApi,
    private val settings: SettingsPreferences,
    private val json: Json,
) {

    private val _state = MutableStateFlow<ModelsState>(ModelsState.Loading)
    val state: StateFlow<ModelsState> = _state.asStateFlow()

    private val mutex = Mutex()

    init {
        // Стартуем с кэша — мгновенный показ, сеть догрузит свежее.
        _state.value = ModelsState.Loading
    }

    /**
     * Загрузка каталога. [force] — игнорировать свежесть.
     * При ошибке сети отдаём кэш (stale) с пометкой ошибки.
     */
    suspend fun refresh(force: Boolean = false) = mutex.withLock {
        val cached = readCache()
        _state.value = ModelsState.Loading
        try {
            val response = gatewayApi.getModels()
            val models = response.data
                .filter { it.modalityIsChat() }
                .map { it.toDomain() }
            writeCache(models)
            _state.value = ModelsState.Loaded(models)
        } catch (t: Throwable) {
            _state.value = ModelsState.Error(
                message = t.message ?: "Не удалось загрузить модели",
                cached = cached,
            )
        }
    }

    /** Кэш из DataStore (может быть пустым). */
    suspend fun readCache(): List<ChatModel> {
        val raw = settings.current().modelsCacheJson ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ChatModel.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private suspend fun writeCache(models: List<ChatModel>) {
        runCatching {
            settings.setModelsCacheJson(json.encodeToString(ListSerializer(ChatModel.serializer()), models))
        }
    }

    private fun ru.syntee.helper.core.network.dto.GatewayModelDto.modalityIsChat(): Boolean {
        // В базе интересуют только чат-модели; modality может отсутствовать.
        return modality == null || modality == "chat"
    }

    private fun ru.syntee.helper.core.network.dto.GatewayModelDto.toDomain() = ChatModel(
        id = id,
        displayName = display_name ?: id,
        ownedBy = owned_by,
        supportsStreaming = supports_streaming ?: true,
        supportsTools = supports_tools ?: false,
        supportsVision = supports_vision ?: false,
        reasoningSupported = reasoning?.supported ?: false,
        reasoningLevels = reasoning?.levels ?: emptyList(),
        maxTokens = max_tokens,
        description = description,
        inputPricePer1k = pricing?.input_price,
        outputPricePer1k = pricing?.output_price,
        status = ModelStatus.from(status),
    )
}
