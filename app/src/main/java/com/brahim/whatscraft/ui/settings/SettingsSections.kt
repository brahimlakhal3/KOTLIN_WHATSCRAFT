package com.brahim.whatscraft.ui.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brahim.whatscraft.BuildConfig
import com.brahim.whatscraft.data.AppSettings
import com.brahim.whatscraft.data.BubbleStyle
import com.brahim.whatscraft.data.Palette
import com.brahim.whatscraft.data.Session
import com.brahim.whatscraft.data.TextSize
import com.brahim.whatscraft.data.ThemeMode
import com.brahim.whatscraft.di.Container
import com.brahim.whatscraft.ui.components.ConnectionPill
import com.brahim.whatscraft.ui.theme.Ink
import com.brahim.whatscraft.ui.theme.WhatscraftColors
import com.brahim.whatscraft.ui.theme.bubbleShape
import com.brahim.whatscraft.ui.theme.paletteSwatch
import com.brahim.whatscraft.util.TimeFormat
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- recovery ---

@Composable
internal fun RecoveryPage(
    onBack: () -> Unit,
    onRecovery: () -> Unit,
) {
    SettingsPageScaffold(title = "Recovery", onBack = onBack) {
        item(key = "intro") {
            PageIntro(
                title = "Account recovery",
                subtitle = "No email, no phone number. One code is all that stands between " +
                    "you and a lost password.",
            )
        }

        item(key = "recovery") {
            SettingsGroup("Recovery code") {
                NavRow(
                    icon = Icons.Rounded.Key,
                    title = "Get a new recovery code",
                    subtitle = "Replaces the old one immediately",
                    onClick = onRecovery,
                )
            }
        }

        item(key = "note") {
            NoteCard(
                text = "Keep the code somewhere safe and offline. It is shown once, and it " +
                    "is the only way back into the account if you forget your password.",
                icon = Icons.Rounded.Key,
            )
        }
    }
}

// -------------------------------------------------------------- appearance ---

@Composable
internal fun AppearancePage(
    container: Container,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    SettingsPageScaffold(title = "Appearance", onBack = onBack) {
        item(key = "intro") {
            PageIntro(
                title = "Looks",
                subtitle = "Every change is applied to the whole app the moment you pick it.",
            )
        }

        item(key = "theme") {
            SettingsGroup("Theme") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("Mode", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    SegmentedRow(
                        options = ThemeMode.entries,
                        selected = settings.themeMode,
                        label = ::themeLabel,
                        onSelect = { scope.launch { container.settings.setThemeMode(it) } },
                    )
                }
            }
        }

        item(key = "palette") {
            SettingsGroup("Colour story") {
                PaletteGrid(
                    selected = settings.palette,
                    dark = dark,
                    onSelect = { scope.launch { container.settings.setPalette(it) } },
                )
            }
        }

        item(key = "palette-blurb") {
            NoteCard(
                text = paletteBlurb(settings.palette),
                icon = Icons.Rounded.Palette,
            )
        }

        item(key = "preview") {
            SettingsGroup("Preview") {
                ChatPreview(settings)
            }
        }

        item(key = "text") {
            SettingsGroup("Message text") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("Size", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    SegmentedRow(
                        options = TextSize.entries,
                        selected = settings.textSize,
                        label = { it.label },
                        onSelect = { scope.launch { container.settings.setTextSize(it) } },
                    )
                }
            }
        }

        item(key = "bubbles") {
            SettingsGroup("Bubble shape") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    SegmentedRow(
                        options = BubbleStyle.entries,
                        selected = settings.bubbleStyle,
                        label = { it.label },
                        onSelect = { scope.launch { container.settings.setBubbleStyle(it) } },
                    )
                }
            }
        }

        item(key = "extras") {
            SettingsGroup("Extras") {
                SwitchRow(
                    icon = Icons.Rounded.DarkMode,
                    title = "True black",
                    subtitle = "OLED black canvas in dark monochrome",
                    checked = settings.pureBlack,
                    enabled = settings.palette == Palette.Monochrome,
                    onChange = { scope.launch { container.settings.setPureBlack(it) } },
                )
                RowDivider()
                SwitchRow(
                    icon = Icons.Rounded.VerifiedUser,
                    title = "Reduce motion",
                    subtitle = "No pop-ins or sliding pages",
                    checked = settings.reduceMotion,
                    onChange = { scope.launch { container.settings.setReduceMotion(it) } },
                )
            }
        }
    }
}

