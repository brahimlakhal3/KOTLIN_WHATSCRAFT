package com.brahim.whatscraft.ui.contacts

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Search
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
 * People: the whole relay directory, filtered as you type. Anyone who is not
 * connected yet gets an Invite button; requests waiting for you can be
 * accepted right here, and existing connections open their chat.
 */
@Composable
fun ContactsScreen(
    container: Container,
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = container.repository
    val contacts by repository.contacts.collectAsStateWithLifecycle(initialValue = emptyList())
    val people by repository.people.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var inviting by remember { mutableStateOf<String?>(null) }
    var loadingPeople by remember { mutableStateOf(true) }
    var peopleError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var removeTarget by remember { mutableStateOf<Contact?>(null) }

    val friends = contacts.filter { it.relationship == Relationship.Friends }
    val incoming = contacts.filter { it.relationship == Relationship.PendingIncoming }
    val outgoing = contacts.filter { it.relationship == Relationship.PendingOutgoing }
    val directory = people.filter { it.relationship == Relationship.None }

    val cleanQuery = query.trim().removePrefix("@").lowercase()
    fun matches(person: Contact) =
        cleanQuery.isBlank() ||
            person.username.lowercase().contains(cleanQuery) ||
            person.displayName.lowercase().contains(cleanQuery)

    val searchResults = if (cleanQuery.isBlank()) {
        emptyList()
    } else {
        // Directory rows come from the last fetch; live contact rows win so a
        // request that just arrived cannot show up as "Invite" in results.
        val merged = people.associateBy { it.id }.toMutableMap()
        contacts.forEach { merged[it.id] = it }
        merged.values.filter(::matches).sortedBy { it.username.lowercase() }
    }

    fun invite(person: Contact) {
        if (inviting != null) return
        inviting = person.username
        notice = null
        error = null
        scope.launch {
            try {
                repository.addContact(person.username)
                notice = "Invitation sent to @${person.username}"
                runCatching { repository.refreshPeople() }
            } catch (failure: Exception) {
                error = failure.message ?: "Could not send that invitation."
            } finally {
                inviting = null
            }
        }
    }

    fun inviteUsername(username: String) {
        if (inviting != null) return
        inviting = username
        notice = null
        error = null
        scope.launch {
            try {
                repository.addContact(username)
                notice = "Invitation sent to @$username"
                query = ""
                runCatching { repository.refreshPeople() }
            } catch (failure: Exception) {
                error = failure.message ?: "Could not send that invitation."
            } finally {
                inviting = null
            }
        }
    }

    fun accept(contact: Contact) {
        scope.launch {
            runCatching { repository.acceptContact(contact.requestId.orEmpty()) }
            runCatching { repository.refreshPeople() }
        }
    }

    fun decline(contact: Contact) {
        scope.launch {
            runCatching { repository.declineContact(contact.requestId.orEmpty()) }
            runCatching { repository.refreshPeople() }
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
                        text = "Everyone on this app is listed below. Search for a name, then " +
                            "send an invitation. Both sides have to accept before a chat exists.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search people") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
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

            if (cleanQuery.isNotBlank()) {
                item(key = "results-header") { SectionHeader("Results") }

                if (loadingPeople && searchResults.isEmpty()) {
                    item(key = "results-loading") { LoadingPeopleRow() }
                } else if (searchResults.isEmpty()) {
                    val meUsername = container.sessions.current?.username.orEmpty()
                    val canInviteByUsername =
                        cleanQuery.matches(Regex("^[a-z0-9][a-z0-9_.]{2,23}$")) &&
                            !cleanQuery.equals(meUsername, ignoreCase = true)
                    if (canInviteByUsername) {
                        item(key = "invite-username") {
                            PersonRow(
                                person = Contact(
                                    id = cleanQuery,
                                    username = cleanQuery,
                                    displayName = cleanQuery,
                                    about = "",
                                    shade = 0,
                                    relationship = Relationship.None,
                                    requestId = null,
                                ),
                                subtitle = "Not in the directory yet - send a direct invitation",
                                busy = inviting != null,
                                onInvite = { inviteUsername(cleanQuery) },
                            )
                        }
                    } else {
                        item(key = "results-empty") {
                            EmptyState(
                                icon = Icons.Rounded.PersonOutline,
                                title = "No one found",
                                body = "Try another name or @username.",
                            )
                        }
                    }
                }

                items(searchResults, key = { "result-${it.id}" }) { person ->
                    when (person.relationship) {
                        Relationship.Friends -> ContactRow(
                            contact = person,
                            subtitle = if (person.about.isNotBlank()) person.about else "@${person.username}",
                            onClick = {
                                scope.launch {
                                    runCatching { onOpenChat(repository.openConversation(person.id)) }
                                }
                            },
                            onLongClick = null,
                        )

                        Relationship.PendingIncoming -> RequestRow(
                            contact = person,
                            onAccept = { accept(person) },
                            onDecline = { decline(person) },
                        )

                        Relationship.PendingOutgoing -> ContactRow(
                            contact = person,
                            subtitle = "Waiting for them to accept",
                            onClick = null,
                            onLongClick = null,
                        )

                        else -> PersonRow(
                            person = person,
                            subtitle = if (person.about.isNotBlank()) {
                                "@${person.username} · ${person.about}"
                            } else {
                                "@${person.username}"
                            },
                            busy = inviting == person.username,
                            onInvite = { invite(person) },
                        )
                    }
                }
            } else {
                if (incoming.isNotEmpty()) {
                    item(key = "incoming-header") { SectionHeader("Waiting for you") }
                    items(incoming, key = { "in-${it.id}" }) { contact ->
                        RequestRow(
                            contact = contact,
                            onAccept = { accept(contact) },
                            onDecline = { decline(contact) },
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
                            body = "Invite someone from the directory below, or share your username: " +
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

                item(key = "directory-header") { SectionHeader("Everyone on Whatscraft") }

                if (loadingPeople) {
                    item(key = "directory-loading") { LoadingPeopleRow() }
                } else if (peopleError != null) {
                    item(key = "directory-error") {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Text(
                                text = peopleError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        loadingPeople = true
                                        peopleError = null
                                        runCatching { repository.refreshPeople() }
                                            .onFailure { peopleError = it.message ?: "Could not load people." }
                                        loadingPeople = false
                                    }
                                },
                            ) {
                                Text("Try again")
                            }
                        }
                    }
                } else if (directory.isEmpty()) {
                    item(key = "directory-empty") {
                        EmptyState(
                            icon = Icons.Rounded.PersonAdd,
                            title = "No new people right now",
                            body = "Everyone here is already in your connections, or you are the " +
                                "first one. Share @${container.sessions.current?.username ?: "your username"}.",
                        )
                    }
                } else {
                    items(directory, key = { "person-${it.id}" }) { person ->
                        PersonRow(
                            person = person,
                            subtitle = if (person.about.isNotBlank()) {
                                "@${person.username} · ${person.about}"
                            } else {
                                "@${person.username}"
                            },
                            busy = inviting == person.username,
                            onInvite = { invite(person) },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(start = 88.dp),
                        )
                    }
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
                        scope.launch {
                            runCatching { repository.removeContact(contact.id) }
                            runCatching { repository.refreshPeople() }
                        }
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

    LaunchedEffect(Unit) {
        runCatching { repository.refreshContacts() }
        loadingPeople = true
        peopleError = null
        try {
            repository.refreshPeople()
        } catch (failure: Exception) {
            peopleError = failure.message ?: "Could not load people."
        } finally {
            loadingPeople = false
        }
    }
}

@Composable
private fun LoadingPeopleRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Looking for people...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PersonRow(
    person: Contact,
    subtitle: String,
    busy: Boolean,
    onInvite: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Avatar(
            name = person.displayName.ifBlank { person.username },
            shade = person.shade,
            size = 52.dp,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = person.displayName.ifBlank { person.username },
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
        OutlinedButton(
            onClick = onInvite,
            enabled = !busy,
            shape = MaterialTheme.shapes.small,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Text("Invite")
            }
        }
    }
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
