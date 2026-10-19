package com.brahim.whatscraft.ui.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.brahim.whatscraft.data.model.Contact
import com.brahim.whatscraft.data.model.Relationship
import com.brahim.whatscraft.di.Container
import com.brahim.whatscraft.ui.components.Avatar
import com.brahim.whatscraft.ui.components.EmptyState
import com.brahim.whatscraft.ui.components.SectionHeader
import kotlinx.coroutines.launch

/**
 * People: send a request by username, answer the ones you receive, and open a
 * chat with anyone you are connected to.
 */
@Composable
fun ContactsScreen(
    container: Container,
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = container.repository
    val contacts by repository.contacts.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var removeTarget by remember { mutableStateOf<Contact?>(null) }

    val friends = contacts.filter { it.relationship == Relationship.Friends }
    val incoming = contacts.filter { it.relationship == Relationship.PendingIncoming }
    val outgoing = contacts.filter { it.relationship == Relationship.PendingOutgoing }

    fun add() {
        val clean = username.trim().removePrefix("@").lowercase()
        if (clean.isBlank() || busy) return
        busy = true
        error = null
        notice = null
        scope.launch {
            try {
                repository.addContact(clean)
                notice = "Request sent to @$clean"
                username = ""
            } catch (failure: Exception) {
                error = failure.message ?: "Could not send that request."
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item(key = "title") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(20.dp))
                    Text("Connections", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Find each other by username. Both sides have to accept before " +
                            "a chat exists.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.filter { char -> !char.isWhitespace() } },
                        label = { Text("Add by username") },
                        leadingIcon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        trailingIcon = {
                            if (busy) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                IconButton(onClick = ::add, enabled = username.isNotBlank()) {
                                    Icon(Icons.Rounded.Check, contentDescription = "Send request")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    notice?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    error?.let {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            if (incoming.isNotEmpty()) {
                item(key = "incoming-header") { SectionHeader("Waiting for you") }
                items(incoming, key = { "in-${it.id}" }) { contact ->
                    RequestRow(
                        contact = contact,
                        onAccept = { scope.launch { runCatching { repository.acceptContact(contact.requestId.orEmpty()) } } },
                        onDecline = { scope.launch { runCatching { repository.declineContact(contact.requestId.orEmpty()) } } },
                    )
                }
            }

            if (outgoing.isNotEmpty()) {
                item(key = "outgoing-header") { SectionHeader("Sent") }
                items(outgoing, key = { "out-${it.id}" }) { contact ->
                    ContactRow(
                        contact = contact,
                        subtitle = "Waiting for them to accept",
                        onClick = null,
                        onLongClick = null,
                    )
                }
            }

            item(key = "friends-header") {
                SectionHeader(if (friends.isEmpty()) "Your connections" else "Your connections (${friends.size})")
            }

            if (friends.isEmpty()) {
                item(key = "friends-empty") {
                    EmptyState(
                        icon = Icons.Rounded.PersonOutline,
                        title = "Nobody here yet",
                        body = "Ask a friend for their username, or share yours: " +
                            "@${container.sessions.current?.username ?: "you"}",
                    )
                }
            } else {
                items(friends, key = { it.id }) { contact ->
                    ContactRow(
                        contact = contact,
                        subtitle = if (contact.about.isNotBlank()) contact.about else "@${contact.username}",
                        onClick = {
                            scope.launch {
                                runCatching { onOpenChat(repository.openConversation(contact.id)) }
                            }
                        },
                        onLongClick = { removeTarget = contact },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 88.dp),
                    )
                }
            }

            item(key = "bottom-space") { Spacer(Modifier.height(32.dp)) }
        }
    }

    removeTarget?.let { contact ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove connection") },
            text = {
                Text(
                    "You and ${contact.displayName} will no longer be able to message each other. " +
                        "Your existing chat is removed for both of you.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeTarget = null
                        scope.launch { runCatching { repository.removeContact(contact.id) } }
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("Keep") }
            },
        )
    }

    LaunchedEffect(Unit) { runCatching { repository.refreshContacts() } }
}

@Composable
private fun RequestRow(
    contact: Contact,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Avatar(name = contact.displayName.ifBlank { contact.username }, shade = contact.shade, size = 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = contact.displayName.ifBlank { contact.username },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${contact.username} wants to connect",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDecline) {
            Icon(Icons.Rounded.Close, contentDescription = "Decline")
        }
        Button(onClick = onAccept, shape = MaterialTheme.shapes.small) {
            Text("Accept")
        }
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    subtitle: String,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Avatar(
            name = contact.displayName.ifBlank { contact.username },
            shade = contact.shade,
            size = 52.dp,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = contact.displayName.ifBlank { contact.username },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onLongClick != null) {
            TextButton(onClick = { onLongClick() }) { Text("Remove") }
        }
    }
}
