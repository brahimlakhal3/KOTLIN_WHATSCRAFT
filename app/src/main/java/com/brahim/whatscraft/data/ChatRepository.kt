package com.brahim.whatscraft.data

import com.brahim.whatscraft.BuildConfig
import com.brahim.whatscraft.crypto.Crypto
import com.brahim.whatscraft.crypto.IdentityStore
import com.brahim.whatscraft.crypto.MessageCipher
import com.brahim.whatscraft.crypto.SecretVault
import com.brahim.whatscraft.data.local.AppDatabase
import com.brahim.whatscraft.data.local.ContactEntity
import com.brahim.whatscraft.data.local.ConversationEntity
import com.brahim.whatscraft.data.local.MessageEntity
import com.brahim.whatscraft.data.model.ChatMessage
import com.brahim.whatscraft.data.model.ChatSummary
import com.brahim.whatscraft.data.model.Contact
import com.brahim.whatscraft.data.model.ConversationDto
import com.brahim.whatscraft.data.model.DeviceRegistration
import com.brahim.whatscraft.data.model.EnvelopeDto
import com.brahim.whatscraft.data.model.FriendDto
import com.brahim.whatscraft.data.model.KeysDeviceDto
import com.brahim.whatscraft.data.model.KeysMemberDto
import com.brahim.whatscraft.data.model.MessageBodyDto
import com.brahim.whatscraft.data.model.MessageDto
import com.brahim.whatscraft.data.model.MessageStatus
import com.brahim.whatscraft.data.model.PatchMeRequest
import com.brahim.whatscraft.data.model.Reaction
import com.brahim.whatscraft.data.model.Relationship
import com.brahim.whatscraft.data.model.SendMessageRequest
import com.brahim.whatscraft.data.model.ServerFrame
import com.brahim.whatscraft.data.model.UserDto
import com.brahim.whatscraft.data.model.WrappedKeyDto
import com.brahim.whatscraft.data.model.WhatsCraftJson
import com.brahim.whatscraft.notify.MessageNotifier
import com.brahim.whatscraft.util.IsoTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Everything the UI needs, in one place.
 *
 * Responsibilities:
 *   - keeps the local cache in sync with the relay,
 *   - encrypts outgoing messages and decrypts incoming ones,
 *   - tracks presence, typing and read receipts,
 *   - raises notifications for chats that are not on screen.
 *
 * The plaintext of a message exists in only two places: the sender's phone while
 * it is being written, and this repository's memory. On disk it is sealed with
 * the Keystore key.
 */
