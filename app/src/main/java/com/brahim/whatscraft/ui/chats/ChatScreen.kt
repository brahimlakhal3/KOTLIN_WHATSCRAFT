package com.brahim.whatscraft.ui.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brahim.whatscraft.data.model.ChatMessage
import com.brahim.whatscraft.data.model.Contact
import com.brahim.whatscraft.data.model.MessageStatus
import com.brahim.whatscraft.data.model.Reaction
import com.brahim.whatscraft.data.model.Relationship
import com.brahim.whatscraft.di.Container
import com.brahim.whatscraft.ui.components.Avatar
import com.brahim.whatscraft.ui.components.EncryptionNote
import com.brahim.whatscraft.ui.components.TypingDots
import com.brahim.whatscraft.ui.theme.BubbleShapes
import com.brahim.whatscraft.ui.theme.Motion
import com.brahim.whatscraft.ui.theme.SystemBarIcons
import com.brahim.whatscraft.ui.theme.WhatscraftColors
import com.brahim.whatscraft.util.TimeFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One conversation: the thread, the day separators, the typing signal, read
 * receipts, retry-on-tap for failed sends, and a composer that never blocks.
 *
 * Performance notes: the draft text lives inside [Composer] and the thread is
 * built once per message-list change, so typing never re-walks the history.
 * Only the newest window of messages is decrypted (see ChatRepository).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    container: Container,
    conversationId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = container.repository
    val scope = rememberCoroutineScope()

    val conversations by repository.conversations.collectAsStateWithLifecycle(initialValue = emptyList())
    val contacts by repository.contacts.collectAsStateWithLifecycle(initialValue = emptyList())
    val messages by repository.messages(conversationId).collectAsStateWithLifecycle(initialValue = emptyList())

    val summary = conversations.firstOrNull { it.id == conversationId }
    val peer = contacts.firstOrNull { it.id == summary?.peerId }

    // Rebuilt only when the messages themselves change - not per keystroke,
    // not per presence update.
    val thread = remember(messages) { buildThread(messages) }
    val waiting = remember(messages) { messages.count { it.status == MessageStatus.Queued } }

    var menuOpen by remember { mutableStateOf(false) }
    var showSafety by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var loadingMore by remember(conversationId) { mutableStateOf(false) }
    var atStart by remember(conversationId) { mutableStateOf(false) }
    var loadingHistory by remember(conversationId) { mutableStateOf(true) }

    // The first paint of a conversation lands on the newest message without
    // animating through the whole history. It happens behind the entrance
    // spinner, so the very first frame the user sees is already at the foot.
    var landing by remember(conversationId) { mutableStateOf(true) }

    // True when the chat had no messages at all when it opened: its first
    // arrival gets the pop-in, while a chat that opened with history lands
    // quietly (even though it now shows just its newest message).
    var openedEmpty by remember(conversationId) { mutableStateOf(false) }

    // The bubble that just arrived: it gets a small pop-in, but only while the
    // chat is open. Old bubbles composed by scrolling stay still.
    var freshId by remember(conversationId) { mutableStateOf<String?>(null) }
    val typingGuard = remember(conversationId) { TypingGuard() }

    val listState = rememberLazyListState()
    val nearBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }

    LaunchedEffect(conversationId) {
        repository.setActiveConversation(conversationId)
        val startedAt = System.currentTimeMillis()
        try {
            val more = runCatching { repository.ensureHistory(conversationId) }.getOrDefault(true)
            atStart = !more
        } finally {
            // A short, deliberate entrance: the spinner stays for about a
            // second even when everything is already on the phone.
            val spent = System.currentTimeMillis() - startedAt
            if (spent < ENTRY_LOADING_MS) delay(ENTRY_LOADING_MS - spent)
            // Give the collected list a beat to pick up what was just stored,
            // so the spinner hands over to messages, never to a blank frame.
            repeat(20) {
                if (messages.isNotEmpty()) return@repeat
                delay(10)
            }
            loadingHistory = false
        }
        openedEmpty = messages.isEmpty()
        // Whatever the relay collected while this phone was away (process
        // killed, phone rebooted...) lands after the screen is already up:
        // entering never waits on the network.
        runCatching { repository.syncNewer(conversationId) }
        runCatching { repository.markConversationRead(conversationId) }
    }

    DisposableEffect(conversationId) {
        onDispose {
            typingGuard.lapse?.cancel()
            if (typingGuard.announced) repository.setTyping(conversationId, false)
            repository.setActiveConversation(null)
        }
    }

    // New messages settle at the bottom when the user is already there. The
    // first paint after the spinner leaves jumps there instantly instead of
    // animating through every message - and it happens while the spinner still
    // covers the list, so the hand-over shows a thread already at its foot.
    val lastId = messages.lastOrNull()?.id
    LaunchedEffect(lastId) {
        val newest = messages.lastOrNull() ?: return@LaunchedEffect
        val bottom = lastScrollIndex(thread.size, atStart)
        if (landing) {
            runCatching { listState.scrollToItem(bottom) }
            landing = false
            // A conversation that was empty until this message still gets the
            // pop-in; a loaded history keeps landing quietly.
            if (openedEmpty) {
                freshId = newest.id
                openedEmpty = false
            }
        } else {
            freshId = newest.id
            if (nearBottom || newest.mine) {
                listState.animateScrollToItem(bottom)
            }
        }
    }

    // The pop-in lasts a moment; after that the bubble is just a bubble again.
    LaunchedEffect(freshId) {
        val id = freshId ?: return@LaunchedEffect
        delay(700)
        if (freshId == id) freshId = null
    }

    // "Load earlier" keeps its spinner up for a beat so the tap visibly does
    // something, then the older block fades in just below the row. The view
    // itself is never yanked anywhere: whoever taps the row is at the top of
    // the list, and the new page simply fills in under their eyes.
    fun loadMore() {
        if (loadingMore) return
        loadingMore = true
        scope.launch {
            val startedAt = System.currentTimeMillis()
            val more = runCatching { repository.loadMore(conversationId) }.getOrDefault(false)
            val spent = System.currentTimeMillis() - startedAt
            if (spent < MORE_LOADING_MS) delay(MORE_LOADING_MS - spent)
            if (!more) atStart = true
            loadingMore = false
        }
    }

    // Typing is announced once, then left to lapse; the guard is a plain
    // holder so keystrokes never recompose the screen.
    fun onDraftChanged(value: String) {
        if (value.isBlank()) {
            typingGuard.lapse?.cancel()
            typingGuard.lapse = null
            if (typingGuard.announced) {
                typingGuard.announced = false
                repository.setTyping(conversationId, false)
            }
            return
        }
        if (!typingGuard.announced) {
            typingGuard.announced = true
            repository.setTyping(conversationId, true)
        }
        typingGuard.lapse?.cancel()
        typingGuard.lapse = scope.launch {
            delay(TYPING_LAPSE_MS)
            typingGuard.announced = false
            repository.setTyping(conversationId, false)
        }
    }

    fun send(body: String) {
        if (body.isBlank()) return
        typingGuard.lapse?.cancel()
        typingGuard.lapse = null
        if (typingGuard.announced) {
            typingGuard.announced = false
            repository.setTyping(conversationId, false)
        }
        scope.launch { repository.sendText(conversationId, body) }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(
                            name = summary?.title ?: "Chat",
                            shade = summary?.shade ?: 0,
                            size = 38.dp,
                            online = summary?.online ?: false,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = summary?.title ?: "Chat",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val status = when {
                                summary?.typing == true -> "typing"
                                summary?.online == true -> "online"
                                peer?.lastSeen != null && peer.lastSeen > 0 ->
                                    TimeFormat.lastSeen(peer.lastSeen)
                                else -> "end-to-end encrypted"
                            }
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelSmall,
                                color = WhatscraftColors.onHeader.copy(alpha = 0.75f),
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Safety number") },
                            leadingIcon = { Icon(Icons.Rounded.VerifiedUser, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                showSafety = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (summary?.muted == true) "Unmute" else "Mute") },
                            leadingIcon = { Icon(Icons.Rounded.NotificationsOff, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                val muted = summary?.muted != true
                                scope.launch { repository.setConversationMuted(conversationId, muted) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove connection") },
                            leadingIcon = { Icon(Icons.Rounded.PersonRemove, contentDescription = null) },
                            enabled = summary?.peerId != null,
                            onClick = {
                                menuOpen = false
                                val peerId = summary?.peerId ?: return@DropdownMenuItem
                                scope.launch {
                                    runCatching { repository.removeContact(peerId) }
                                    onBack()
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear messages") },
                            leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                confirmClear = true
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WhatscraftColors.header,
                    titleContentColor = WhatscraftColors.onHeader,
                    navigationIconContentColor = WhatscraftColors.onHeader,
                    actionIconContentColor = WhatscraftColors.onHeader,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            AnimatedVisibility(
                visible = peer?.keyChanged == true,
                enter = fadeIn(tween(Motion.QuickMillis)) + slideInVertically(),
                exit = fadeOut(tween(Motion.QuickMillis)),
            ) {
                KeyChangedBanner(onReview = { showSafety = true })
            }

            AnimatedVisibility(
                visible = waiting > 0,
                enter = fadeIn(tween(Motion.QuickMillis)) + slideInVertically { -it / 2 },
                exit = fadeOut(tween(Motion.QuickMillis)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (waiting == 1) {
                            "Waiting for a connection - this message sends by itself"
                        } else {
                            "Waiting for a connection - $waiting messages send by themselves"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box(Modifier.weight(1f).background(WhatscraftColors.threadBackground)) {
                if (thread.isNotEmpty()) {
                    LazyColumn(
                        state = listState,
                        // Short threads rest on the bottom like every chat app:
                        // a handful of messages should not float at the top of
                        // an empty screen.
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (!atStart) {
                            item(key = LOAD_MORE_KEY, contentType = "loadMore") {
                                LoadMoreRow(loading = loadingMore, onClick = ::loadMore)
                            }
                        }
                        items(
                            items = thread,
                            key = { item -> item.key },
                            contentType = { item -> item.contentType },
                        ) { item ->
                            when (item) {
                                is ThreadItem.Day -> DaySeparator(item.label)
                                is ThreadItem.Bubble -> MessageBubble(
                                    message = item.message,
                                    clock = item.clock,
                                    fresh = item.message.id == freshId,
                                    onRetry = { scope.launch { repository.resend(item.message.id) } },
                                    onReact = { emoji ->
                                        scope.launch {
                                            repository.react(conversationId, item.message.id, emoji)
                                        }
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                } else if (!loadingHistory) {
                    EmptyThread()
                }

                // The entrance spinner rests on top of the thread while it
                // settles: the list is already laid out behind it - parked on
                // the newest messages - so the hand-over is a plain fade, with
                // nothing jumping or hitching once the loading is over.
                ThreadOpening(visible = loadingHistory)
            }

            Composer(
                enterToSend = container.settings.current.enterToSend,
                onDraftChanged = ::onDraftChanged,
                onSend = ::send,
                onHeightChanged = {
                    // Only when the composer genuinely grows (a new line), not
                    // on every character.
                    if (thread.isNotEmpty() && nearBottom) {
                        scope.launch { listState.scrollToItem(lastScrollIndex(thread.size, atStart)) }
                    }
                },
            )
        }
    }

    if (showSafety && summary?.peerId != null) {
        SafetyNumberDialog(
            container = container,
            userId = summary.peerId,
            name = summary.title,
            onDismiss = { showSafety = false },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear this chat?") },
            text = {
                Text(
                    "Every message in this conversation will be deleted from this phone. " +
                        "The relay only holds encrypted bytes, and nothing is removed for " +
                        "the other person.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        scope.launch {
                            repository.clearConversation(conversationId)
                            // Nothing older is reachable once the watermark is
                            // set, so the "load earlier" row goes away too.
                            atStart = true
                        }
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }

    // The chat header carries colour in the WhatsApp palette; keep the status
    // bar icons readable while this screen is on top.
    SystemBarIcons(top = WhatscraftColors.header, bottom = MaterialTheme.colorScheme.background)
}

// ------------------------------------------------------------- components ---

/** Plain holder: mutating it must never schedule recomposition. */
private class TypingGuard {
    var announced = false
    var lapse: Job? = null
}

private sealed interface ThreadItem {
    val key: String
    val contentType: String

    data class Day(override val key: String, val label: String) : ThreadItem {
        override val contentType: String = "day"
    }

    data class Bubble(val message: ChatMessage, val clock: String) : ThreadItem {
        override val key: String = message.id
        override val contentType: String = "bubble"
    }
}

/** Precomputes day separators and clock strings once per change. */
private fun buildThread(messages: List<ChatMessage>): List<ThreadItem> =
    buildList(messages.size + 8) {
        messages.forEachIndexed { index, message ->
            val previous = messages.getOrNull(index - 1)
            if (previous == null || TimeFormat.daysBetween(message.createdAt, previous.createdAt) != 0L) {
                add(ThreadItem.Day("$DAY_KEY_PREFIX${message.id}", TimeFormat.dayHeader(message.createdAt)))
            }
            add(ThreadItem.Bubble(message, TimeFormat.clock(message.createdAt)))
        }
    }

/** Index of the bottom of the list, accounting for the "load more" row. */
private fun lastScrollIndex(threadSize: Int, atStart: Boolean): Int =
    (threadSize - if (atStart) 1 else 0).coerceAtLeast(0)

@Composable
private fun LoadMoreRow(loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Loading earlier messages",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            TextButton(onClick = onClick) { Text("Load earlier messages") }
        }
    }
}

@Composable
private fun Composer(
    enterToSend: Boolean,
    onDraftChanged: (String) -> Unit,
    onSend: (String) -> Unit,
    onHeightChanged: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    // The draft lives here: a keystroke recomposes this field and nothing else.
    var text by remember { mutableStateOf("") }
    var lastHeight by remember { mutableIntStateOf(0) }
    val canSend = text.isNotBlank()

    // The send button "wakes up" with a small spring as soon as there is text.
    val sendScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "send-scale",
    )

    fun submit() {
        if (text.isBlank()) return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val body = text
        text = ""
        onSend(body)
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                if (size.height != lastHeight) {
                    lastHeight = size.height
                    onHeightChanged()
                }
            },
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onDraftChanged(it)
                    },
                    placeholder = { Text("Message") },
                    shape = MaterialTheme.shapes.large,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (enterToSend) ImeAction.Default else ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = ::submit,
                    enabled = canSend,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .size(52.dp)
                        .graphicsLayer {
                            scaleX = sendScale
                            scaleY = sendScale
                        },
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    clock: String,
    fresh: Boolean,
    onRetry: () -> Unit,
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val undecryptable = message.status == MessageStatus.Undecryptable
    var menuOpen by remember(message.id) { mutableStateOf(false) }

    // A just-sent or just-received bubble pops in from its own corner. Bubbles
    // that merely scroll into view are already at rest. The flag arrives one
    // frame after the item is composed, so the animation starts here.
    val entrance = remember(message.id) { Animatable(1f) }
    LaunchedEffect(message.id, fresh) {
        if (fresh) {
            entrance.snapTo(0f)
            entrance.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.62f, stiffness = 460f),
            )
        }
    }

    val currentStatus by rememberUpdatedState(message.status)
    val heartMine by rememberUpdatedState(
        message.reactions.any { it.emoji == HEART_REACTION && it.mine },
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                val progress = entrance.value
                alpha = progress
                translationY = (1f - progress) * 20.dp.toPx()
                scaleX = 0.94f + 0.06f * progress
                scaleY = scaleX
                transformOrigin = TransformOrigin(if (message.mine) 0.85f else 0.15f, 1f)
            },
        ) {
            Column(horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start) {
                Surface(
                    color = when {
                        undecryptable -> MaterialTheme.colorScheme.surfaceVariant
                        message.mine -> WhatscraftColors.bubbleOutgoing
                        else -> WhatscraftColors.bubbleIncoming
                    },
                    shape = if (message.mine) BubbleShapes.outgoing else BubbleShapes.incoming,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .pointerInput(message.id) {
                            detectTapGestures(
                                onTap = {
                                    if (
                                        currentStatus == MessageStatus.Failed ||
                                        currentStatus == MessageStatus.Queued
                                    ) {
                                        onRetry()
                                    }
                                },
                                onDoubleTap = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onReact(if (heartMine) "" else HEART_REACTION)
                                },
                                onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuOpen = true
                                },
                            )
                        },
                ) {
                    // The clock rides at the end of the text, WhatsApp-style: an
                    // invisible inline slot reserves room on the last line, and
                    // the visible clock is drawn over it in the bottom corner.
                    val metaInline = remember(message.mine) {
                        mapOf(
                            META_SLOT to InlineTextContent(
                                Placeholder(
                                    width = if (message.mine) 62.sp else 46.sp,
                                    height = 16.sp,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom,
                                ),
                            ) { },
                        )
                    }
                    val annotated = remember(message.text) {
                        buildAnnotatedString {
                            append(message.text)
                            append("  ")
                            appendInlineContent(META_SLOT, "time")
                        }
                    }
                    Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(
                            text = annotated,
                            inlineContent = metaInline,
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = if (undecryptable) FontStyle.Italic else FontStyle.Normal,
                            color = when {
                                undecryptable -> MaterialTheme.colorScheme.onSurfaceVariant
                                message.mine -> WhatscraftColors.onBubbleOutgoing
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 1.dp),
                        ) {
                            Text(
                                text = clock,
                                style = MaterialTheme.typography.labelSmall,
                                color = bubbleSubtitleColor(message),
                            )
                            if (message.mine) {
                                MessageStatusIcon(
                                    status = message.status,
                                    color = bubbleSubtitleColor(message),
                                    readColor = WhatscraftColors.tickRead,
                                )
                            }
                        }
                    }
                }

                if (message.reactions.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    ReactionChips(
                        reactions = message.reactions,
                        onToggle = { reaction ->
                            onReact(if (reaction.mine) "" else reaction.emoji)
                        },
                    )
                }
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Row(Modifier.padding(horizontal = 6.dp)) {
                    QUICK_REACTIONS.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    menuOpen = false
                                    val mine = message.reactions.any { it.emoji == emoji && it.mine }
                                    onReact(if (mine) "" else emoji)
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                        ) {
                            Text(text = emoji, fontSize = 22.sp)
                        }
                    }
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Copy text") },
                    enabled = !undecryptable,
                    onClick = {
                        menuOpen = false
                        clipboard.setText(AnnotatedString(message.text))
                    },
                )
            }
        }
    }
}

@Composable
private fun ReactionChips(reactions: List<Reaction>, onToggle: (Reaction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        reactions.forEach { reaction ->
            Surface(
                shape = CircleShape,
                color = if (reaction.mine) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                ),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onToggle(reaction) },
            ) {
                Text(
                    text = if (reaction.count > 1) {
                        "${reaction.emoji} ${reaction.count}"
                    } else {
                        reaction.emoji
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (reaction.mine) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}

private const val META_SLOT = "meta"
private const val HEART_REACTION = "❤️"
private val QUICK_REACTIONS = listOf("❤️", "😂", "👍", "😮", "😢", "🙏")

@Composable
private fun bubbleSubtitleColor(message: ChatMessage): Color = when {
    message.status == MessageStatus.Undecryptable -> MaterialTheme.colorScheme.onSurfaceVariant
    message.mine -> WhatscraftColors.onBubbleOutgoing.copy(alpha = 0.7f)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun MessageStatusIcon(status: MessageStatus, color: Color, readColor: Color) {
    when (status) {
        MessageStatus.Sending -> Icon(
            Icons.Rounded.Schedule,
            contentDescription = "Sending",
            tint = color,
            modifier = Modifier.size(12.dp),
        )

        MessageStatus.Queued -> Icon(
            Icons.Rounded.Schedule,
            contentDescription = "Waiting for connection - tap the message to retry",
            tint = color,
            modifier = Modifier.size(14.dp),
        )

        MessageStatus.Sent -> Icon(
            Icons.Rounded.Check,
            contentDescription = "Sent",
            tint = color,
            modifier = Modifier.size(13.dp),
        )

        MessageStatus.Read -> Icon(
            Icons.Rounded.DoneAll,
            contentDescription = "Read",
            tint = readColor,
            modifier = Modifier.size(13.dp),
        )

        MessageStatus.Failed -> Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = "Tap the message to retry",
            tint = color,
            modifier = Modifier.size(15.dp),
        )

        MessageStatus.Undecryptable -> Icon(
            Icons.Rounded.Lock,
            contentDescription = "Encrypted",
            tint = color,
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun DaySeparator(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = WhatscraftColors.threadLabel, shape = CircleShape) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ThreadOpening(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = EnterTransition.None,
        exit = fadeOut(tween(THREAD_FADE_MS)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(WhatscraftColors.threadBackground),
        ) {
            LoadingThread()
        }
    }
}

@Composable
private fun LoadingThread() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Opening the conversation",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyThread() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Say hello", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "This conversation is end-to-end encrypted. Messages are sealed on " +
                "this phone and only the two of you hold the keys.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KeyChangedBanner(onReview: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Their security code changed",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReview) { Text("Verify") }
        }
    }
}

@Composable
private fun SafetyNumberDialog(
    container: Container,
    userId: String,
    name: String,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var number by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        number = runCatching { container.repository.safetyNumber(userId) }.getOrNull()
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Safety number with $name") },
        text = {
            Column {
                Text(
                    text = "Read these digits out loud, or compare them in person. " +
                        "If they match on both phones, nobody is in the middle of this chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                val value = number
                when {
                    loading -> Text("Working...", style = MaterialTheme.typography.bodyMedium)
                    value.isNullOrBlank() -> Text(
                        text = "Their keys have not been downloaded yet. Open a chat and try again.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        },
        confirmButton = {
            val value = number
            TextButton(
                enabled = !value.isNullOrBlank(),
                onClick = {
                    value?.let { clipboard.setText(AnnotatedString(it)) }
                    onDismiss()
                },
            ) {
                Text("Copy")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private const val TYPING_LAPSE_MS = 1_800L

/** How long the loading spinner stays when a conversation is opened. */
private const val ENTRY_LOADING_MS = 1_000L

/** How long the "load earlier" row spins before the older page settles in. */
private const val MORE_LOADING_MS = 500L

/** Cross-fade between the entrance spinner and the list behind it. */
private const val THREAD_FADE_MS = 260

/** LazyColumn keys shared by the entry and paging logic. */
private const val LOAD_MORE_KEY = "load-more"
private const val DAY_KEY_PREFIX = "day-"
