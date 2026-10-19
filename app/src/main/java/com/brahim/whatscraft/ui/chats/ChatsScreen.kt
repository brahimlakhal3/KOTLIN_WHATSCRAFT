package com.brahim.whatscraft.ui.chats

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brahim.whatscraft.data.model.ChatSummary
import com.brahim.whatscraft.di.Container
import com.brahim.whatscraft.ui.components.Avatar
import com.brahim.whatscraft.ui.components.ConnectionPill
import com.brahim.whatscraft.ui.components.EmptyState
import com.brahim.whatscraft.ui.components.CloudNote
import com.brahim.whatscraft.ui.components.TypingDots
import com.brahim.whatscraft.ui.components.UnreadBadge
import com.brahim.whatscraft.util.TimeFormat
import kotlinx.coroutines.launch

/**
 * The chat list. One quiet screen: connection state, a filter, and the
 * conversations ordered by their last message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    container: Container,
    onOpenChat: (String) -> Unit,
    onFindPeople: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = container.repository
    val conversations by repository.conversations.collectAsStateWithLifecycle(initialValue = emptyList())
    val connection by repository.connectionState.collectAsStateWithLifecycle()
    val syncing by repository.syncing.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val visible = remember(conversations, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) {
            conversations
        } else {
            conversations.filter { summary ->
                summary.title.lowercase().contains(needle) ||
                    summary.subtitle?.lowercase()?.contains(needle) == true
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onFindPeople,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("New chat")
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = { scope.launch { repository.refreshAll() } },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "header") {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Whatscraft", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.weight(1f))
                            ConnectionPill(connection)
                        }
                        Spacer(Modifier.size(18.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = ImeAction.Search,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.size(12.dp))
                    }
                }

                if (visible.isEmpty()) {
                    item(key = "empty") {
                        if (query.isBlank() && conversations.isEmpty() && syncing) {
                            SyncingState()
                        } else {
                            EmptyState(
                                icon = Icons.Rounded.ChatBubbleOutline,
                                title = if (query.isBlank()) "No conversations yet" else "Nothing found",
                                body = if (query.isBlank()) {
                                    "Add a friend by their username and your chats will appear here. " +
                                        "Only the two of you can read them."
                                } else {
                                    "No chat matches \"$query\"."
                                },
                                modifier = Modifier.animateContentSize(),
                            )
                        }
                    }
                } else {
                    items(visible, key = { it.id }) { summary ->
                        Column(Modifier.animateItem()) {
                            ConversationRow(
                                summary = summary,
                                onClick = { onOpenChat(summary.id) },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(start = 88.dp),
                            )
                        }
                    }
                }

                item(key = "footer") {
                    Box(Modifier.padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                        CloudNote(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 40.dp),
                        )
                    }
                    Spacer(Modifier.size(72.dp))
                }
            }
        }
    }
}

@Composable
private fun SyncingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 72.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Syncing with the relay",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConversationRow(
    summary: ChatSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = summary.title, shade = summary.shade)

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (summary.muted) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = TimeFormat.chatList(summary.lastMessageAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.size(3.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (summary.typing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "typing",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            TypingDots()
                        }
                    } else {
                        Text(
                            text = summary.subtitle ?: "No messages yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                UnreadBadge(count = summary.unread, muted = summary.muted)
            }
        }
    }
}