class ChatRepository(
    private val api: ApiClient,
    private val socket: SocketClient,
    private val database: AppDatabase,
    private val identityStore: IdentityStore,
    private val vault: SecretVault,
    private val sessions: SessionStore,
    private val settings: SettingsStore,
    private val notifier: MessageNotifier,
    private val scope: CoroutineScope,
) {

    private val conversationsDao = database.conversations()
    private val messagesDao = database.messages()
    private val contactsDao = database.contacts()

    private val json = WhatsCraftJson

    /** Cached conversation directories (user -> devices -> public keys). */
    private data class Directory(val members: List<KeysMemberDto>, val fetchedAt: Long)

    private val directories = ConcurrentHashMap<String, Directory>()

    /** conversationId -> userIds currently typing. */
    private val typing = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    private val typingState = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()

    /** How many messages each conversation currently shows; grown by [loadMore]. */
    private val windowSizes = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Conversations whose relay history was pulled to its end (no older pages). */
    private val relayExhausted = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Opening a sealed string is a Keystore round trip, so the recently opened
     * plaintexts are kept: a new message must not re-open the whole window.
     * LRU, guarded by the map's own monitor.
     */
    private val plaintextCache = object : LinkedHashMap<String, Pair<String, String>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, String>>): Boolean =
            size > PLAINTEXT_CACHE_MAX
    }

    /** Socket sends wait on these until the relay acknowledges the message. */
    private val acks = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    /** One queue flush at a time: a reconnect and a retry may coincide. */
    private val queueMutex = Mutex()

    /** Message ids currently on the wire, so a retry cannot send twice. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    private val _activeConversation = MutableStateFlow<String?>(null)
    val activeConversation: StateFlow<String?> = _activeConversation.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private var started = false
    private var lastServerUrl: String? = null

    @Volatile
    private var devicePublished = false

    // ------------------------------------------------------------ observers --

    val connectionState: StateFlow<SocketClient.State> = socket.state

    val contacts: Flow<List<Contact>> =
        contactsDao.observeAll()
            .map { rows -> rows.map(::toContact) }
            .flowOn(Dispatchers.Default)

    val conversations: Flow<List<ChatSummary>> =
        combine(conversationsDao.observeAll(), typing) { rows, typingMap ->
            rows.map { row -> toSummary(row, typingMap[row.id].orEmpty()) }
        }.flowOn(Dispatchers.Default)

    /**
     * The newest slice of the conversation, oldest first. Only the visible
     * window is decrypted, and all mapping happens off the main thread so a
     * keystroke in the composer never competes with crypto.
     *
     * Reactions travel as ordinary sealed rows with `kind = "reaction"`; they
     * never become bubbles. Instead they are collapsed per sender (the newest
     * reaction wins, so removing one is just an empty emoji) and attached to
     * the message they point at.
     */
    fun messages(conversationId: String): Flow<List<ChatMessage>> =
        combine(messagesDao.observe(conversationId), windowSizes) { rows, windows ->
            val visibleRows = rows.filter { it.kind != KIND_REACTION }
            val window = (windows[conversationId] ?: INITIAL_WINDOW).coerceAtLeast(1)
            val from = (visibleRows.size - window).coerceAtLeast(0)
            val slice = visibleRows.subList(from, visibleRows.size)
            val visibleIds = slice.mapTo(HashSet(slice.size)) { it.id }
            val me = sessions.current?.userId

            // target -> sender -> emoji (insertion order is the order they were
            // first reacted, which keeps the chips stable as they update).
            val latest = LinkedHashMap<String, LinkedHashMap<String, String>>()
            rows.forEach { row ->
                if (row.kind != KIND_REACTION) return@forEach
                val reaction = parseReaction(row) ?: return@forEach
                val (target, emoji) = reaction
                if (target !in visibleIds) return@forEach
                val bySender = latest.getOrPut(target) { LinkedHashMap() }
                if (emoji.isEmpty()) bySender.remove(row.senderId) else bySender[row.senderId] = emoji
            }

            val reactionsByTarget = HashMap<String, List<Reaction>>(latest.size)
            latest.forEach { (target, bySender) ->
                val byEmoji = LinkedHashMap<String, IntArray>()
                bySender.forEach { (sender, emoji) ->
                    val tally = byEmoji.getOrPut(emoji) { intArrayOf(0, 0) }
                    tally[0] += 1
                    if (sender == me) tally[1] = 1
                }
                reactionsByTarget[target] = byEmoji.map { (emoji, tally) ->
                    Reaction(emoji = emoji, count = tally[0], mine = tally[1] == 1)
                }
            }

            slice.map { row -> toChatMessage(row, reactionsByTarget[row.id].orEmpty()) }
        }.flowOn(Dispatchers.Default)

    fun isSignedIn(): Boolean = sessions.isSignedIn

    fun currentUserId(): String? = sessions.current?.userId

    fun currentUser(): UserDto? = sessions.current?.let {
        UserDto(
            id = it.userId,
            username = it.username,
            displayName = it.displayName,
            about = it.about,
            shade = it.shade,
        )
    }

    /** This install's identity key, as published to the relay. */
    fun myIdentityKey(): String = Crypto.b64(identityStore.load().identity.publicKey)

    /**
     * The sixty-digit safety number to compare out loud with [userId]. Null until
     * their device keys have been fetched.
     */
    suspend fun safetyNumber(userId: String): String? {
        val theirKey = contactsDao.byId(userId)?.deviceKeysJson
            ?.let { encoded ->
                runCatching {
                    json.decodeFromString(ListSerializer(KeysDeviceDto.serializer()), encoded)
                }.getOrNull()
            }
            ?.firstOrNull { it.identityKey.isNotBlank() }
            ?.identityKey
            ?: return null
        return MessageCipher.safetyNumber(myIdentityKey(), theirKey)
    }

    // ------------------------------------------------------------ lifecycle --

    /** Wires up the socket and starts syncing. Safe to call repeatedly. */
    init {
        // These collectors live for the whole process. [start] runs again after
        // a sign-out/sign-in cycle, and collecting the frame flow twice would
        // process every message twice: double unread counts, double alerts.
        scope.launch {
            settings.settings.collect { updated ->
                val url = updated.serverUrl.ifBlank { BuildConfig.DEFAULT_SERVER_URL }
                api.baseUrl = url
                if (url != lastServerUrl) {
                    lastServerUrl = url
                    socket.reconnectNow()
                }
            }
        }
        scope.launch { socket.frames.collect(::onFrame) }
        scope.launch { typingTicker() }
        // Anything written while the relay was unreachable leaves on its own:
        // the moment the connection is back, and on a slow beat afterwards in
        // case a single send failed while the socket stayed up.
        scope.launch { queueTicker() }
    }

    fun start() {
        if (started) return
        started = true

        api.baseUrl = settings.current.serverUrl.ifBlank { BuildConfig.DEFAULT_SERVER_URL }
        lastServerUrl = api.baseUrl
        identityStore.load()

        socket.start(
            scope = scope,
            urlProvider = { api.webSocketUrl() },
            tokenProvider = { sessions.current?.accessToken },
        )

        scope.launch { refreshAll() }
    }

    /** Public entry point to resync everything (pull-to-refresh, reconnect...). */
    suspend fun refreshAll() {
        if (!sessions.isSignedIn) return
        _syncing.value = true
        try {
            runCatching { publishDevice() }
            runCatching { refreshContacts() }
            runCatching { refreshConversations() }
            runCatching { flushQueue() }
            runCatching { settings.markSynced() }
        } finally {
            _syncing.value = false
        }
    }

    /**
     * Announces this device's public keys so the other side can encrypt to it.
     * The relay upserts by id, so calling this on every sync is harmless.
     */
    suspend fun publishDevice() {
        val identity = identityStore.load()
        runCatching {
            api.registerDevice(
                DeviceRegistration(
                    id = identity.deviceId,
                    name = identity.deviceName,
                    platform = "android",
                    identityKey = identity.identityPublic,
                    signingKey = identity.signingPublic,
                ),
            )
            devicePublished = true
        }.onFailure { _lastError.value = it.message }
    }

    suspend fun signOut() {
        val refreshToken = sessions.current?.refreshToken
        socket.stop()
        started = false
        devicePublished = false
        directories.clear()
        if (refreshToken != null) {
            runCatching { api.logout(refreshToken) }
        }
        sessions.clear()
        clearCache()
        notifier.cancelAll()
    }

    /** Deletes the account on the relay and destroys every local secret. */
    suspend fun deleteAccount(password: String) {
        api.deleteAccount(password)
        socket.stop()
        started = false
        devicePublished = false
        directories.clear()
        sessions.clear()
        clearCache()
        identityStore.secureWipe()
        vault.destroy()
        notifier.cancelAll()
    }

    /** Forgets this device's keys and re-registers with fresh ones. */
    suspend fun regenerateIdentity() {
        identityStore.regenerate()
        directories.clear()
        devicePublished = false
        publishDevice()
    }

    private suspend fun clearCache() {
        messagesDao.clear()
        conversationsDao.clear()
        contactsDao.clear()
        synchronized(plaintextCache) { plaintextCache.clear() }
        windowSizes.value = emptyMap()
        relayExhausted.value = emptySet()
    }

    // -------------------------------------------------------------- profile --

    suspend fun updateProfile(displayName: String?, about: String?) {
        val response = api.patchMe(PatchMeRequest(displayName = displayName, about = about))
        sessions.updateUser(response.user)
    }

    suspend fun updateShade(shade: Int) {
        val response = api.patchMe(PatchMeRequest(shade = shade))
        sessions.updateUser(response.user)
    }

    suspend fun setConversationMuted(conversationId: String, muted: Boolean) {
        runCatching { api.setMuted(conversationId, muted) }
        conversationsDao.setMuted(conversationId, muted)
    }

    // -------------------------------------------------------------- friends --

    suspend fun refreshContacts() {
        val graph = api.friends()
        val now = IsoTime.now()
        val known = contactsDao.all().associateBy { it.id }
        val rows = mutableListOf<ContactEntity>()

        fun build(entity: FriendDto, relationship: Relationship) {
            val existing = known[entity.user.id]
            rows += ContactEntity(
                id = entity.user.id,
                username = entity.user.username,
                displayName = entity.user.displayName,
                about = entity.user.about,
                shade = entity.user.shade,
                lastSeen = entity.user.lastSeen ?: existing?.lastSeen,
                online = entity.online,
                relationship = relationship.name,
                requestId = entity.id,
                deviceKeysJson = existing?.deviceKeysJson,
                fingerprint = existing?.fingerprint,
                keyChanged = existing?.keyChanged ?: false,
                updatedAt = now,
            )
        }

        graph.friends.forEach { build(it, Relationship.Friends) }
        graph.incoming.forEach { build(it, Relationship.PendingIncoming) }
        graph.outgoing.forEach { build(it, Relationship.PendingOutgoing) }
        contactsDao.upsert(rows)

        // Drop contacts that no longer exist anywhere in the graph.
        val liveIds = rows.map { it.id }.toSet()
        known.keys.filterNot { liveIds.contains(it) }.forEach { contactsDao.delete(it) }

        // A direct chat only exists while the connection does. This also
        // cleans up after a removal that happened while this phone was offline
        // (the relay's `conversation_removed` frame could not be delivered).
        conversationsDao.all()
            .filter { it.kind != "group" }
            .filter { it.peerId != null && it.peerId !in liveIds }
            .forEach { forgetConversation(it.id) }
    }

    suspend fun addContact(username: String) {
        api.sendFriendRequest(username)
        refreshContacts()
    }

    suspend fun acceptContact(requestId: String) {
        val response = api.acceptFriendRequest(requestId)
        refreshContacts()
        refreshConversations()
        response.conversationId?.let { conversationId ->
            directories.remove(conversationId)
            runCatching { ensureHistory(conversationId) }
        }
    }

    suspend fun declineContact(requestId: String) {
        api.declineFriendRequest(requestId)
        refreshContacts()
    }

    suspend fun removeContact(userId: String) {
        api.removeFriend(userId)
        contactsDao.delete(userId)
        // The relay deletes the direct chat for both sides; drop it here too so
        // the list updates instantly and messages still in the outbox cannot
        // keep targeting a conversation that no longer exists.
        conversationsDao.byPeer(userId)?.let { forgetConversation(it.id) }
        refreshContacts()
        runCatching { refreshConversations() }
    }

    /** Looks a username up and returns whether it can be added. */
    suspend fun lookupContact(username: String) = api.lookupUser(username)

    // -------------------------------------------------------- conversations --

    suspend fun refreshConversations() {
        val me = sessions.current?.userId ?: return
        val remote = api.conversations()
        val existing = conversationsDao.all().associateBy { it.id }

        conversationsDao.upsert(remote.map { dto -> dto.toEntity(me = me, previous = existing[dto.id]) })

        // Conversations that vanished from the relay keep their last message but
        // lose the unread badge.
        val liveIds = remote.map { it.id }.toSet()
        existing.values
            .filterNot { liveIds.contains(it.id) }
            .filter { it.unread != 0 }
            .forEach { conversationsDao.upsert(it.copy(unread = 0, updatedAt = IsoTime.now())) }
    }

    suspend fun openConversation(peerId: String): String {
        val dto = api.openConversation(peerId)
        conversationsDao.upsert(
            dto.toEntity(me = sessions.current?.userId, previous = conversationsDao.byId(dto.id)),
        )
        directories.remove(dto.id)
        return dto.id
    }

    suspend fun markConversationRead(conversationId: String) {
        val newest = messagesDao.newestSeq(conversationId) ?: return
        if (newest <= 0) return
        conversationsDao.markRead(conversationId, newest)
        notifier.cancelForConversation(conversationId)
        if (settings.current.readReceipts) {
            socket.sendRead(conversationId, newest)
            runCatching { api.markRead(conversationId, newest) }
        }
    }

    fun setActiveConversation(conversationId: String?) {
        _activeConversation.value = conversationId
        if (conversationId != null) {
            // Every entry opens on the newest messages (INITIAL_WINDOW of
            // them) with the list at the bottom; re-opening never pays for
            // what a previous visit pulled - older pages are re-loaded with
            // the button, 30 at a time.
            windowSizes.update { it + (conversationId to INITIAL_WINDOW) }
            scope.launch { markConversationRead(conversationId) }
        }
    }

    /**
     * Seeds a conversation that has never been opened on this phone (or came
     * back from a reinstall) with its newest page. Returns true while older
     * messages are still reachable - hidden locally or waiting on the relay.
     */
    suspend fun ensureHistory(conversationId: String): Boolean {
        if (messagesDao.newest(conversationId, 1).isEmpty()) {
            val page = api.messages(conversationId, before = null, limit = HISTORY_PAGE)
            val usable = page.messages.filter { it.seq > clearedUpTo(conversationId) }
            if (usable.isEmpty()) {
                // Empty, or everything the relay holds was cleared on this
                // phone: older messages are cleared too (seq only decreases).
                markRelayEnd(conversationId, exhausted = true)
            } else {
                store(usable, conversationId)
                markRelayEnd(conversationId, exhausted = !page.hasMore)
            }
        }
        return olderReachable(conversationId)
    }

    /**
     * Pulls what arrived while this phone was away (socket down, app killed,
     * phone rebooted) so the newest bubble really is the newest. Cheap: a
     * local check first, then a walk backwards from the relay's newest page
     * until it meets rows already stored. Runs after the chat is on screen,
     * so entering never waits for it.
     */
    suspend fun syncNewer(conversationId: String) {
        val localNewest = messagesDao.newestSeq(conversationId) ?: return
        if (localNewest <= 0) return
        // The conversation preview tracks the newest seq the relay holds.
        val serverNewest = conversationsDao.byId(conversationId)?.lastMessageSeq ?: 0L
        if (serverNewest <= localNewest) return

        var before: Long? = null
        var pages = 0
        while (pages++ < MAX_CATCHUP_PAGES) {
            val page = api.messages(conversationId, before = before, limit = HISTORY_PAGE)
            if (page.messages.isEmpty()) return
            val oldest = page.messages.minOf { it.seq }
            store(page.messages, conversationId)
            // The page overlaps what we already had: the gap is closed.
            if (oldest <= localNewest || !page.hasMore) return
            before = oldest
        }
    }

    /** Pulls one older page from the relay; records when the start is reached. */
    private suspend fun loadOlder(conversationId: String) {
        val before = messagesDao.oldestSeq(conversationId)?.takeIf { it > 0 }
        val page = api.messages(conversationId, before = before, limit = HISTORY_PAGE)
        val usable = page.messages.filter { it.seq > clearedUpTo(conversationId) }
        if (usable.isEmpty()) {
            markRelayEnd(conversationId, exhausted = true)
            return
        }
        store(usable, conversationId)
        markRelayEnd(conversationId, exhausted = !page.hasMore)
    }

    /**
     * Grows the visible slice by [WINDOW_STEP]: first from the local database
     * (instant), then by pulling an older page from the relay. Returns true
     * while older messages remain reachable.
     */
    suspend fun loadMore(conversationId: String): Boolean {
        var shown = windowSizes.value[conversationId] ?: INITIAL_WINDOW
        val total = messagesDao.countVisible(conversationId)

        // The local history shrank (chat cleared): resync the window first.
        if (total < shown) {
            shown = total.coerceAtLeast(1)
            windowSizes.update { it + (conversationId to shown) }
        }

        if (shown < total) {
            windowSizes.update { it + (conversationId to (shown + WINDOW_STEP).coerceAtMost(total)) }
            return olderReachable(conversationId)
        }

        val before = total
        loadOlder(conversationId)
        val grown = messagesDao.countVisible(conversationId)
        if (grown > before) {
            // Reveal the page that just landed; anything deeper stays hidden.
            windowSizes.update { it + (conversationId to (shown + WINDOW_STEP).coerceAtMost(grown)) }
        }
        return olderReachable(conversationId)
    }

    /**
     * True while anything older than the visible window is reachable: rows
     * still hidden locally, or a relay page not pulled yet.
     */
    private suspend fun olderReachable(conversationId: String): Boolean {
        val shown = windowSizes.value[conversationId] ?: INITIAL_WINDOW
        if (messagesDao.countVisible(conversationId) > shown) return true
        return conversationId !in relayExhausted.value
    }

    private fun markRelayEnd(conversationId: String, exhausted: Boolean) {
        relayExhausted.update { ids ->
            if (exhausted) ids + conversationId else ids - conversationId
        }
    }

    /**
     * A bubble just landed in the open conversation. The window normally
     * already holds [INITIAL_WINDOW] messages, so this only matters right
     * after a clear, when fewer rows are on screen: new bubbles grow the
     * window with them instead of hiding older ones without a button.
     */
    private fun noteArrival(conversationId: String) {
        if (_activeConversation.value != conversationId) return
        windowSizes.update { windows ->
            val shown = windows[conversationId] ?: INITIAL_WINDOW
            if (shown >= INITIAL_WINDOW) windows else windows + (conversationId to (shown + 1))
        }
    }

    private suspend fun store(dtos: List<MessageDto>, conversationId: String) {
        val cleared = clearedUpTo(conversationId)
        val me = sessions.current?.userId
        val identities = runCatching { directory(conversationId) }.getOrDefault(emptyList())
        messagesDao.upsert(
            dtos.filter { it.seq > cleared }.mapNotNull { dto -> dto.toEntity(me, identities) },
        )
    }

    /** Highest seq this conversation has cleared locally (0 = nothing cleared). */
    private fun clearedUpTo(conversationId: String): Long =
        settings.current.cleared[conversationId] ?: 0L

    // ----------------------------------------------------------------- clear --

    /**
     * Deletes this conversation's messages from the phone - only from the phone.
     * The relay keeps its ciphertext, but a local watermark hides everything up
     * to the newest seq so history cannot silently come back.
     */
    suspend fun clearConversation(conversationId: String) {
        val newest = messagesDao.newestSeq(conversationId) ?: 0
        settings.markCleared(conversationId, newest)
        messagesDao.clearConversation(conversationId)
        conversationsDao.clearLastMessage(conversationId)
        windowSizes.update { it + (conversationId to INITIAL_WINDOW) }
        // Nothing older is reachable through the watermark anymore.
        markRelayEnd(conversationId, exhausted = true)
        notifier.cancelForConversation(conversationId)
    }

    /**
     * Forgets a whole conversation locally. Used when a connection is removed:
     * both phones lose the chat until a new invitation is accepted.
     */
    private suspend fun forgetConversation(conversationId: String) {
        messagesDao.clearConversation(conversationId)
        conversationsDao.delete(conversationId)
        directories.remove(conversationId)
        windowSizes.update { it - conversationId }
        relayExhausted.update { it - conversationId }
        notifier.cancelForConversation(conversationId)
        synchronized(plaintextCache) { plaintextCache.clear() }
    }

    /** Deletes every message on this phone. Contacts, keys and chats stay. */
    suspend fun clearAllMessages() {
        val conversations = conversationsDao.all()
        val markers = conversations.mapNotNull { conversation ->
            val newest = messagesDao.newestSeq(conversation.id) ?: return@mapNotNull null
            if (newest <= 0) null else conversation.id to newest
        }.toMap()

        settings.markAllCleared(markers)
        messagesDao.clear()
        conversationsDao.clearAllLastMessages()
        synchronized(plaintextCache) { plaintextCache.clear() }
        windowSizes.value = emptyMap()
        relayExhausted.value = emptySet()
        conversations.forEach { notifier.cancelForConversation(it.id) }
    }

    // ------------------------------------------------------------- messages --

    /** Encrypts and sends a text message. Returns right after the local echo. */
    suspend fun sendText(conversationId: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val me = sessions.current ?: return
        val identity = identityStore.load()

        val messageId = UUID.randomUUID().toString()
        val sealed = vault.sealString(body, messageId)
        val now = IsoTime.format(IsoTime.now())

        // 1. Local echo, so the bubble appears instantly.
        messagesDao.upsert(
            MessageEntity(
                id = messageId,
                conversationId = conversationId,
                senderId = me.userId,
                senderDeviceId = identity.deviceId,
                seq = 0,
                kind = "text",
                bodyJson = null,
                sealedText = sealed,
                createdAt = now,
                status = MessageStatus.Sending.name.lowercase(),
                mine = true,
            ),
        )
        conversationsDao.setLastMessage(
            id = conversationId,
            messageId = messageId,
            senderId = me.userId,
            sealed = sealed,
            at = now,
            seq = 0,
        )

        noteArrival(conversationId)
        transmit(conversationId, messageId, body)
    }

    /**
     * Sends a reaction to [targetMessageId]. An empty [emoji] removes the
     * reaction. Reactions are sealed and delivered like any other message, so
     * the relay never learns which message was reacted to (or with what).
     */
    suspend fun react(conversationId: String, targetMessageId: String, emoji: String) {
        val me = sessions.current ?: return
        val identity = identityStore.load()
        val messageId = UUID.randomUUID().toString()
        val payload = "$REACTION_PREFIX|$targetMessageId|$emoji"
        val sealed = vault.sealString(payload, messageId)
        val now = IsoTime.format(IsoTime.now())

        messagesDao.upsert(
            MessageEntity(
                id = messageId,
                conversationId = conversationId,
                senderId = me.userId,
                senderDeviceId = identity.deviceId,
                seq = 0,
                kind = KIND_REACTION,
                bodyJson = null,
                sealedText = sealed,
                createdAt = now,
                status = MessageStatus.Sending.name.lowercase(),
                mine = true,
            ),
        )

        transmit(conversationId, messageId, payload, kind = KIND_REACTION)
    }

    /** Re-sends a message that failed (or was queued while offline). */
    suspend fun resend(messageId: String) {
        val entity = messagesDao.byId(messageId) ?: return
        val text = entity.sealedText?.let { runCatching { vault.openString(it, messageId) }.getOrNull() } ?: return
        messagesDao.setStatus(messageId, MessageStatus.Sending.name.lowercase())
        transmit(entity.conversationId, messageId, text, kind = entity.kind)
    }

    /**
     * Sends everything still waiting for the relay: messages written while
     * offline and ones that failed on a flaky socket. Runs automatically on
     * reconnect and on a slow beat, and manually from pull-to-refresh.
     */
    suspend fun flushQueue() {
        if (!sessions.isSignedIn) return
        queueMutex.withLock {
            val now = System.currentTimeMillis()
            messagesDao.unsent()
                .filter { it.seq <= 0 }
                .filter { row ->
                    when (row.status) {
                        MessageStatus.Queued.name.lowercase(),
                        MessageStatus.Failed.name.lowercase(),
                        -> true

                        // A send that never got an ack (the process died, or the
                        // socket vanished) must not spin forever.
                        MessageStatus.Sending.name.lowercase() ->
                            now - IsoTime.parse(row.createdAt) > SEND_ACK_TIMEOUT_MS

                        else -> false
                    }
                }
                .forEach { resend(it.id) }
        }
    }

    /** Retries the offline queue by itself; the user never has to tap. */
    private suspend fun queueTicker() {
        while (true) {
            delay(QUEUE_RETRY_MS)
            if (socket.isConnected) runCatching { flushQueue() }
        }
    }

    private suspend fun transmit(
        conversationId: String,
        messageId: String,
        plaintext: String,
        kind: String = "text",
    ) {
        // A tap-to-retry and a queue flush can fire together; only one transmit
        // per message may be on the wire.
        if (!inFlight.add(messageId)) return
        val identity = identityStore.load()
        try {
            val recipients = directory(conversationId)
                .flatMap { member ->
                    member.devices
                        .filter { it.identityKey.isNotBlank() }
                        .map { MessageCipher.RecipientDevice(it.id, it.identityKey) }
                }
            check(recipients.isNotEmpty()) { "Nobody to encrypt for yet" }

            val sealed = MessageCipher.seal(
                identity = identity.identity,
                signing = identity.signing,
                conversationId = conversationId,
                messageId = messageId,
                plaintext = plaintext,
                devices = recipients,
            )

            val bodyDto = MessageBodyDto(
                alg = sealed.body.alg,
                iv = sealed.body.iv,
                ct = sealed.body.ct,
                sig = sealed.body.sig,
            )
            val envelopes = sealed.envelopes.map { envelope ->
                EnvelopeDto(
                    deviceId = envelope.deviceId,
                    wrappedKey = WrappedKeyDto(envelope.wrappedKey.iv, envelope.wrappedKey.ct),
                )
            }

            messagesDao.byId(messageId)?.let { row ->
                messagesDao.upsert(row.copy(bodyJson = json.encodeToString(MessageBodyDto.serializer(), bodyDto)))
            }

            // 2. Prefer the socket; fall back to REST when it is down. A write
            // can disappear into a socket that still looks open, so socket
            // sends wait for the relay's ack and fall into the queue on timeout.
            val ack = CompletableDeferred<Unit>()
            acks[messageId] = ack
            try {
                val sentOverSocket = socket.sendMessage(
                    id = messageId,
                    conversationId = conversationId,
                    body = bodyDto,
                    envelopes = envelopes,
                    clientId = messageId,
                    kind = kind,
                )

                if (sentOverSocket) {
                    val acknowledged = withTimeoutOrNull(SEND_ACK_TIMEOUT_MS) { ack.await() } != null
                    if (!acknowledged) throw java.io.IOException("The relay did not answer in time")
                } else {
                    val response = api.sendMessage(
                        conversationId,
                        SendMessageRequest(
                            id = messageId,
                            kind = kind,
                            body = bodyDto,
                            envelopes = envelopes,
                            clientId = messageId,
                        ),
                    )
                    confirmSent(messageId, response.message.seq, response.message.createdAt)
                }
            } finally {
                acks.remove(messageId)
            }
        } catch (error: ApiException) {
            if (error.status == 0) {
                // No reachable relay: not a failure, just a wait.
                markUndelivered(messageId, MessageStatus.Queued)
            } else {
                markUndelivered(messageId, MessageStatus.Failed)
                _lastError.value = error.message
            }
        } catch (error: java.io.IOException) {
            markUndelivered(messageId, MessageStatus.Queued)
        } catch (error: Exception) {
            markUndelivered(messageId, MessageStatus.Failed)
            _lastError.value = error.message
        } finally {
            inFlight.remove(messageId)
        }
    }

    /** Sets a status unless the relay confirmed the message mid-flight. */
    private suspend fun markUndelivered(messageId: String, status: MessageStatus) {
        val row = messagesDao.byId(messageId) ?: return
        if (row.seq > 0) return
        messagesDao.setStatus(messageId, status.name.lowercase())
    }

    private suspend fun confirmSent(messageId: String, seq: Long, createdAt: String) {
        val existing = messagesDao.byId(messageId) ?: return
        messagesDao.confirm(messageId, seq, MessageStatus.Sent.name.lowercase(), createdAt)
        // A reaction must never replace the conversation preview.
        if (existing.kind == KIND_REACTION) return
        conversationsDao.setLastMessage(
            id = existing.conversationId,
            messageId = messageId,
            senderId = existing.senderId,
            sealed = existing.sealedText,
            at = createdAt,
            seq = seq,
        )
    }

    fun clearError() {
        _lastError.value = null
    }

    // -------------------------------------------------------------- typing ---

    fun setTyping(conversationId: String, typingNow: Boolean) {
        if (!settings.current.typingIndicators) return
        socket.sendTyping(conversationId, typingNow)
    }

    private suspend fun typingTicker() {
        while (true) {
            kotlinx.coroutines.delay(1_500)
            val now = IsoTime.now()
            var changed = false
            typingState.entries.forEach { (conversationId, users) ->
                val alive = users.filterValues { it > now }
                if (alive.size != users.size) {
                    changed = true
                    typingState[conversationId] = ConcurrentHashMap(alive)
                }
            }
            if (changed) publishTyping()
        }
    }

    private fun publishTyping() {
        typing.value = typingState.mapValues { (_, users) -> users.keys.toSet() }
    }

    // ------------------------------------------------------- frame handling --

    private suspend fun onFrame(frame: ServerFrame) {
        when (frame) {
            is ServerFrame.Ready -> {
                if (sessions.isSignedIn) refreshAll()
            }

            is ServerFrame.IncomingMessage -> handleIncoming(frame.conversationId, frame.message)

            is ServerFrame.Ack -> {
                acks.remove(frame.id)?.complete(Unit)
                confirmSent(frame.id, frame.seq, frame.createdAt)
            }

            is ServerFrame.Typing -> {
                val users = typingState.getOrPut(frame.conversationId) { ConcurrentHashMap() }
                if (frame.state == "start") {
                    users[frame.userId] = IsoTime.now() + TYPING_TTL_MS
                } else {
                    users.remove(frame.userId)
                }
                publishTyping()
            }

            is ServerFrame.Read -> messagesDao.markReadUpTo(frame.conversationId, frame.seq)

            is ServerFrame.Presence -> {
                contactsDao.setPresence(frame.userId, frame.online, frame.lastSeen)
                conversationsDao.setPeerOnline(frame.userId, frame.online)
            }

            is ServerFrame.ConversationChanged -> {
                val previous = conversationsDao.byId(frame.conversation.id)
                conversationsDao.upsert(
                    frame.conversation.toEntity(sessions.current?.userId, previous),
                )
                directories.remove(frame.conversation.id)
                runCatching { refreshConversations() }
            }

            is ServerFrame.ConversationRemoved -> {
                forgetConversation(frame.conversationId)
                runCatching { refreshConversations() }
            }

            is ServerFrame.Friend -> {
                runCatching { refreshContacts() }
                runCatching { refreshConversations() }
                frame.user?.let { user ->
                    notifier.onFriendEvent(frame.kind, user.displayName.ifBlank { user.username })
                }
            }

            is ServerFrame.Error -> {
                _lastError.value = frame.message
            }

            is ServerFrame.Pong -> Unit
        }
    }

    private suspend fun handleIncoming(conversationId: String, dto: MessageDto) {
        val me = sessions.current ?: return
        // Locally cleared history must never reappear through a resync.
        if (dto.seq > 0 && dto.seq <= clearedUpTo(conversationId)) return
        val identities = runCatching { directory(conversationId) }.getOrDefault(emptyList())
        val entity = dto.toEntity(me.userId, identities) ?: return

        // A reconnect or a resync can hand us a message we already stored. The
        // row is refreshed either way, but only a first delivery may count as
        // unread or raise a notification.
        val seen = messagesDao.byId(dto.id) != null

        messagesDao.upsert(entity)

        // A reaction has no preview, no unread count and no notification; it
        // only repaints the bubble it belongs to.
        if (entity.kind == KIND_REACTION) return

        // A freshly delivered bubble belongs on screen while the chat is open.
        if (!seen) noteArrival(conversationId)

        val mine = dto.senderId == me.userId
        conversationsDao.setLastMessage(
            id = conversationId,
            messageId = dto.id,
            senderId = dto.senderId,
            sealed = entity.sealedText,
            at = dto.createdAt,
            seq = dto.seq,
        )

        if (mine || seen) return

        val conversation = conversationsDao.byId(conversationId)
        val isActive = _activeConversation.value == conversationId
        if (isActive) {
            markConversationRead(conversationId)
        } else {
            conversationsDao.setUnread(conversationId, (conversation?.unread ?: 0) + 1)
        }

        if (!isActive && !(conversation?.muted ?: false) && settings.current.notificationsEnabled) {
            val sender = identities.firstOrNull { it.userId == dto.senderId }?.displayName?.takeIf { it.isNotBlank() }
                ?: contactsDao.byId(dto.senderId)?.displayName?.takeIf { it.isNotBlank() }
                ?: contactsDao.byId(dto.senderId)?.username
                ?: "New message"
            val preview = entity.sealedText?.let {
                runCatching { vault.openString(it, dto.id) }.getOrNull()
            }
            notifier.onMessage(
                conversationId = conversationId,
                title = sender,
                body = preview ?: "New message",
                showPreview = settings.current.showMessagePreview,
                messageId = dto.id,
            )
        }
    }

    // ------------------------------------------------------------- directory --

    /**
     * The device list (with public keys) for every member of a conversation.
     * Cached briefly; refreshed on demand when a key is missing.
     */
    private suspend fun directory(conversationId: String, force: Boolean = false): List<KeysMemberDto> {
        val cached = directories[conversationId]
        if (!force && cached != null && IsoTime.now() - cached.fetchedAt < DIRECTORY_TTL_MS) {
            return cached.members
        }
        val fresh = api.conversationKeys(conversationId).members
        directories[conversationId] = Directory(fresh, IsoTime.now())
        rememberFingerprints(fresh)
        return fresh
    }

    /**
     * Trust on first use: the first identity key we ever see for a contact is
     * remembered. If it changes later the chat shows a warning until the user
     * verifies the new safety number out of band.
     */
    private suspend fun rememberFingerprints(members: List<KeysMemberDto>) {
        val me = sessions.current?.userId
        val myKey = Crypto.b64(identityStore.load().identity.publicKey)
        members.filter { it.userId != me }.forEach { member ->
            val identityKey = member.devices.firstOrNull { it.identityKey.isNotBlank() }?.identityKey
                ?: return@forEach
            val contact = contactsDao.byId(member.userId) ?: return@forEach
            val fingerprint = MessageCipher.safetyNumber(myKey, identityKey)
            val previous = contact.fingerprint
            contactsDao.upsert(
                contact.copy(
                    deviceKeysJson = json.encodeToString(ListSerializer(KeysDeviceDto.serializer()), member.devices),
                    fingerprint = previous ?: fingerprint,
                    keyChanged = previous != null && previous != fingerprint,
                    updatedAt = IsoTime.now(),
                ),
            )
        }
    }

    // -------------------------------------------------------------- mapping --

    private suspend fun MessageDto.toEntity(me: String?, members: List<KeysMemberDto>): MessageEntity? {
        val deviceId = identityStore.load().deviceId
        val mine = senderId == me
        val envelope = envelopes.firstOrNull { it.deviceId == deviceId }

        var text: String? = null
        var status = MessageStatus.Sent
        if (envelope != null) {
            val senderDevices = members.firstOrNull { it.userId == senderId }?.devices.orEmpty()
            // Prefer the device that actually sent it, then try the rest: a stale
            // directory may not know which device authored the message.
            val candidates = buildList {
                senderDeviceId?.let { id -> senderDevices.firstOrNull { it.id == id }?.let { add(it) } }
                addAll(senderDevices.filterNot { it.id == senderDeviceId })
            }
            text = openBody(candidates, envelope, conversationId, id, body)
            if (text == null) status = MessageStatus.Undecryptable
        } else if (mine) {
            // Our own message: the plaintext is already here, but we do not have
            // it in memory - re-seal it from the local copy when there is one.
            val local = messagesDao.byId(id)
            return local?.copy(seq = seq, createdAt = createdAt)
        } else {
            status = MessageStatus.Undecryptable
        }

        return MessageEntity(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            senderDeviceId = senderDeviceId,
            seq = seq,
            kind = kind,
            bodyJson = json.encodeToString(MessageDto.serializer(), this),
            sealedText = text?.let { vault.sealString(it, id) },
            createdAt = createdAt,
            status = status.name.lowercase(),
            mine = mine,
        )
    }

    /**
     * Tries every candidate device of the sender until one of them unwraps the
     * message. Returns null when none does (wrong directory, tampered message).
     */
    private fun openBody(
        candidates: List<KeysDeviceDto>,
        envelope: EnvelopeDto,
        conversationId: String,
        messageId: String,
        body: MessageBodyDto,
    ): String? {
        val identity = identityStore.load()
        candidates.forEach { device ->
            val signingKey = device.signingKey ?: return@forEach
            val attempt = runCatching {
                MessageCipher.open(
                    identity = identity.identity,
                    myDeviceId = identity.deviceId,
                    conversationId = conversationId,
                    messageId = messageId,
                    senderIdentityPublicB64 = device.identityKey,
                    senderSigningPublicB64 = signingKey,
                    body = MessageCipher.Body(body.alg, body.iv, body.ct, body.sig),
                    envelope = MessageCipher.WrappedKey(envelope.wrappedKey.iv, envelope.wrappedKey.ct),
                )
            }
            attempt.getOrNull()?.let { return it }
        }
        return null
    }

    private fun ConversationDto.toEntity(me: String?, previous: ConversationEntity?): ConversationEntity {
        val peer = peer(me.orEmpty())
        // A conversation whose newest message was cleared here keeps an empty
        // preview, even when a later sync still reports that message.
        val cleared = clearedUpTo(id)
        val lastIsCleared = lastMessage != null && lastMessage.seq <= cleared
        return ConversationEntity(
            id = id,
            kind = kind,
            title = title,
            createdAt = createdAt,
            // The local counter is the source of truth while the app runs (it is
            // incremented per incoming frame); the relay's value is used the
            // first time a conversation is seen.
            lastReadSeq = maxOf(lastReadSeq, previous?.lastReadSeq ?: 0L),
            muted = muted,
            unread = previous?.unread ?: unread,
            membersJson = json.encodeToString(ListSerializer(UserDto.serializer()), members),
            peerId = peer?.id,
            peerUsername = peer?.username,
            peerDisplayName = peer?.displayName?.takeIf { it.isNotBlank() } ?: peer?.username ?: title,
            peerShade = peer?.shade ?: 0,
            peerOnline = previous?.peerOnline ?: false,
            lastMessageId = if (lastIsCleared) null else (lastMessage?.id ?: previous?.lastMessageId),
            lastMessageSenderId = if (lastIsCleared) null else (lastMessage?.senderId ?: previous?.lastMessageSenderId),
            lastMessageSealed = if (lastIsCleared) null else previous?.lastMessageSealed,
            lastMessageAt = if (lastIsCleared) previous?.lastMessageAt else (lastMessage?.createdAt ?: previous?.lastMessageAt),
            lastMessageSeq = if (lastIsCleared) {
                previous?.lastMessageSeq ?: 0L
            } else {
                maxOf(lastMessage?.seq ?: 0L, previous?.lastMessageSeq ?: 0L)
            },
            updatedAt = IsoTime.now(),
        )
    }

    private fun toSummary(row: ConversationEntity, typingUsers: Set<String>): ChatSummary {
        val preview = row.lastMessageSealed?.let { sealed ->
            row.lastMessageId?.let { id -> runCatching { vault.openString(sealed, id) }.getOrNull() }
        }
        return ChatSummary(
            id = row.id,
            title = row.peerDisplayName?.takeIf { it.isNotBlank() } ?: row.peerUsername ?: "Chat",
            subtitle = preview,
            shade = row.peerShade,
            unread = row.unread,
            muted = row.muted,
            online = row.peerOnline,
            isGroup = row.kind == "group",
            peerId = row.peerId,
            lastMessageAt = IsoTime.parse(row.lastMessageAt),
            lastMessageMine = row.lastMessageSenderId == sessions.current?.userId,
            typing = typingUsers.isNotEmpty(),
        )
    }

    private fun toChatMessage(row: MessageEntity, reactions: List<Reaction>): ChatMessage = ChatMessage(
        id = row.id,
        conversationId = row.conversationId,
        text = plaintext(row.sealedText, row.id),
        mine = row.mine,
        senderId = row.senderId,
        status = MessageStatus.entries.firstOrNull { it.name.equals(row.status, ignoreCase = true) }
            ?: MessageStatus.Sent,
        createdAt = IsoTime.parse(row.createdAt),
        seq = row.seq,
        kind = row.kind,
        reactions = reactions,
    )

    /**
     * A reaction row stores `react|<targetMessageId>|<emoji>` as its sealed
     * plaintext. An empty emoji means "removed".
     */
    private fun parseReaction(row: MessageEntity): Pair<String, String>? {
        val payload = plaintext(row.sealedText, row.id)
        if (!payload.startsWith("$REACTION_PREFIX|")) return null
        val parts = payload.split('|', limit = 3)
        if (parts.size != 3) return null
        return parts[1] to parts[2]
    }

    /** Decrypts with a small LRU cache so repeated emissions stay cheap. */
    private fun plaintext(sealed: String?, id: String): String {
        if (sealed == null) return UNDECRYPTABLE
        synchronized(plaintextCache) {
            plaintextCache[id]?.let { (cachedSealed, text) ->
                if (cachedSealed == sealed) return text
            }
        }
        val text = runCatching { vault.openString(sealed, id) }.getOrDefault(UNDECRYPTABLE)
        synchronized(plaintextCache) { plaintextCache[id] = sealed to text }
        return text
    }

    private fun toContact(row: ContactEntity) = Contact(
        id = row.id,
        username = row.username,
        displayName = row.displayName,
        about = row.about,
        shade = row.shade,
        online = row.online,
        lastSeen = row.lastSeen?.let(IsoTime::parse),
        relationship = Relationship.entries.firstOrNull { it.name == row.relationship } ?: Relationship.None,
        requestId = row.requestId,
        fingerprint = row.fingerprint,
        keyChanged = row.keyChanged,
    )

    private companion object {
        const val DIRECTORY_TTL_MS = 10 * 60 * 1000L
        const val TYPING_TTL_MS = 5_000L
        const val HISTORY_PAGE = 40

        /**
         * Messages shown when a conversation is opened - always the newest
         * ones, with the view at the bottom - and per "load earlier" tap:
         * 30, then 60, then 90...
         */
        const val INITIAL_WINDOW = 30
        const val WINDOW_STEP = 30

        /** Pages [ChatRepository.syncNewer] may pull in one catch-up. */
        const val MAX_CATCHUP_PAGES = 25
        const val PLAINTEXT_CACHE_MAX = 256

        /** How long a socket send waits for the relay's ack before queuing. */
        const val SEND_ACK_TIMEOUT_MS = 12_000L

        /** Background retry beat for messages waiting on a connection. */
        const val QUEUE_RETRY_MS = 20_000L

        const val UNDECRYPTABLE = "This message cannot be read on this device"

        /** Rows with this kind are reactions, never bubbles. */
        const val KIND_REACTION = "reaction"
        const val REACTION_PREFIX = "react"
    }
}
