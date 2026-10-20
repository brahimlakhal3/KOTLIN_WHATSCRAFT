package com.brahim.whatscraft.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brahim.whatscraft.BuildConfig
import com.brahim.whatscraft.data.Session
import com.brahim.whatscraft.data.ThemeMode
import com.brahim.whatscraft.di.Container
import com.brahim.whatscraft.ui.components.Avatar
import com.brahim.whatscraft.ui.components.ConnectionPill
import com.brahim.whatscraft.ui.theme.Motion
import com.brahim.whatscraft.ui.theme.WhatscraftColors
import com.brahim.whatscraft.ui.theme.paletteSwatch
import com.brahim.whatscraft.util.TimeFormat
import kotlinx.coroutines.launch

/** One page of the settings stack. The home page is the hub. */
internal enum class SettingsPage(val title: String) {
    Home("Settings"),
    Profile("Profile"),
    Recovery("Recovery"),
    Appearance("Appearance"),
    Chats("Chats"),
    Notifications("Notifications"),
    Privacy("Privacy"),
    Relay("Relay"),
    Account("Account"),
    About("About"),
}

/**
 * Settings as a small stack of pages instead of one endless scroll: a hub with
 * the profile card and one row per topic, then a focused page for each.
 */
@Composable
fun SettingsScreen(
    container: Container,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val session by container.sessions.session.collectAsStateWithLifecycle()
    val connection by container.socket.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var page by rememberSaveable { mutableStateOf(SettingsPage.Home) }
    var direction by remember { mutableIntStateOf(1) }

    fun open(target: SettingsPage) {
        direction = if (target.ordinal >= page.ordinal) 1 else -1
        page = target
    }

    BackHandler(enabled = page != SettingsPage.Home) { open(SettingsPage.Home) }

    // Everything modal lives here so each page stays a plain list of rows.
    var editingProfile by remember { mutableStateOf(false) }
    var recoveryCode by remember { mutableStateOf<String?>(null) }
    var changingPassword by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf(false) }
    var clearingMessages by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val dir = direction
            if (settings.reduceMotion) {
                fadeIn(tween(120)).togetherWith(fadeOut(tween(90)))
            } else {
                (
                    slideInHorizontally(Motion.screenEnter()) { width -> dir * width / 5 } +
                        fadeIn(Motion.screenEnter())
                    ).togetherWith(
                    slideOutHorizontally(Motion.screenExit()) { width -> -dir * width / 8 } +
                        fadeOut(Motion.screenExit()),
                ).using(SizeTransform(clip = false))
            }
        },
        label = "settings-page",
        modifier = modifier.fillMaxSize(),
    ) { target ->
        when (target) {
            SettingsPage.Home -> SettingsHome(
                settings = settings,
                user = session,
                connection = connection,
                onOpen = ::open,
                onEditProfile = { open(SettingsPage.Profile) },
            )

            SettingsPage.Profile -> ProfilePage(
                container = container,
                user = session,
                onBack = { open(SettingsPage.Home) },
            )

            SettingsPage.Recovery -> RecoveryPage(
                onBack = { open(SettingsPage.Home) },
                onRecovery = {
                    scope.launch {
                        runCatching { container.api.rotateRecoveryCode() }
                            .onSuccess { recoveryCode = it.recoveryCode }
                    }
                },
            )

            SettingsPage.Appearance -> AppearancePage(
                container = container,
                settings = settings,
                onBack = { open(SettingsPage.Home) },
            )

            SettingsPage.Chats -> ChatsPage(
                container = container,
                settings = settings,
                onBack = { open(SettingsPage.Home) },
            )

            SettingsPage.Notifications -> NotificationsPage(
                container = container,
                settings = settings,
                onBack = { open(SettingsPage.Home) },
                onBackgroundRelay = { enabled ->
                    scope.launch { container.settings.setBackgroundRelay(enabled) }
                    container.applyBackgroundPreference(enabled)
                },
            )

            SettingsPage.Privacy -> PrivacyPage(
                container = container,
                settings = settings,
                onBack = { open(SettingsPage.Home) },
            )

            SettingsPage.Relay -> RelayPage(
                container = container,
                settings = settings,
                onBack = { open(SettingsPage.Home) },
            )

            SettingsPage.Account -> AccountPage(
                user = session,
                onBack = { open(SettingsPage.Home) },
                onChangePassword = { changingPassword = true },
                onClearMessages = { clearingMessages = true },
                onSignOut = {
                    scope.launch {
                        container.repository.signOut()
                        container.onSignedOut()
                        onSignedOut()
                    }
                },
                onDeleteAccount = { deletingAccount = true },
            )

            SettingsPage.About -> AboutPage(
                settings = settings,
                onBack = { open(SettingsPage.Home) },
            )
        }
    }

    if (editingProfile && session != null) {
        EditProfileDialog(
            displayName = session?.displayName.orEmpty(),
            about = session?.about.orEmpty(),
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
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                    ) {
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

    if (clearingMessages) {
        AlertDialog(
            onDismissRequest = { clearingMessages = false },
            title = { Text("Delete all messages?") },
            text = {
                Text(
                    "Every chat will be emptied for your account on every device. Your " +
                        "contacts stay, and the other person keeps their own copy.",
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
            dismissButton = { TextButton(onClick = { clearingMessages = false }) { Text("Cancel") } },
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

// ------------------------------------------------------------------- home ---

@Composable
private fun SettingsHome(
    settings: com.brahim.whatscraft.data.AppSettings,
    user: Session?,
    connection: com.brahim.whatscraft.data.SocketClient.State,
    onOpen: (SettingsPage) -> Unit,
    onEditProfile: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 32.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(horizontal = 8.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Your profile, this phone and the relay",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            ProfileCard(user = user, onClick = onEditProfile)
        }

        item(key = "security") {
            SettingsGroup("Security") {
                NavRow(
                    icon = Icons.Rounded.Key,
                    title = "Account recovery",
                    subtitle = "A one-time code if you lose this phone",
                    onClick = { onOpen(SettingsPage.Recovery) },
                )
            }
        }

        item(key = "looks") {
            SettingsGroup("Looks") {
                NavRow(
                    icon = Icons.Rounded.Palette,
                    title = "Appearance",
                    subtitle = "${paletteLabel(settings.palette)} · ${themeLabel(settings.themeMode)} · " +
                        "${settings.textSize.label} text",
                    onClick = { onOpen(SettingsPage.Appearance) },
                )
            }
        }

        item(key = "behaviour") {
            SettingsGroup("Behaviour") {
                NavRow(
                    icon = Icons.Rounded.Edit,
                    title = "Chats",
                    subtitle = if (settings.enterToSend) "Enter sends · double-tap reacts"
                    else "Enter adds a line · double-tap reacts",
                    onClick = { onOpen(SettingsPage.Chats) },
                )
                RowDivider()
                NavRow(
                    icon = Icons.Rounded.Notifications,
                    title = "Notifications",
                    subtitle = if (settings.notificationsEnabled) {
                        if (settings.showMessagePreview) "On, with message text" else "On, text hidden"
                    } else {
                        "Off"
                    },
                    onClick = { onOpen(SettingsPage.Notifications) },
                )
                RowDivider()
                NavRow(
                    icon = Icons.Rounded.VerifiedUser,
                    title = "Privacy",
                    subtitle = "Typing indicator",
                    onClick = { onOpen(SettingsPage.Privacy) },
                )
            }
        }

        item(key = "connection") {
            SettingsGroup("Connection") {
                NavRow(
                    icon = Icons.Rounded.Language,
                    title = "Relay",
                    subtitle = "Server address, sync, connection state",
                    trailing = { ConnectionPill(connection) },
                    onClick = { onOpen(SettingsPage.Relay) },
                )
            }
        }

        item(key = "account") {
            SettingsGroup("Account") {
                NavRow(
                    icon = Icons.Rounded.Person,
                    title = "Account",
                    subtitle = "@${user?.username ?: ""} · password, messages, sign out",
                    onClick = { onOpen(SettingsPage.Account) },
                )
                RowDivider()
                NavRow(
                    icon = Icons.Rounded.Info,
                    title = "About Whatscraft",
                    subtitle = "Version ${BuildConfig.VERSION_NAME}",
                    onClick = { onOpen(SettingsPage.About) },
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(user: Session?, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                name = user?.displayName?.ifBlank { user.username } ?: "You",
                shade = user?.shade ?: 0,
                size = 62.dp,
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
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = user?.about.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Edit profile",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- profile ---

@Composable
private fun ProfilePage(
    container: Container,
    user: Session?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var name by remember(user?.userId) { mutableStateOf(user?.displayName.orEmpty()) }
    var about by remember(user?.userId) { mutableStateOf(user?.about.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var shade by remember(user?.userId) { mutableIntStateOf(user?.shade ?: 0) }

    val displayName = user?.displayName?.ifBlank { user.username } ?: "You"
    val dirty = name.trim() != (user?.displayName ?: "") || about.trim() != (user?.about ?: "")

    SettingsPageScaffold(title = "Profile", onBack = onBack) {
        item(key = "hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Avatar(
                    name = name.ifBlank { displayName },
                    shade = shade,
                    size = 92.dp,
                )
                Spacer(Modifier.height(12.dp))
                Text(displayName, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "@${user?.username ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!user?.about.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = user?.about.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        item(key = "shade") {
            SettingsGroup("Avatar tone") {
                val tones = WhatscraftColors.avatarTones
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    tones.forEachIndexed { index, tone ->
                        val selected = index == shade
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                )
                                .clickable {
                                    shade = index
                                    scope.launch {
                                        runCatching { container.repository.updateShade(index) }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(tone),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "details") {
            SettingsGroup("Details") {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            saved = false
                        },
                        label = { Text("Display name") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = about,
                        onValueChange = {
                            about = it
                            saved = false
                        },
                        label = { Text("About") },
                        maxLines = 3,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                busy = true
                                scope.launch {
                                    runCatching {
                                        container.repository.updateProfile(
                                            name.trim().ifBlank { null },
                                            about.trim(),
                                        )
                                    }
                                    busy = false
                                    saved = true
                                }
                            },
                            enabled = !busy && dirty,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Save")
                        }
                        if (saved && !dirty) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Saved",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item(key = "username") {
            SettingsGroup("Username") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "@${user?.username ?: ""}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Friends find you with this. It never changes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString("@${user?.username ?: ""}"))
                        },
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy username")
                    }
                }
            }
        }

        item(key = "note") {
            NoteCard(
                text = "Your name and about are the only things friends see. Chats are " +
                    "stored in your relay and load on every device you sign in to.",
                icon = Icons.Rounded.Cloud,
            )
        }
    }
}

// ---------------------------------------------------------------- dialogs ---

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
