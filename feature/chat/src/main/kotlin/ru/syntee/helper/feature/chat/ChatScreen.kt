package ru.syntee.helper.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.syntee.helper.core.designsystem.components.EmptyState

/**
 * Экран чата: шапка (назад + модель), тело сообщений, инпут-бар.
 * Стадия 03: история из Room, стриминг, Markdown, reasoning-блок.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val modelsState by viewModel.modelsState.collectAsStateWithLifecycle()
    val pickerVisible by viewModel.pickerVisible.collectAsStateWithLifecycle()

    if (pickerVisible) {
        ModelPickerSheet(
            state = modelsState,
            recentIds = emptyList(), // стадия 03: недавние из настроек
            selectedModelId = selectedModel?.id,
            onSelect = viewModel::selectModel,
            onDismiss = viewModel::closePicker,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable { viewModel.openPicker() },
                    ) {
                        Text(
                            text = stringResource(R.string.chat_new_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = selectedModel?.displayName
                                ?: stringResource(R.string.chat_model_none),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedModel != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
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
        },
        bottomBar = { ChatInputBar() },
    ) { innerPadding ->
        EmptyState(
            title = stringResource(R.string.chat_empty_title),
            subtitle = stringResource(R.string.chat_empty_subtitle),
            icon = {
                Icon(
                    Icons.Filled.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@Composable
private fun ChatInputBar() {
    var text by remember { mutableStateOf("") }

    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                shape = MaterialTheme.shapes.extraLarge,
                maxLines = 5,
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { text = "" }, // стадия 03: sendMessage()
                enabled = text.isNotBlank(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.chat_send),
                )
            }
        }
    }
}
