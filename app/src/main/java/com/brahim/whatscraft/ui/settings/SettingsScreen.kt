package com.brahim.whatscraft.ui.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brahim.whatscraft.BuildConfig
import com.brahim.whatscraft.data.Palette
import com.brahim.whatscraft.data.ThemeMode
import com.brahim.whatscraft.di.Container
import com.brahim.whatscraft.ui.components.Avatar
import com.brahim.whatscraft.ui.components.EncryptionNote
import com.brahim.whatscraft.ui.components.SectionHeader
import com.brahim.whatscraft.ui.components.SettingsRow
import com.brahim.whatscraft.ui.theme.Ink
import com.brahim.whatscraft.ui.theme.paletteSwatch
import com.brahim.whatscraft.util.TimeFormat
import kotlinx.coroutines.launch

/**
 * Settings: profile, encryption keys, appearance, notifications, privacy and
 * the two irreversible actions.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: Container,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val session by container.sessions.session.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var editingProfile by remember { mutableStateOf(false) }
    var recoveryCode by remember { mutableStateOf<String?>(null) }
    var changingPassword by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf(false) }
    var rotatingKeys by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf(settings.serverUrl) }
    var serverStatus by remember { mutableStateOf<String?>(null) }
    var checkingServer by remember { mutableStateOf(false) }
    var clearingMessages by remember { mutableStateOf(false) }

    LaunchedEffect(settings.serverUrl) { serverUrl = settings.serverUrl }

    val user = session

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
                    Text("Settings", style = MaterialTheme.typography.headlineMedium)
                }
            }

            item(key = "profile") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(
                            name = user?.displayName?.ifBlank { user.username } ?: "You",
                            shade = user?.shade ?: 0,
                            size = 64.dp,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = user?.displayName?.ifBlank { user.username } ?: "You",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = "@${user?.username ?: ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!user?.about.isNullOrBlank()) {
                                Text(
                                    text = user?.about.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { editingProfile = true },
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Edit")
                        }
                    }
                }
            }

            item(key = "security-header") { SectionHeader("Encryption") }
            item(key = "recovery") {
                SettingsRow(
                    title = "Recovery code",
                    subtitle = "Get a fresh one-time code for this account",
                    icon = Icons.Rounded.Key,
                    onClick = {
                        scope.launch {
                            runCatching { container.api.rotateRecoveryCode() }
                                .onSuccess { recoveryCode = it.recoveryCode }
                        }
                    },
                )
            }
            item(key = "identity") {
                SettingsRow(
                    title = "This device's key",
                    subtitle = "Copy it for a friend to check, or reset it if you are worried",
                    icon = Icons.Rounded.VerifiedUser,
                    onClick = {
                        clipboard.setText(AnnotatedString(container.repository.myIdentityKey()))
                    },
                    trailing = {
                        TextButton(onClick = { rotatingKeys = true }) { Text("Reset") }
                    },
                )
            }
            item(key = "password") {
                SettingsRow(
                    title = "Change password",
                    subtitle = "You will stay signed in on this phone",
                    icon = Icons.Rounded.Lock,
                    onClick = { changingPassword = true },
                )
            }

            item(key = "appearance-header") { SectionHeader("Appearance") }
            item(key = "theme") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { scope.launch { container.settings.setThemeMode(mode) } },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                                label = {
                                    Text(
                                        when (mode) {
                                            ThemeMode.System -> "System"
                                            ThemeMode.Light -> "Light"
                                            ThemeMode.Dark -> "Dark"
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
            item(key = "palette") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.ColorLens,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Colors", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = paletteBlurb(settings.palette),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Palette.entries.forEach { choice ->
                            val selected = settings.palette == choice
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.surfaceVariant
                                        else Color.Transparent,
                                    )
                                    .clickable {
                                        scope.launch { container.settings.setPalette(choice) }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(paletteSwatch(choice, dark = dark)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = if (choice == Palette.Monochrome && dark) Ink else Color.White,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "pure-black") {
                SwitchRow(
                    title = "True black",
                    subtitle = "OLED black in dark monochrome",
                    icon = Icons.Rounded.DarkMode,
                    checked = settings.pureBlack,
                    enabled = settings.palette == Palette.Monochrome,
                    onChange = { scope.launch { container.settings.setPureBlack(it) } },
                )
            }

            item(key = "notifications-header") { SectionHeader("Notifications") }
            item(key = "notifications") {
                SwitchRow(
                    title = "Message notifications",
                    subtitle = "Nothing shows while a chat is open",
                    icon = Icons.Rounded.Notifications,
                    checked = settings.notificationsEnabled,
                    onChange = { scope.launch { container.settings.setNotifications(it) } },
                )
            }
            item(key = "preview") {
                SwitchRow(
                    title = "Show message text",
                    subtitle = "Off keeps your words off the lock screen",
                    icon = Icons.Rounded.Lock,
                    checked = settings.showMessagePreview,
                    onChange = { scope.launch { container.settings.setShowMessagePreview(it) } },
                )
            }
            item(key = "relay") {
                SwitchRow(
                    title = "Stay connected",
                    subtitle = "A quiet notification keeps messages arriving with the app closed",
                    icon = Icons.Rounded.Refresh,
                    checked = settings.backgroundRelay,
                    onChange = {
                        scope.launch { container.settings.setBackgroundRelay(it) }
                        container.applyBackgroundPreference(it)
                    },
                )
            }

            item(key = "privacy-header") { SectionHeader("Privacy") }
            item(key = "receipts") {
                SwitchRow(
                    title = "Read receipts",
                    subtitle = "Share when you have read a message",
                    icon = Icons.Rounded.VerifiedUser,
                    checked = settings.readReceipts,
                    onChange = { scope.launch { container.settings.setReadReceipts(it) } },
                )
            }
            item(key = "typing") {
                SwitchRow(
                    title = "Typing indicator",
                    subtitle = "Show when you are writing",
                    icon = Icons.Rounded.Edit,
                    checked = settings.typingIndicators,
                    onChange = { scope.launch { container.settings.setTypingIndicators(it) } },
                )
            }
            item(key = "enter") {
                SwitchRow(
                    title = "Enter sends",
                    subtitle = "Off: Enter adds a line break",
                    icon = Icons.Rounded.Edit,
                    checked = settings.enterToSend,
                    onChange = { scope.launch { container.settings.setEnterToSend(it) } },
                )
            }

            item(key = "server-header") { SectionHeader("Relay") }
            item(key = "server") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Text("Server", style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        placeholder = { Text("https://your-relay.example") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    container.settings.setServerUrl(serverUrl)
                                    serverStatus = "Saved"
                                }
                            },
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    checkingServer = true
                                    container.settings.setServerUrl(serverUrl)
                                    val reachable = runCatching { container.api.health() }.getOrDefault(false)
                                    serverStatus = if (reachable) "Reachable" else "No answer from that address"
                                    checkingServer = false
                                }
                            },
                            enabled = !checkingServer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            if (checkingServer) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Test")
                        }
                    }
                    serverStatus?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "account-header") { SectionHeader("Account") }
            item(key = "clear-messages") {
                SettingsRow(
                    title = "Delete all messages",
                    subtitle = "Empties every conversation on this phone; contacts and keys stay",
                    icon = Icons.Rounded.DeleteSweep,
                    destructive = true,
                    onClick = { clearingMessages = true },
                )
            }
            item(key = "signout") {
                SettingsRow(
                    title = "Sign out",
                    subtitle = "Messages stay encrypted on this phone",
                    icon = Icons.Rounded.Logout,
                    onClick = {
                        scope.launch {
                            container.repository.signOut()
                            container.onSignedOut()
                            onSignedOut()
                        }
                    },
                )
            }
            item(key = "delete") {
                SettingsRow(
                    title = "Delete account",
                    subtitle = "Removes your account and every key on this device",
                    icon = Icons.Rounded.DeleteForever,
                    destructive = true,
                    onClick = { deletingAccount = true },
                )
            }

            item(key = "about") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                    EncryptionNote(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Whatscraft ${BuildConfig.VERSION_NAME} - every message is encrypted " +
                            "on your phone before it reaches the relay, and the relay only ever " +
                            "stores encrypted bytes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (settings.lastSyncedAt > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Last synced ${TimeFormat.chatList(settings.lastSyncedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (clearingMessages) {
        AlertDialog(
            onDismissRequest = { clearingMessages = false },
            title = { Text("Delete all messages?") },
            text = {
                Text(
                    "Every conversation on this phone will be emptied. Your contacts, " +
                        "friends and encryption keys stay. The relay only ever held " +
                        "ciphertext, so nothing readable is deleted there.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearingMessages = false
                        scope.launch { container.repository.clearAllMessages() }
                    },
                ) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearingMessages = false }) { Text("Cancel") }
            },
        )
    }

    if (editingProfile && user != null) {
        EditProfileDialog(
            displayName = user.displayName,
            about = user.about,
            onSave = { name, about ->
                scope.launch { runCatching { container.repository.updateProfile(name, about) } }
            },
            onDismiss = { editingProfile = false },
        )
    }

    recoveryCode?.let { code ->
        AlertDialog(
            onDismissRequest = { recoveryCode = null },
            title = { Text("Your new recovery code") },
            text = {
                Column {
                    Text(
                        "Save this now. It replaces the previous code and will not be shown again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        recoveryCode = null
                    },
                ) {
                    Text("Copy")
                }
            },
            dismissButton = { TextButton(onClick = { recoveryCode = null }) { Text("Close") } },
        )
    }

    if (changingPassword) {
        ChangePasswordDialog(
            onSubmit = { current, next -> container.api.changePassword(passwordChange(current, next)) },
            onDismiss = { changingPassword = false },
        )
    }

    if (rotatingKeys) {
        AlertDialog(
            onDismissRequest = { rotatingKeys = false },
            title = { Text("Reset this device's key?") },
            text = {
                Text(
                    "A new key means your old messages stay readable on this phone, but friends " +
                        "will see a new safety number. Do this only if you believe your key was " +
                        "compromised.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        rotatingKeys = false
                        scope.launch { runCatching { container.repository.regenerateIdentity() } }
                    },
                ) {
                    Text("Reset key")
                }
            },
            dismissButton = { TextButton(onClick = { rotatingKeys = false }) { Text("Cancel") } },
        )
    }

    if (deletingAccount) {
        DeleteAccountDialog(
            onDelete = { password ->
                container.repository.deleteAccount(password)
                container.onSignedOut()
                onSignedOut()
            },
            onDismiss = { deletingAccount = false },
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val toggle: (() -> Unit)? = if (enabled) ({ onChange(!checked) }) else null
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onChange else null,
                enabled = enabled,
            )
        },
        onClick = toggle,
    )
}

@Composable
private fun EditProfileDialog(
    displayName: String,
    about: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(displayName) }
    var bio by remember { mutableStateOf(about) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your profile") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("About") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(name.trim(), bio.trim())
                    onDismiss()
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChangePasswordDialog(
    onSubmit: suspend (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change password") },
        text = {
            Column {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text("Current password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = next,
                    onValueChange = { next = it },
                    label = { Text("New password") },
                    supportingText = { Text("At least 8 characters") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            val submit: () -> Unit = {
                if (!busy) {
                    error = when {
                        next.length < 8 -> "New password is too short."
                        else -> null
                    }
                    if (error == null) {
                        busy = true
                        scope.launch {
                            try {
                                onSubmit(current, next)
                                onDismiss()
                            } catch (failure: Exception) {
                                error = failure.message ?: "Could not change the password."
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
            }
            TextButton(onClick = submit, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Text("Change")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteAccountDialog(
    onDelete: suspend (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete your account?") },
        text = {
            Column {
                Text(
                    "This removes your account, your messages on the relay and every key on this " +
                        "phone. It cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Your password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && password.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            onDelete(password)
                        } catch (failure: Exception) {
                            error = failure.message ?: "Could not delete the account."
                            busy = false
                        }
                    }
                },
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun passwordChange(current: String, next: String) =
    com.brahim.whatscraft.data.model.ChangePasswordRequest(currentPassword = current, newPassword = next)

/** One line under "Colors" naming the active palette. */
private fun paletteBlurb(palette: Palette): String = when (palette) {
    Palette.Monochrome -> "Black and white, nothing else"
    Palette.WhatsApp -> "Green accents and a beige conversation"
    Palette.Ocean -> "Blue accents and a cool conversation"
    Palette.Berry -> "Violet accents and a soft conversation"
    Palette.Sunset -> "Orange accents and a warm conversation"
}
