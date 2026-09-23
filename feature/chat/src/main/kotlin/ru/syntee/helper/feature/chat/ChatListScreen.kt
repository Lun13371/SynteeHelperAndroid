package ru.syntee.helper.feature.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.syntee.helper.core.data.model.Chat
import ru.syntee.helper.core.designsystem.components.EmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * РЎРїРёСЃРѕРє С‡Р°С‚РѕРІ: РїРѕРёСЃРє, РїРёРЅ/Р°СЂС…РёРІ (СЃРІР°Р№Рї РІР»РµРІРѕ + undo), СѓРґР°Р»РµРЅРёРµ,
 * РїРµСЂРµРёРјРµРЅРѕРІР°РЅРёРµ (РґРѕР»РіРёР№ С‚Р°Рї в†’ РјРµРЅСЋ), FAB РЅРѕРІРѕРіРѕ С‡Р°С‚Р°.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var searchVisible by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Chat?>(null) }
    var deleteTarget by remember { mutableStateOf<Chat?>(null) }

    // РЎРѕР·РґР°РЅРёРµ С‡Р°С‚Р° в†’ РЅР°РІРёРіР°С†РёСЏ
    LaunchedEffect(Unit) {
        viewModel.createdChat.collect { id -> onChatClick(id) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (searchVisible) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            placeholder = { Text(stringResource(R.string.chat_search_hint)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = stringResource(
                                if (showArchived) R.string.chat_list_archive_title else R.string.chat_list_title,
                            ),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(
                            if (searchVisible) Icons.Outlined.Inbox else Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.chat_search),
                        )
                    }
                    IconButton(onClick = viewModel::toggleArchivedView) {
                        Icon(
                            if (showArchived) Icons.Outlined.Inbox else Icons.Outlined.Archive,
                            contentDescription = stringResource(R.string.chat_archive_toggle),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (!showArchived) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::createChat,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_chat)) },
                )
            }
        },
    ) { innerPadding ->
        if (chats.isEmpty()) {
            EmptyState(
                title = stringResource(
                    if (showArchived) R.string.chat_archive_empty else R.string.chats_empty_title,
                ),
                subtitle = stringResource(
                    if (showArchived) R.string.chat_archive_empty_subtitle else R.string.chats_empty_subtitle,
                ),
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
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 8.dp, end = 8.dp, top = 4.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(chats, key = { it.id }) { chat ->
                    ChatRow(
                        chat = chat,
                        onClick = { onChatClick(chat.id) },
                        onArchive = {
                            viewModel.setArchived(chat, archived = !chat.isArchived)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = if (chat.isArchived) {
                                        "Р§Р°С‚ РІРѕСЃСЃС‚Р°РЅРѕРІР»РµРЅ"
                                    } else {
                                        "Р§Р°С‚ РІ Р°СЂС…РёРІРµ"
                                    },
                                    actionLabel = "РћС‚РјРµРЅРёС‚СЊ",
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.setArchived(chat, archived = chat.isArchived)
                                }
                            }
                        },
                        onPin = { viewModel.setPinned(chat, !chat.isPinned) },
                        onRename = { renameTarget = chat },
                        onDelete = { deleteTarget = chat },
                    )
                }
            }
        }
    }

    renameTarget?.let { chat ->
        RenameDialog(
            initial = chat.title,
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                viewModel.renameChat(chat.id, title)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { chat ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.chat_delete_title)) },
            text = { Text(stringResource(R.string.chat_delete_text, chat.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChat(chat.id)
                    deleteTarget = null
                }) {
                    Text(stringResource(R.string.chat_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChatRow(
    chat: Chat,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onArchive()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.shapes.large,
                    ),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(
                    modifier = Modifier.padding(end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (chat.isArchived) R.string.chat_unarchive else R.string.chat_archive,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatDate(chat.updatedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (chat.isPinned) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = stringResource(R.string.chat_pinned),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (chat.isPinned) R.string.chat_unpin else R.string.chat_pin)) },
                leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                onClick = { menuExpanded = false; onPin() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (chat.isArchived) R.string.chat_unarchive else R.string.chat_archive)) },
                leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                onClick = { menuExpanded = false; onArchive() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_rename)) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = { menuExpanded = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menuExpanded = false; onDelete() },
            )
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("d MMM В· HH:mm", Locale("ru")).format(Date(timestamp))
