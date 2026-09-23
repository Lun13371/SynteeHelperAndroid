package ru.syntee.helper.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.syntee.helper.core.data.model.ChatMessage
import ru.syntee.helper.core.data.model.MessageRole
import ru.syntee.helper.core.data.model.StreamingState
import ru.syntee.helper.core.designsystem.components.EmptyState
import ru.syntee.helper.feature.chat.markdown.MarkdownText

/**
 * Экран чата: история + живой стрим, Markdown, reasoning-блок,
 * стоп-генерация, автоскролл, копирование, «Повторить» при ошибке.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val chat by viewModel.chat.collectAsStateWithLifecycle()
    val uiMessages by viewModel.uiMessages.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val modelsState by viewModel.modelsState.collectAsStateWithLifecycle()
    val recentModelIds by viewModel.recentModelIds.collectAsStateWithLifecycle()
    val pickerVisible by viewModel.pickerVisible.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var renameDialogVisible by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 1
        }
    }

    // Автоскролл: только если пользователь внизу
    val lastLength = uiMessages.lastOrNull()?.textContentLength() ?: 0
    LaunchedEffect(uiMessages.size, lastLength) {
        if (uiMessages.isNotEmpty() && isAtBottom) {
            listState.scrollToItem(uiMessages.size - 1)
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    if (pickerVisible) {
        ModelPickerSheet(
            state = modelsState,
            recentIds = recentModelIds,
            selectedModelId = selectedModel?.id,
            onSelect = viewModel::selectModel,
            onDismiss = viewModel::closePicker,
        )
    }

    if (renameDialogVisible) {
        var renameText by remember(renameDialogVisible) { mutableStateOf(chat?.title.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renameDialogVisible = false },
            title = { Text(stringResource(R.string.chat_rename)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameChat(renameText)
                        renameDialogVisible = false
                    },
                    enabled = renameText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = chat?.title ?: stringResource(R.string.chat_new_title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            modifier = Modifier.combinedClickable(
                                onClick = { renameDialogVisible = true },
                            ),
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
                            modifier = Modifier.combinedClickable(onClick = viewModel::openPicker),
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
        bottomBar = {
            ChatInputBar(
                text = inputText,
                onTextChange = viewModel::setInput,
                isStreaming = streaming?.let { it.error == null } ?: false,
                onSend = viewModel::sendMessage,
                onStop = viewModel::stopGeneration,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiMessages.isEmpty()) {
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
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(uiMessages, key = { it.messageId() }) { item ->
                        when (item) {
                            is UiMessage.Final -> MessageBubble(
                                message = item.message,
                                onCopy = {
                                    clipboard.setText(AnnotatedString(item.message.content))
                                },
                                onRetry = viewModel::retryLast,
                            )
                            is UiMessage.Streaming -> StreamingBubble(
                                state = item.state,
                                onRetry = viewModel::retryLast,
                            )
                        }
                    }
                }

                // Chip «↓ Вниз» — если пользователь проскроллил вверх
                AnimatedVisibility(
                    visible = !isAtBottom && uiMessages.isNotEmpty(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 8.dp),
                ) {
                    IconButton(
                        onClick = {
                            if (uiMessages.isNotEmpty()) {
                                scope.launch { listState.scrollToItem(uiMessages.size - 1) }
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Icon(
                            Icons.Filled.ArrowDownward,
                            contentDescription = stringResource(R.string.chat_scroll_down),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}

// ── Пузыри ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
) {
    if (message.role == MessageRole.USER) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.large.copy(
                    bottomEnd = androidx.compose.foundation.shape.CornerSize(4.dp),
                ),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .combinedClickable(onClick = {}, onLongClick = onCopy),
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = onCopy),
        ) {
            message.thinking?.let { thinking ->
                ReasoningBlock(text = thinking, durationMs = message.thinkingMs, live = false)
                Spacer(Modifier.height(6.dp))
            }
            if (message.content.isNotBlank()) {
                MarkdownText(markdown = message.content)
            }
            if (message.finishReason == "error") {
                ErrorFooter(onRetry = onRetry)
            } else {
                AssistantFooter(message)
            }
        }
    }
}

@Composable
private fun StreamingBubble(
    state: StreamingState,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (state.thinking.isNotBlank()) {
            ReasoningBlock(
                text = state.thinking,
                durationMs = null,
                live = true,
            )
            Spacer(Modifier.height(6.dp))
        }
        if (state.content.isNotBlank()) {
            Row(verticalAlignment = Alignment.Bottom) {
                MarkdownText(
                    markdown = state.content,
                    streaming = true,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (state.error == null) {
                    Spacer(Modifier.width(4.dp))
                    StreamingCursor()
                }
            }
        } else if (state.error == null) {
            StreamingCursor()
        }
        if (state.error != null) {
            ErrorFooter(onRetry = onRetry)
        }
    }
}

@Composable
private fun StreamingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        modifier = Modifier
            .size(width = 8.dp, height = 16.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
    )
}

// ── Reasoning-блок ────────────────────────────────────────────────────────

@Composable
private fun ReasoningBlock(
    text: String,
    durationMs: Long?,
    live: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = buildString {
                        append(stringResource(R.string.chat_thinking))
                        if (live) append("…")
                        durationMs?.let { append(" · %.1f с".format(it / 1000f)) }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (expanded) "▾" else "▸",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Default,
                        lineHeight = 18.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                )
            }
        }
    }
}

// ── Футеры ────────────────────────────────────────────────────────────────

@Composable
private fun AssistantFooter(message: ChatMessage) {
    val parts = mutableListOf<String>()
    message.model?.let { parts += it }
    if (message.promptTokens != null || message.completionTokens != null) {
        parts += "${message.promptTokens ?: 0}+${message.completionTokens ?: 0} ток."
    }
    message.durationMs?.let { parts += "%.1f с".format(it / 1000f) }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ErrorFooter(onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(top = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.chat_generation_failed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.chat_retry))
            }
        }
    }
}

// ── Инпут ─────────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isStreaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                shape = MaterialTheme.shapes.extraLarge,
                maxLines = 5,
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = if (isStreaming) onStop else onSend,
                enabled = isStreaming || text.isNotBlank(),
            ) {
                if (isStreaming) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.chat_stop),
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send),
                    )
                }
            }
        }
    }
}

// ── Утилиты ───────────────────────────────────────────────────────────────

private fun UiMessage.messageId(): String = when (this) {
    is UiMessage.Final -> message.id
    is UiMessage.Streaming -> state.messageId
}

private fun UiMessage.textContentLength(): Int = when (this) {
    is UiMessage.Final -> message.content.length
    is UiMessage.Streaming -> state.content.length + state.thinking.length
}