@Composable
private fun PaletteGrid(
    selected: Palette,
    dark: Boolean,
    onSelect: (Palette) -> Unit,
) {
    val rows = Palette.entries.chunked(5)
    Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { palette ->
                    val active = palette == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(palette) }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(paletteSwatch(palette, dark)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (active) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = if (palette == Palette.Monochrome && dark) Ink else Color.White,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = paletteShortLabel(palette),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A miniature conversation that redraws with the chosen bubbles and type. */
@Composable
private fun ChatPreview(settings: AppSettings) {
    Surface(
        color = WhatscraftColors.threadBackground,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Surface(
                    color = WhatscraftColors.bubbleIncoming,
                    shape = bubbleShape(settings.bubbleStyle, mine = false),
                ) {
                    Text(
                        text = "This is how your chats look.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    color = WhatscraftColors.bubbleOutgoing,
                    contentColor = WhatscraftColors.onBubbleOutgoing,
                    shape = bubbleShape(settings.bubbleStyle, mine = true),
                ) {
                    Text(
                        text = "Clean and private.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------- chats ---

@Composable
internal fun ChatsPage(
    container: Container,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    SettingsPageScaffold(title = "Chats", onBack = onBack) {
        item(key = "intro") {
            PageIntro(
                title = "Chats",
                subtitle = "How writing, sending and reacting feels.",
            )
        }

        item(key = "behaviour") {
            SettingsGroup("Writing") {
                SwitchRow(
                    icon = Icons.Rounded.Edit,
                    title = "Enter sends",
                    subtitle = "Off: Enter adds a line break",
                    checked = settings.enterToSend,
                    onChange = { scope.launch { container.settings.setEnterToSend(it) } },
                )
                RowDivider()
                SwitchRow(
                    icon = Icons.Rounded.Favorite,
                    title = "Double-tap to react",
                    subtitle = "Double-tapping a bubble toggles the heart",
                    checked = settings.doubleTapHeart,
                    onChange = { scope.launch { container.settings.setDoubleTapHeart(it) } },
                )
                RowDivider()
                SwitchRow(
                    icon = Icons.Rounded.Vibration,
                    title = "Haptics",
                    subtitle = "A short tick on send, reactions and menus",
                    checked = settings.haptics,
                    onChange = { scope.launch { container.settings.setHaptics(it) } },
                )
            }
        }

        item(key = "note") {
            NoteCard(
                text = "Opening a chat always lands on the newest message, and the keyboard " +
                    "brings the conversation up with it. Load earlier pages thirty at a time.",
                icon = Icons.Rounded.ChatBubble,
            )
        }
    }
}

// ----------------------------------------------------------- notifications ---

@Composable
internal fun NotificationsPage(
    container: Container,
    settings: AppSettings,
    onBack: () -> Unit,
    onBackgroundRelay: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()

    SettingsPageScaffold(title = "Notifications", onBack = onBack) {
        item(key = "intro") {
            PageIntro(
                title = "Notifications",
                subtitle = "Small, quiet, and only for messages that are not on screen.",
            )
        }

        item(key = "alerts") {
            SettingsGroup("Alerts") {
                SwitchRow(
                    icon = Icons.Rounded.Notifications,
                    title = "Message notifications",
                    subtitle = "Nothing shows while a chat is open",
                    checked = settings.notificationsEnabled,
                    onChange = { scope.launch { container.settings.setNotifications(it) } },
                )
                RowDivider()
                SwitchRow(
                    icon = Icons.Rounded.Lock,
                    title = "Show message text",
                    subtitle = "Off keeps your words off the lock screen",
                    checked = settings.showMessagePreview,
                    enabled = settings.notificationsEnabled,
                    onChange = { scope.launch { container.settings.setShowMessagePreview(it) } },
                )
            }
        }

        item(key = "background") {
            SettingsGroup("Background") {
                SwitchRow(
                    icon = Icons.Rounded.Refresh,
                    title = "Stay connected",
                    subtitle = "A quiet notification keeps messages arriving with the app closed",
                    checked = settings.backgroundRelay,
                    onChange = onBackgroundRelay,
                )
            }
        }

        item(key = "note") {
            NoteCard(
                text = "The notification is posted by this phone when a message arrives, and " +
                    "nothing is shown while you are reading the same conversation.",
                icon = Icons.Rounded.Notifications,
            )
        }
    }
}

// ----------------------------------------------------------------- privacy ---

@Composable
internal fun PrivacyPage(
    container: Container,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    SettingsPageScaffold(title = "Privacy", onBack = onBack) {
        item(key = "intro") {
            PageIntro(
                title = "Privacy",
                subtitle = "What other people can and cannot learn about you.",
            )
        }

        item(key = "chat") {
            SettingsGroup("Inside a chat") {
                SwitchRow(
                    icon = Icons.Rounded.Edit,
                    title = "Typing indicator",
                    subtitle = "Show when you are writing",
                    checked = settings.typingIndicators,
                    onChange = { scope.launch { container.settings.setTypingIndicators(it) } },
                )
            }
        }

        item(key = "presence-note") {
            NoteCard(
                text = "There is no online status and no last seen in Whatscraft. Nothing tells " +
                    "other people when you are here or when you last opened the app. Your phone " +
                    "connects when it needs to, quietly.",
                icon = Icons.Rounded.VisibilityOff,
            )
        }

        item(key = "keys-note") {
            NoteCard(
                text = "Messages are stored on your relay so they can load on any device you " +
                    "sign in to. The relay can read them; pick one you trust, or run your own.",
                icon = Icons.Rounded.Cloud,
            )
        }
    }
}

// -------------------------------------------------------------------- relay ---

@Composable
internal fun RelayPage(
    container: Container,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val connection by container.socket.state.collectAsStateWithLifecycle()
    var serverUrl by remember { mutableStateOf(settings.serverUrl) }
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }

    LaunchedEffect(settings.serverUrl) { serverUrl = settings.serverUrl }

    SettingsPageScaffold(title = "Relay", onBack = onBack) {
        item(key = "intro") {
            PageIntro(
                title = "Relay",
                subtitle = "The server that stores your chats. Everything you send lives there " +
                    "in plain text, waiting for your other devices.",
            )
        }

        item(key = "address") {
            SettingsGroup("Address") {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        placeholder = { Text("https://your-relay.example") },
                        label = { Text("Server URL") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    container.settings.setServerUrl(serverUrl)
                                    status = "Saved"
                                }
                            },
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    checking = true
                                    status = null
                                    container.settings.setServerUrl(serverUrl)
                                    val reachable = runCatching { container.api.health() }
                                        .getOrDefault(false)
                                    status = if (reachable) "Reachable" else "No answer from that address"
                                    checking = false
                                }
                            },
                            enabled = !checking,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            if (checking) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Test")
                        }
                    }
                    status?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item(key = "connection") {
            SettingsGroup("Connection") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Status", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (settings.lastSyncedAt > 0) {
                                "Last sync ${TimeFormat.chatList(settings.lastSyncedAt)}"
                            } else {
                                "Not synced yet"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ConnectionPill(connection)
                }
                RowDivider()
                NavRow(
                    icon = Icons.Rounded.Refresh,
                    title = "Sync now",
                    subtitle = "Contacts, conversations and queued messages",
                    trailing = {
                        if (syncing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    onClick = {
                        if (syncing) return@NavRow
                        scope.launch {
                            syncing = true
                            runCatching { container.repository.refreshAll() }
                            syncing = false
                        }
                    },
                )
            }
        }

        item(key = "note") {
            NoteCard(
                text = "The public relay runs on a free tier and sleeps after about fifteen " +
                    "minutes without traffic. The first connection of the day can take a " +
                    "minute; the app retries on its own and keeps queued messages safe.",
                icon = Icons.Rounded.Language,
            )
        }
    }
}

// ------------------------------------------------------------------ account ---

@Composable
internal fun AccountPage(
    user: Session?,
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onClearMessages: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    SettingsPageScaffold(title = "Account", onBack = onBack) {
        item(key = "intro") {
            PageIntro(
                title = "Account",
                subtitle = "Signed in as @${user?.username ?: ""}",
            )
        }

        item(key = "security") {
            SettingsGroup("Security") {
                NavRow(
                    icon = Icons.Rounded.Lock,
                    title = "Change password",
                    subtitle = "You stay signed in on this phone",
                    onClick = onChangePassword,
                )
            }
        }

        item(key = "data") {
            SettingsGroup("Your messages") {
                NavRow(
                    icon = Icons.Rounded.DeleteSweep,
                    title = "Delete all messages",
                    subtitle = "Empties every chat for your account; contacts stay",
                    destructive = true,
                    onClick = onClearMessages,
                )
            }
        }

        item(key = "session") {
            SettingsGroup("Session") {
                NavRow(
                    icon = Icons.Rounded.Logout,
                    title = "Sign out",
                    subtitle = "Your chats stay safely in the cloud",
                    onClick = onSignOut,
                )
                RowDivider()
                NavRow(
                    icon = Icons.Rounded.DeleteForever,
                    title = "Delete account",
                    subtitle = "Removes the account and every message on the relay",
                    destructive = true,
                    onClick = onDeleteAccount,
                )
            }
        }

        item(key = "note") {
            NoteCard(
                text = "Signing out forgets the tokens on this phone. Your chats remain in the " +
                    "cloud and come back the next time you sign in. Deleting the account removes " +
                    "them from the relay.",
                icon = Icons.Rounded.Cloud,
            )
        }
    }
}

// -------------------------------------------------------------------- about ---

@Composable
internal fun AboutPage(
    settings: AppSettings,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(title = "About", onBack = onBack) {
        item(key = "intro") {
            PageIntro(
                title = "Whatscraft",
                subtitle = "Version ${BuildConfig.VERSION_NAME} - a private messenger that " +
                    "keeps its promises simple.",
            )
        }

        item(key = "principles") {
            SettingsGroup("What it does") {
                AboutLine(
                    title = "Cloud history",
                    body = "Messages are stored in your relay and load on every device you sign in to.",
                )
                RowDivider()
                AboutLine(
                    title = "No presence",
                    body = "No online dot, no last seen, no read clock for strangers.",
                )
                RowDivider()
                AboutLine(
                    title = "Simple by design",
                    body = "No keys to manage. Your account password protects the door.",
                )
                RowDivider()
                AboutLine(
                    title = "No accounts by email",
                    body = "A username, a password, and a one-time recovery code.",
                )
            }
        }

        item(key = "build") {
            SettingsGroup("Build") {
                AboutLine(title = "Version", body = BuildConfig.VERSION_NAME)
                RowDivider()
                AboutLine(
                    title = "Relay",
                    body = settings.serverUrl.ifBlank { BuildConfig.DEFAULT_SERVER_URL },
                    monospace = true,
                )
                RowDivider()
                AboutLine(
                    title = "Source",
                    body = "github.com/brahimlakhal3/KOTLIN_WHATSCRAFT",
                    monospace = true,
                )
                RowDivider()
                AboutLine(
                    title = "Relay source",
                    body = "github.com/brahimlakhal3/SERVER_WHATSCRAFT",
                    monospace = true,
                )
            }
        }

        item(key = "footer") {
            Text(
                text = "Made with Kotlin, Compose and a stubborn belief that private " +
                    "conversation is a normal thing to want.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 26.dp),
            )
        }
    }
}

@Composable
private fun AboutLine(title: String, body: String, monospace: Boolean = false) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ------------------------------------------------------------------ shared ---

@Composable
private fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.System -> "System"
    ThemeMode.Light -> "Light"
    ThemeMode.Dark -> "Dark"
}

internal fun paletteLabel(palette: Palette): String = when (palette) {
    Palette.Monochrome -> "Monochrome"
    Palette.WhatsApp -> "WhatsApp"
    Palette.Ocean -> "Ocean"
    Palette.Berry -> "Berry"
    Palette.Sunset -> "Sunset"
    Palette.Forest -> "Forest"
    Palette.Rose -> "Rose"
    Palette.Lavender -> "Lavender"
    Palette.Sand -> "Sand"
    Palette.Midnight -> "Midnight"
}

private fun paletteShortLabel(palette: Palette): String = when (palette) {
    Palette.Monochrome -> "Mono"
    Palette.WhatsApp -> "Chat"
    else -> paletteLabel(palette)
}

internal fun paletteBlurb(palette: Palette): String = when (palette) {
    Palette.Monochrome -> "Black and white, with a soft graphite canvas at night."
    Palette.WhatsApp -> "Green accents, teal header and the familiar beige conversation."
    Palette.Ocean -> "Blue accents with a cool, quiet conversation."
    Palette.Berry -> "Deep violet accents and a soft evening canvas."
    Palette.Sunset -> "Warm orange accents for late conversations."
    Palette.Forest -> "Deep green accents, calm and grounded."
    Palette.Rose -> "Pink accents with a warm, bright conversation."
    Palette.Lavender -> "Lilac accents and a soft violet canvas."
    Palette.Sand -> "Golden taupe accents on a warm paper canvas."
    Palette.Midnight -> "Indigo accents for a dark, starry night."
}

// ------------------------------------------------------------------ dialogs ---

@Composable
internal fun ChangePasswordDialog(
    onSubmit: suspend (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
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
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
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
internal fun DeleteAccountDialog(
    onDelete: suspend (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete your account?") },
        text = {
            Column {
                Text(
                    "This removes your account and every message on the relay. " +
                        "It cannot be undone.",
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
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
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

internal fun passwordChange(current: String, next: String) =
    com.brahim.whatscraft.data.model.ChangePasswordRequest(
        currentPassword = current,
        newPassword = next,
    )
