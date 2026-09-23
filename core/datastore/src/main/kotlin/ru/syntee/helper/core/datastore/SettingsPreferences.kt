package ru.syntee.helper.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "syntee_settings")

@Serializable
data class AppSettings(
    val isGuest: Boolean = false,
    val defaultModelId: String? = null,
    val temperature: Double = 0.7,
    val maxTokens: Int = 16384,
    val apiBaseUrlOverride: String? = null,
    val webBaseUrlOverride: String? = null,
    val recentModelIds: List<String> = emptyList(),
    /** Кэш профиля (JSON GatewayUser) — мгновенный показ при старте. */
    val userJson: String? = null,
    /** Кэш каталога моделей (JSON) — stale-при-сети-вниз, как на десктопе. */
    val modelsCacheJson: String? = null,
)

/**
 * Настройки приложения (DataStore): гостевой режим, параметры ИИ,
 * dev-override URL гейтвея, недавние модели, кэши.
 */
@Singleton
class SettingsPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {

    private object Keys {
        val IS_GUEST = booleanPreferencesKey("is_guest")
        val DEFAULT_MODEL = stringPreferencesKey("default_model")
        val TEMPERATURE = doublePreferencesKey("temperature")
        val MAX_TOKENS = stringPreferencesKey("max_tokens_int")
        val API_URL_OVERRIDE = stringPreferencesKey("api_url_override")
        val WEB_URL_OVERRIDE = stringPreferencesKey("web_url_override")
        val RECENT_MODELS = stringSetPreferencesKey("recent_models")
        val USER_JSON = stringPreferencesKey("user_json")
        val MODELS_CACHE = stringPreferencesKey("models_cache_json")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            isGuest = prefs[Keys.IS_GUEST] ?: false,
            defaultModelId = prefs[Keys.DEFAULT_MODEL],
            temperature = prefs[Keys.TEMPERATURE] ?: 0.7,
            maxTokens = prefs[Keys.MAX_TOKENS]?.toIntOrNull() ?: 16384,
            apiBaseUrlOverride = prefs[Keys.API_URL_OVERRIDE]?.takeIf { it.isNotBlank() },
            webBaseUrlOverride = prefs[Keys.WEB_URL_OVERRIDE]?.takeIf { it.isNotBlank() },
            recentModelIds = (prefs[Keys.RECENT_MODELS] ?: emptySet()).toList(),
            userJson = prefs[Keys.USER_JSON],
            modelsCacheJson = prefs[Keys.MODELS_CACHE],
        )
    }

    // Все мутаторы объявлены с явным Unit-возвратом: edit{} возвращает
    // Preferences, который не должен протекать в публичный API модуля.

    suspend fun setGuestMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.IS_GUEST] = enabled }
    }

    suspend fun setDefaultModel(modelId: String?) {
        context.settingsDataStore.edit {
            if (modelId == null) it.remove(Keys.DEFAULT_MODEL) else it[Keys.DEFAULT_MODEL] = modelId
        }
    }

    suspend fun setTemperature(value: Double) {
        context.settingsDataStore.edit { it[Keys.TEMPERATURE] = value }
    }

    suspend fun setMaxTokens(value: Int) {
        context.settingsDataStore.edit { it[Keys.MAX_TOKENS] = value.toString() }
    }

    suspend fun setUrlOverrides(api: String?, web: String?) {
        context.settingsDataStore.edit {
            if (api.isNullOrBlank()) it.remove(Keys.API_URL_OVERRIDE) else it[Keys.API_URL_OVERRIDE] = api.trim()
            if (web.isNullOrBlank()) it.remove(Keys.WEB_URL_OVERRIDE) else it[Keys.WEB_URL_OVERRIDE] = web.trim()
        }
    }

    suspend fun pushRecentModel(modelId: String) {
        context.settingsDataStore.edit {
            val current = it[Keys.RECENT_MODELS] ?: emptySet()
            it[Keys.RECENT_MODELS] = (current - modelId + modelId)
                .toList()
                .takeLast(MAX_RECENT_MODELS)
                .toSet()
        }
    }

    suspend fun setUserJson(value: String?) {
        context.settingsDataStore.edit {
            if (value == null) it.remove(Keys.USER_JSON) else it[Keys.USER_JSON] = value
        }
    }

    suspend fun setModelsCacheJson(value: String?) {
        context.settingsDataStore.edit {
            if (value == null) it.remove(Keys.MODELS_CACHE) else it[Keys.MODELS_CACHE] = value
        }
    }

    /** Быстрый доступ к текущим настройкам без подписки на Flow. */
    suspend fun current(): AppSettings = settingsFlow.first()

    private companion object {
        const val MAX_RECENT_MODELS = 5
    }
}
