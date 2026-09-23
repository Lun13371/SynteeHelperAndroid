package ru.syntee.helper.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.syntee.helper.core.data.auth.AuthState
import ru.syntee.helper.core.data.model.ChatModel
import ru.syntee.helper.core.data.model.ModelsState
import ru.syntee.helper.core.designsystem.components.SynteeCard
import ru.syntee.helper.core.designsystem.components.SynteeGhostButton
import ru.syntee.helper.core.designsystem.components.SynteePrimaryButton

/**
 * Настройки: корень (секции) + подэкраны (Аккаунт, ИИ и модели, О приложении).
 * Стадия 04: Провайдеры гостя, Данные, Внешний вид (кастомизация — стадия 05).
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val section by viewModel.section.collectAsStateWithLifecycle()

    BackHandler(enabled = section != SettingsSection.ROOT) {
        viewModel.back()
    }

    when (section) {
        SettingsSection.ROOT -> SettingsRoot(viewModel, modifier)
        SettingsSection.ACCOUNT -> AccountSection(viewModel, modifier)
        SettingsSection.AI -> AiSection(viewModel, modifier)
        SettingsSection.ABOUT -> AboutSection(viewModel, modifier)
    }
}

// ── Корень ─────────────────────────────────────────────────────────────────

@Composable
private fun SettingsRoot(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        )

        SettingsSectionItem(
            icon = Icons.Outlined.Person,
            title = stringResource(R.string.settings_section_account),
            subtitle = when (authState) {
                is AuthState.LoggedIn -> (authState as AuthState.LoggedIn).user?.email
                    ?: stringResource(R.string.settings_section_account_sub)
                is AuthState.Guest -> stringResource(R.string.settings_guest_mode)
                else -> stringResource(R.string.settings_section_account_sub)
            },
            onClick = { viewModel.open(SettingsSection.ACCOUNT) },
        )
        SettingsSectionItem(
            icon = Icons.Outlined.Palette,
            title = stringResource(R.string.settings_section_appearance),
            subtitle = stringResource(R.string.settings_soon),
            enabled = false,
        )
        SettingsSectionItem(
            icon = Icons.Outlined.AutoAwesome,
            title = stringResource(R.string.settings_section_ai),
            subtitle = stringResource(R.string.settings_section_ai_sub),
            onClick = { viewModel.open(SettingsSection.AI) },
        )
        SettingsSectionItem(
            icon = Icons.Outlined.Dns,
            title = stringResource(R.string.settings_section_providers),
            subtitle = stringResource(R.string.settings_soon),
            enabled = false,
        )
        SettingsSectionItem(
            icon = Icons.Outlined.Folder,
            title = stringResource(R.string.settings_section_data),
            subtitle = stringResource(R.string.settings_soon),
            enabled = false,
        )
        SettingsSectionItem(
            icon = Icons.Outlined.Info,
            title = stringResource(R.string.settings_section_about),
            subtitle = viewModel.appInfo.versionName,
            onClick = { viewModel.open(SettingsSection.ABOUT) },
        )
    }
}

@Composable
private fun SettingsSectionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

// ── Подэкран ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        TopAppBar(
            title = { Text(title, style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Аккаунт ────────────────────────────────────────────────────────────────

@Composable
private fun AccountSection(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    SubScreen(
        title = stringResource(R.string.settings_section_account),
        onBack = viewModel::back,
        modifier = modifier,
    ) {
        when (authState) {
            is AuthState.LoggedIn -> {
                val user = (authState as AuthState.LoggedIn).user
                SynteeCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = (user?.displayName ?: user?.email ?: "?")
                                    .take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = user?.displayName ?: user?.email ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            user?.username?.let {
                                Text(
                                    text = "@$it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = user?.email ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                SynteeGhostButton(
                    text = stringResource(R.string.settings_logout),
                    onClick = viewModel::logout,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    },
                )
            }
            is AuthState.Guest -> {
                SynteeCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.settings_guest_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_guest_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                SynteePrimaryButton(
                    text = stringResource(R.string.settings_login),
                    onClick = { /* навигация на логин — через logout из гостя */ viewModel.logout() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }
    }
}

// ── ИИ и модели ────────────────────────────────────────────────────────────

@Composable
private fun AiSection(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val modelsState by viewModel.modelsState.collectAsStateWithLifecycle()
    val defaultModel by viewModel.defaultModel.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()

    SubScreen(
        title = stringResource(R.string.settings_section_ai),
        onBack = viewModel::back,
        modifier = modifier,
    ) {
        // Модель по умолчанию
        Text(
            text = stringResource(R.string.settings_default_model),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(10.dp))
        when (modelsState) {
            is ModelsState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            is ModelsState.Error -> Text(
                text = (modelsState as ModelsState.Error).message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            is ModelsState.Loaded -> {
                val models = (modelsState as ModelsState.Loaded).models
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(models, key = { it.id }) { model ->
                        ModelRadioRow(
                            model = model,
                            isSelected = model.id == defaultModel?.id,
                            onClick = { viewModel.setDefaultModel(model) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Temperature
        Text(
            text = stringResource(R.string.settings_temperature),
            style = MaterialTheme.typography.titleMedium,
        )
        val tempLabel = when {
            appSettings.temperature < 0.5 -> stringResource(R.string.settings_temp_precise)
            appSettings.temperature < 1.0 -> stringResource(R.string.settings_temp_balanced)
            else -> stringResource(R.string.settings_temp_creative)
        }
        Text(
            text = stringResource(
                R.string.settings_temperature_value,
                appSettings.temperature,
                tempLabel,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = appSettings.temperature.toFloat(),
            onValueChange = { viewModel.setTemperature(it.toDouble()) },
            valueRange = 0f..2f,
            steps = 19,
        )
    }
}

@Composable
private fun ModelRadioRow(
    model: ChatModel,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Column {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = model.id,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── О приложении ───────────────────────────────────────────────────────────

@Composable
private fun AboutSection(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    var apiOverride by remember { mutableStateOf("") }
    var webOverride by remember { mutableStateOf("") }
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()

    SubScreen(
        title = stringResource(R.string.settings_section_about),
        onBack = viewModel::back,
        modifier = modifier,
    ) {
        SynteeCard(modifier = Modifier.fillMaxWidth()) {
            AboutRow(stringResource(R.string.about_version), viewModel.appInfo.versionName)
            AboutRow(stringResource(R.string.about_api_url), viewModel.effectiveApiUrl())
            AboutRow(stringResource(R.string.about_web_url), viewModel.effectiveWebUrl())
        }

        Spacer(Modifier.height(20.dp))

        // ── Прод-режим: все запросы на https://syntee.fun ──
        SynteeCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.about_production_mode),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.about_production_mode_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = appSettings.productionMode,
                    onCheckedChange = viewModel::setProductionMode,
                )
            }
        }

        if (viewModel.appInfo.isDebug) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.about_dev_urls),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.about_dev_urls_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = apiOverride,
                onValueChange = { apiOverride = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API URL") },
                placeholder = { Text("http://192.168.1.50:8000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = webOverride,
                onValueChange = { webOverride = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Web URL") },
                placeholder = { Text("http://192.168.1.50:3000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Spacer(Modifier.height(12.dp))
            SynteePrimaryButton(
                text = stringResource(R.string.about_apply_urls),
                onClick = {
                    viewModel.applyUrlOverrides(
                        apiOverride.takeIf { it.isNotBlank() },
                        webOverride.takeIf { it.isNotBlank() },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}
