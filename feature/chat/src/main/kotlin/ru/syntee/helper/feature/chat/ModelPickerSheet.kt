package ru.syntee.helper.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.syntee.helper.core.data.model.ChatModel
import ru.syntee.helper.core.data.model.ModelStatus
import ru.syntee.helper.core.data.model.ModelsState

/**
 * Селектор моделей: bottom sheet с поиском, статусами, pricing за 1M,
 * capability-иконками. Секции: недавние / все.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    state: ModelsState,
    recentIds: List<String>,
    selectedModelId: String?,
    onSelect: (ChatModel) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.model_picker_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.model_picker_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            Spacer(Modifier.height(12.dp))

            when (state) {
                is ModelsState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ModelsState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    if (state.cached.isNotEmpty()) {
                        ModelList(
                            models = state.cached.filterMatches(query),
                            recentIds = recentIds,
                            selectedModelId = selectedModelId,
                            onSelect = onSelect,
                        )
                    }
                }
                is ModelsState.Loaded -> ModelList(
                    models = state.models.filterMatches(query),
                    recentIds = recentIds,
                    selectedModelId = selectedModelId,
                    onSelect = onSelect,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModelList(
    models: List<ChatModel>,
    recentIds: List<String>,
    selectedModelId: String?,
    onSelect: (ChatModel) -> Unit,
) {
    val recent = models.filter { it.id in recentIds }
    val rest = models - recent.toSet()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(420.dp),
    ) {
        if (recent.isNotEmpty() && rest.isNotEmpty()) {
            item(key = "recent_header") {
                SectionLabel(stringResource(R.string.model_picker_recent))
            }
        }
        items(recent, key = { "recent_${it.id}" }) { model ->
            ModelRow(model, model.id == selectedModelId, onSelect)
        }
        if (recent.isNotEmpty() && rest.isNotEmpty()) {
            item(key = "all_header") {
                SectionLabel(stringResource(R.string.model_picker_all))
            }
        }
        items(rest, key = { it.id }) { model ->
            ModelRow(model, model.id == selectedModelId, onSelect)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun ModelRow(
    model: ChatModel,
    isSelected: Boolean,
    onClick: (ChatModel) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(model) }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(model.status)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = model.id,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                model.maxTokens?.let {
                    Text(
                        text = formatContext(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                model.inputPricePer1k?.let { price ->
                    Text(
                        text = formatPricePer1M(price),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (model.supportsVision || model.supportsTools || model.reasoningSupported) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (model.reasoningSupported) {
                        CapabilityIcon(Icons.Outlined.Psychology, R.string.model_cap_reasoning)
                    }
                    if (model.supportsVision) {
                        CapabilityIcon(Icons.Outlined.Visibility, R.string.model_cap_vision)
                    }
                    if (model.supportsTools) {
                        CapabilityIcon(Icons.Outlined.Code, R.string.model_cap_tools)
                    }
                    if (model.supportsStreaming) {
                        CapabilityIcon(Icons.Outlined.Bolt, R.string.model_cap_streaming)
                    }
                }
            }
        }
        if (isSelected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CapabilityIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, labelRes: Int) {
    Icon(
        icon,
        contentDescription = stringResource(labelRes),
        modifier = Modifier.size(14.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusDot(status: ModelStatus) {
    val color = when (status) {
        ModelStatus.OPERATIONAL, ModelStatus.AVAILABLE -> MaterialTheme.colorScheme.tertiary
        ModelStatus.DEGRADED -> androidx.compose.ui.graphics.Color(0xFFF0A030)
        ModelStatus.DOWN, ModelStatus.UNAVAILABLE -> MaterialTheme.colorScheme.error
        ModelStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, CircleShape),
    )
}

private fun List<ChatModel>.filterMatches(query: String): List<ChatModel> {
    if (query.isBlank()) return this
    val q = query.trim().lowercase()
    return filter { it.id.lowercase().contains(q) || it.displayName.lowercase().contains(q) }
}

private fun formatContext(maxTokens: Long): String {
    val k = maxTokens / 1000
    return if (k >= 1000) "${k / 1000}M" else "${k}k"
}

/** Pricing в /v1/models — за 1k; UI показывает за 1M (×1000), как десктоп. */
private fun formatPricePer1M(pricePer1k: Double): String {
    val per1m = pricePer1k * 1000
    return if (per1m == 0.0) "free" else "₽%.0f/1M".format(per1m)
}
