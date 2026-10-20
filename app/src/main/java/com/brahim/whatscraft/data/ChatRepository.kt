package com.brahim.whatscraft.data

import com.brahim.whatscraft.BuildConfig
import com.brahim.whatscraft.data.local.AppDatabase
import com.brahim.whatscraft.data.local.ContactEntity
import com.brahim.whatscraft.data.model.ChatMessage
import com.brahim.whatscraft.data.model.ChatSummary
import com.brahim.whatscraft.data.model.Contact
import com.brahim.whatscraft.data.model.ConversationDto
import com.brahim.whatscraft.data.model.DeviceRegistration
import com.brahim.whatscraft.data.model.DirectoryPersonDto
import com.brahim.whatscraft.data.model.FriendDto
import com.brahim.whatscraft.data.model.MessageBodyDto
import com.brahim.whatscraft.data.model.MessageDto
import com.brahim.whatscraft.data.model.MessageStatus
import com.brahim.whatscraft.data.model.PatchMeRequest
import com.brahim.whatscraft.data.model.Reaction
import com.brahim.whatscraft.data.model.Relationship
import com.brahim.whatscraft.data.model.SendMessageRequest
import com.brahim.whatscraft.data.model.ServerFrame
import com.brahim.whatscraft.data.model.UserDto
import com.brahim.whatscraft.notify.MessageNotifier
import com.brahim.whatscraft.util.IsoTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Everything the UI needs, in one place.
 *
 * Messages live in the cloud only. Opening a conversation fetches its newest
 * window from the relay, "load earlier" fetches another window, and the relay
 * stores the plain text. Nothing about a message is written to the phone:
 * signing out, reinstalling, or opening a chat on another device all show the
 * same history.
 */
class ChatRepository(
    private val api: ApiClient,
    private val auth: AuthProvider,
    private val socket: SocketClient,
    private val database: AppDatabase,
    private val deviceStore: DeviceStore,
    private val sessions: SessionStore,
    private val settings: SettingsStore,
    private val notifier: MessageNotifier,
    private val scope: CoroutineScope,
) {

    private val contactsDao = database.contacts()

    /** Conversation metadata as the relay last described it, plus live typing. */
    private data class ConversationState(
        val dtos: Map<String, ConversationDto> = emptyMap(),
        val typing: Map<String, Set<String>> = emptyMap(),
    )

    private val conversationState = MutableStateFlow(ConversationState())

    /**
     * Raw message rows for chats opened in this session, oldest first. Reaction
     * rows are kept here too; [messages] hides them and folds them onto the
     * bubble they belong to.
     */
    private val messageState = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())

    /** Whether an older page exists on the relay for each opened conversation. */
    private val historyMore = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** conversationId -> userIds currently typing. */
    private val typingState = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()

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

    @Volatile
    private var started = false

    @Volatile
    private var lastServerUrl: String? = null

    @Volatile
    private var devicePublished = false

    // ------------------------------------------------------------ observers --

    val connectionState: StateFlow<SocketClient.State> = socket.state

    val contacts: Flow<List<Contact>> =
        contactsDao.observeAll()
            .map { rows -> rows.map(::toContact) }
            .flowOn(Dispatchers.Default)

    /** Everyone on the relay, loaded when the People screen opens. */
    private val _people = MutableStateFlow<List<Contact>>(emptyList())
    val people: StateFlow<List<Contact>> = _people.asStateFlow()

    val conversations: Flow<List<ChatSummary>> =
        conversationState
            .map { state ->
                val me = sessions.current?.userId
                state.dtos.values
                    .map { dto -> dto.toSummary(state.typing[dto.id].orEmpty(), me) }
                    .sortedByDescending { it.lastMessageAt }
            }
            .flowOn(Dispatchers.Default)

    /**
     * The thread shown on screen. Rows are already plain text from the relay;
     * mapping and reaction folding happen off the main thread.
     */
    fun messages(conversationId: String): Flow<List<ChatMessage>> =
        messageState
            .map { state -> render(state[conversationId].orEmpty()) }
            .flowOn(Dispatchers.Default)

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

    // ------------------------------------------------------------ lifecycle --

    /** Wires up the socket and starts syncing. Safe to call repeatedly. */
    init {
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
        scope.launch { queueTicker() }
    }

    fun start() {
        if (started) return
        started = true

        api.baseUrl = settings.current.serverUrl.ifBlank { BuildConfig.DEFAULT_SERVER_URL }
        lastServerUrl = api.baseUrl

        socket.start(
            scope = scope,
            urlProvider = { api.webSocketUrl() },
            tokenProvider = { auth.freshAccessToken() },
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

    /** Registers this device so access tokens and push delivery keep working. */
    suspend fun publishDevice() {
        val device = deviceStore.load()
        runCatching {
            api.registerDevice(
                DeviceRegistration(
                    id = device.id,
                    name = device.name,
                    platform = "android",
                ),
            )
            devicePublished = true
        }.onFailure {
            // Background housekeeping: a relay that is waking up must not show
            // a snackbar over whatever the user is reading.
        }
    }

    suspend fun signOut() {
        val refreshToken = sessions.current?.refreshToken
        socket.stop()
        started = false
        devicePublished = false
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
        sessions.clear()
        clearCache()
        deviceStore.secureWipe()
        notifier.cancelAll()
    }

    private suspend fun clearCache() {
        contactsDao.clear()
        conversationState.value = ConversationState()
        messageState.value = emptyMap()
        historyMore.value = emptyMap()
        typingState.clear()
        synchronized(acks) { acks.clear() }
        inFlight.clear()
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
        conversationState.update { state ->
            val dto = state.dtos[conversationId] ?: return@update state
            state.copy(dtos = state.dtos + (conversationId to dto.copy(muted = muted)))
        }
    }

    // -------------------------------------------------------------- friends --

    /** Loads the whole directory; the screen filters it locally as you type. */
    suspend fun refreshPeople() {
        _people.value = api.people().map { person ->
            person.toContact()
        }
    }

    suspend fun refreshContacts() {
        val graph = api.friends()
        val now = IsoTime.now()
        val rows = mutableListOf<ContactEntity>()

        fun build(entity: FriendDto, relationship: Relationship) {
            rows += ContactEntity(
                id = entity.user.id,
                username = entity.user.username,
                displayName = entity.user.displayName,
                about = entity.user.about,
                shade = entity.user.shade,
                relationship = relationship.name,
                requestId = entity.id,
                updatedAt = now,
            )
        }

        graph.friends.forEach { build(it, Relationship.Friends) }
        graph.incoming.forEach { build(it, Relationship.PendingIncoming) }
        graph.outgoing.forEach { build(it, Relationship.PendingOutgoing) }
        contactsDao.upsert(rows)

        val liveIds = rows.map { it.id }.toSet()
        contactsDao.all().filterNot { liveIds.contains(it.id) }.forEach { contactsDao.delete(it.id) }

        // A direct chat only exists while the connection does. The relay sends
        // `conversation_removed`, but this also cleans up after a removal that
        // happened while the phone was offline.
        conversationState.update { state ->
            val stale = state.dtos.values
                .filter { it.kind != "group" }
                .filter { dto -> dto.peer(sessions.current?.userId.orEmpty())?.id?.let { it !in liveIds } == true }
                .map { it.id }
                .toSet()
            if (stale.isEmpty()) {
                state
            } else {
                state.copy(dtos = state.dtos.filterKeys { it !in stale })
            }
        }
        messageState.update { it.filterKeys { id -> conversationState.value.dtos.containsKey(id) } }
    }

    suspend fun addContact(username: String) {
        api.sendFriendRequest(username)
        refreshContacts()
    }

    suspend fun acceptContact(requestId: String) {
        api.acceptFriendRequest(requestId)
        refreshContacts()
        refreshConversations()
    }

    suspend fun declineContact(requestId: String) {
        api.declineFriendRequest(requestId)
        refreshContacts()
    }

    suspend fun removeContact(userId: String) {
        api.removeFriend(userId)
        contactsDao.delete(userId)
        val direct = conversationState.value.dtos.values
            .filter { it.kind == "direct" && it.peer(sessions.current?.userId.orEmpty())?.id == userId }
            .map { it.id }
        direct.forEach { forgetConversation(it) }
        refreshContacts()
        runCatching { refreshConversations() }
    }

    /** Looks a username up and returns whether it can be added. */
    suspend fun lookupContact(username: String) = api.lookupUser(username)

    // -------------------------------------------------------- conversations --

    suspend fun refreshConversations() {
        val remote = api.conversations()
        conversationState.update { state ->
            val merged = remote.associateBy { it.id }.mapValues { (id, dto) ->
                val pending = messageState.value[id].orEmpty()
                    .filter { it.seq == 0L && it.kind != KIND_REACTION }
                    .maxByOrNull { it.createdAt }
                if (pending != null) {
                    val preview = pending.previewDto()
                    val current = dto.lastMessage
                    if (current == null || pending.createdAt > IsoTime.parse(current.createdAt)) {
                        dto.copy(lastMessage = preview)
                    } else {
                        dto
                    }
                } else {
                    dto
                }
            }
            val keptTyping = state.typing.filterKeys { merged.containsKey(it) }
            state.copy(dtos = merged, typing = keptTyping)
        }
    }

    suspend fun openConversation(peerId: String): String {
        val dto = api.openConversation(peerId)
        putConversation(dto)
        return dto.id
    }

    suspend fun markConversationRead(conversationId: String) {
        val dto = conversationState.value.dtos[conversationId]
        val newest = maxOf(
            messageState.value[conversationId].orEmpty().maxOfOrNull { it.seq } ?: 0L,
            dto?.lastMessage?.seq ?: 0L,
            dto?.lastReadSeq ?: 0L,
        )
        if (newest <= 0) return

        val alreadyRead = dto != null && dto.unread == 0 && dto.lastReadSeq >= newest
        conversationState.update { state ->
            val current = state.dtos[conversationId] ?: return@update state
            state.copy(
                dtos = state.dtos + (
                    conversationId to current.copy(
                        unread = 0,
                        lastReadSeq = maxOf(current.lastReadSeq, newest),
                    )
                    ),
            )
        }
        notifier.cancelForConversation(conversationId)
        if (alreadyRead) return

        // Only the unread watermark moves; the relay never tells the other
        // side which message was read.
        runCatching { api.markRead(conversationId, newest) }
    }

    fun setActiveConversation(conversationId: String?) {
        _activeConversation.value = conversationId
        if (conversationId != null) {
            // Cloud-only entries always start from a fresh download. Dropping
            // the old rows now means the entrance spinner covers a blank list,
            // never stale history from a previous visit.
            messageState.update { it - conversationId }
            historyMore.update { it - conversationId }
            scope.launch { markConversationRead(conversationId) }
        }
    }

    /**
     * Fetches the newest [INITIAL_WINDOW] messages. Returns true while the relay
     * still has older pages.
     */
    suspend fun ensureHistory(conversationId: String): Boolean {
        val page = api.messages(conversationId, before = null, limit = INITIAL_WINDOW)
        val me = sessions.current?.userId
        val fetched = page.messages.map { it.toChatMessage(me) }

        // Keep anything that arrived while the request was in flight (a new
        // socket frame or a message typed just before the screen opened).
        val newestFetched = fetched.maxOfOrNull { it.seq } ?: 0L
        val localOnly = messageState.value[conversationId].orEmpty().filter { row ->
            row.seq == 0L || (row.seq > newestFetched && row.seq > 0)
        }

        putMessages(conversationId, mergeMessages(localOnly, fetched))
        historyMore.update { it + (conversationId to page.hasMore) }
        return page.hasMore
    }

    /**
     * Pulls one older page from the relay. Returns true while older messages
     * remain reachable.
     */
    suspend fun loadMore(conversationId: String): Boolean {
        val current = messageState.value[conversationId].orEmpty()
        val oldest = current.filter { it.seq > 0 }.minOfOrNull { it.seq }
        if (oldest == null) {
            historyMore.update { it + (conversationId to false) }
            return false
        }

        val page = api.messages(conversationId, before = oldest, limit = WINDOW_STEP)
        val me = sessions.current?.userId
        val fetched = page.messages.map { it.toChatMessage(me) }
        putMessages(conversationId, mergeMessages(current, fetched))
        historyMore.update { it + (conversationId to page.hasMore) }
        return page.hasMore
    }

    /**
     * Fills the gap after a reconnect without disturbing the pages already
     * loaded: fetches the newest window and merges it into whatever the open
     * conversation currently shows. This also retries an entry that failed
     * while the relay was waking up.
     */
    suspend fun syncNewer(conversationId: String) {
        val page = api.messages(conversationId, before = null, limit = INITIAL_WINDOW)
        val me = sessions.current?.userId
        val fetched = page.messages.map { it.toChatMessage(me) }
        if (fetched.isEmpty()) return

        val current = messageState.value[conversationId].orEmpty()
        putMessages(conversationId, mergeMessages(current, fetched))
        if (historyMore.value[conversationId] == null) {
            historyMore.update { it + (conversationId to page.hasMore) }
        }
        if (_activeConversation.value == conversationId) {
            runCatching { markConversationRead(conversationId) }
        }
    }

    // ----------------------------------------------------------------- clear --

    /**
     * Hides this conversation's history for this account. The relay keeps the
     * other person's copy and new messages arrive normally.
     */
    suspend fun clearConversation(conversationId: String) {
        runCatching { api.clearConversation(conversationId) }
            .onFailure { _lastError.value = it.message ?: "Could not clear this chat." }
        val newest = messageState.value[conversationId].orEmpty().maxOfOrNull { it.seq } ?: 0L
        messageState.update { it - conversationId }
        historyMore.update { it + (conversationId to false) }
        conversationState.update { state ->
            val dto = state.dtos[conversationId] ?: return@update state
            state.copy(
                dtos = state.dtos + (
                    conversationId to dto.copy(
                        lastMessage = null,
                        unread = 0,
                        lastReadSeq = maxOf(dto.lastReadSeq, newest),
                    )
                    ),
            )
        }
        notifier.cancelForConversation(conversationId)
    }

    /** Deletes every message in every chat for this account. */
    suspend fun clearAllMessages() {
        runCatching { api.clearAllMessages() }
            .onFailure { _lastError.value = it.message ?: "Could not clear your messages." }
        messageState.value = emptyMap()
        historyMore.value = emptyMap()
        conversationState.update { state ->
            state.copy(
                dtos = state.dtos.mapValues { (_, dto) ->
                    dto.copy(lastMessage = null, unread = 0)
                },
            )
        }
        conversationState.value.dtos.keys.forEach { notifier.cancelForConversation(it) }
    }

    /** Forgets a whole conversation locally (a connection was removed). */
    private fun forgetConversation(conversationId: String) {
        messageState.update { it - conversationId }
        historyMore.update { it - conversationId }
        conversationState.update { state ->
            state.copy(
                dtos = state.dtos - conversationId,
                typing = state.typing - conversationId,
            )
        }
        notifier.cancelForConversation(conversationId)
    }

    // ------------------------------------------------------------- messages --

    /** Sends a text message. The bubble appears immediately, cloud-first. */
    suspend fun sendText(conversationId: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        val me = sessions.current ?: return
        val now = IsoTime.now()
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            text = body,
            mine = true,
            senderId = me.userId,
            status = MessageStatus.Sending,
            createdAt = now,
            seq = 0,
            kind = "text",
        )
        addMessage(message)
        previewSent(message)
        transmit(conversationId, message.id, body)
    }

    /**
     * Sends a reaction. An empty [emoji] removes it. Reactions are ordinary
     * cloud rows of kind `reaction`; the thread folds them onto the target.
     */
    suspend fun react(conversationId: String, targetMessageId: String, emoji: String) {
        val me = sessions.current ?: return
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            text = "$REACTION_PREFIX|$targetMessageId|$emoji",
            mine = true,
            senderId = me.userId,
            status = MessageStatus.Sending,
            createdAt = IsoTime.now(),
            seq = 0,
            kind = KIND_REACTION,
        )
        addMessage(message)
        transmit(conversationId, message.id, message.text, kind = KIND_REACTION)
    }

    /** Re-sends a message that failed or was queued while offline. */
    suspend fun resend(messageId: String) {
        val row = findMessage(messageId) ?: return
        updateMessage(messageId) { it.copy(status = MessageStatus.Sending) }
        transmit(row.conversationId, row.id, row.text, kind = row.kind)
    }

    /**
     * Sends everything still waiting: messages written while offline and ones
     * that failed on a flaky socket. Runs on reconnect and on a slow beat.
     */
    suspend fun flushQueue() {
        if (!sessions.isSignedIn) return
        queueMutex.withLock {
            val now = System.currentTimeMillis()
            messageState.value.values
                .flatten()
                .filter { it.seq <= 0 }
                .filter { row ->
                    when (row.status) {
                        MessageStatus.Queued,
                        MessageStatus.Failed,
                        -> true

                        MessageStatus.Sending -> now - row.createdAt > SEND_ACK_TIMEOUT_MS
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
        text: String,
        kind: String = "text",
    ) {
        if (!inFlight.add(messageId)) return
        try {
            val body = MessageBodyDto(text = text)
            val ack = CompletableDeferred<Unit>()
            acks[messageId] = ack
            try {
                val sentOverSocket = socket.sendMessage(
                    id = messageId,
                    conversationId = conversationId,
                    body = body,
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
                            body = body,
                            clientId = messageId,
                        ),
                    )
                    confirmSent(conversationId, messageId, response.message.seq, response.message.createdAt)
                }
            } finally {
                acks.remove(messageId)
            }
        } catch (error: ApiException) {
            if (error.status == 0) {
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
    private fun markUndelivered(messageId: String, status: MessageStatus) {
        val row = findMessage(messageId) ?: return
        if (row.seq > 0) return
        updateMessage(messageId) { it.copy(status = status) }
    }

    private fun confirmSent(conversationId: String, messageId: String, seq: Long, createdAt: String) {
        val existing = findMessage(messageId) ?: return
        val whenSent = IsoTime.parse(createdAt).takeIf { it > 0 } ?: existing.createdAt
        val confirmed = existing.copy(
            seq = seq,
            status = MessageStatus.Sent,
            createdAt = whenSent,
        )
        updateMessage(messageId) { confirmed }
        // A reaction must never replace the conversation preview.
        if (existing.kind == KIND_REACTION) return
        conversationState.update { state ->
            val dto = state.dtos[conversationId] ?: return@update state
            state.copy(
                dtos = state.dtos + (
                    conversationId to dto.copy(
                        lastMessage = MessageDto(
                            id = messageId,
                            conversationId = conversationId,
                            senderId = existing.senderId,
                            seq = seq,
                            kind = existing.kind,
                            body = MessageBodyDto(existing.text),
                            createdAt = IsoTime.format(whenSent),
                        ),
                    )
                    ),
            )
        }
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
            delay(1_500)
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
        val snapshot = typingState.mapValues { (_, users) -> users.keys.toSet() }
        conversationState.update { state -> state.copy(typing = snapshot) }
    }

    // ------------------------------------------------------- frame handling --

    private suspend fun onFrame(frame: ServerFrame) {
        when (frame) {
            is ServerFrame.Ready -> {
                if (sessions.isSignedIn) {
                    refreshAll()
                    // If the relay was asleep when a chat opened, its fetch
                    // failed and that chat is still empty. Fill it in now.
                    _activeConversation.value?.let { id ->
                        runCatching { syncNewer(id) }
                    }
                }
            }

            is ServerFrame.IncomingMessage -> handleIncoming(frame.conversationId, frame.message)

            is ServerFrame.Ack -> {
                acks.remove(frame.id)?.complete(Unit)
                confirmSent(frame.conversationId, frame.id, frame.seq, frame.createdAt)
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

            is ServerFrame.Read -> Unit

            is ServerFrame.Presence -> Unit

            is ServerFrame.ConversationChanged -> {
                putConversation(frame.conversation)
                if (_activeConversation.value == frame.conversation.id) {
                    markConversationRead(frame.conversation.id)
                }
            }

            is ServerFrame.ConversationRemoved -> {
                forgetConversation(frame.conversationId)
            }

            is ServerFrame.Friend -> {
                runCatching { refreshContacts() }
                runCatching { refreshConversations() }
                frame.user?.let { user ->
                    notifier.onFriendEvent(frame.kind, user.displayName.ifBlank { user.username })
                }
            }

            is ServerFrame.Error -> handleServerError(frame)

            is ServerFrame.Pong -> Unit
        }
    }

    /**
     * The relay's `err` frames cover everything from a rejected handshake to a
     * refused message. Background noise stays silent: auth is renewed on its
     * own, and a refused send fails only that bubble.
     */
    private fun handleServerError(frame: ServerFrame.Error) {
        when {
            frame.code == "unauthorized" || frame.code == "auth_required" -> {
                scope.launch {
                    runCatching { auth.refresh(usedToken = sessions.current?.accessToken) }
                    socket.reconnectNow()
                }
            }

            frame.clientId != null -> {
                val rejection = ApiException(frame.code, frame.message, 400)
                val waiting = acks.remove(frame.clientId)
                if (waiting != null) {
                    waiting.completeExceptionally(rejection)
                } else {
                    markUndelivered(frame.clientId, MessageStatus.Failed)
                    _lastError.value = frame.message
                }
            }

            else -> Unit
        }
    }

    private suspend fun handleIncoming(conversationId: String, dto: MessageDto) {
        val me = sessions.current?.userId ?: return
        val mine = dto.senderId == me
        val row = dto.toChatMessage(me)
        val existing = findMessage(dto.id)

        // Keep an open thread current. For chats not open in this session the
        // message stays in the cloud; opening the chat fetches it again.
        if (existing != null || _activeConversation.value == conversationId || messageState.value.containsKey(conversationId)) {
            upsertMessage(row)
        }

        // A reaction has no preview, unread count or notification; it only
        // repaints the bubble it belongs to.
        if (dto.kind == KIND_REACTION) return

        val conversation = conversationState.value.dtos[conversationId]
        conversationState.update { state ->
            val current = state.dtos[conversationId] ?: return@update state
            state.copy(
                dtos = state.dtos + (
                    conversationId to current.copy(
                        lastMessage = dto,
                        unread = if (!mine && existing == null) current.unread + 1 else current.unread,
                    )
                    ),
            )
        }

        if (mine) return

        if (_activeConversation.value == conversationId) {
            markConversationRead(conversationId)
            return
        }

        if (conversation == null) {
            runCatching { refreshConversations() }
        }

        val muted = conversation?.muted ?: conversationState.value.dtos[conversationId]?.muted ?: false
        if (muted || !settings.current.notificationsEnabled) return

        val sender = conversation?.members?.firstOrNull { it.id == dto.senderId }?.displayName?.takeIf { it.isNotBlank() }
            ?: contactsDao.byId(dto.senderId)?.displayName?.takeIf { it.isNotBlank() }
            ?: contactsDao.byId(dto.senderId)?.username
            ?: "New message"
        val preview = dto.body.text
        notifier.onMessage(
            conversationId = conversationId,
            title = sender,
            body = preview?.takeIf { it.isNotBlank() } ?: "New message",
            showPreview = settings.current.showMessagePreview,
            messageId = dto.id,
        )
    }

    // -------------------------------------------------------------- mapping --

    private fun MessageDto.toChatMessage(me: String?): ChatMessage = ChatMessage(
        id = id,
        conversationId = conversationId,
        text = body.text ?: LEGACY_MESSAGE,
        mine = senderId == me,
        senderId = senderId,
        status = MessageStatus.Sent,
        createdAt = IsoTime.parse(createdAt),
        seq = seq,
        kind = kind,
    )

    private fun ConversationDto.toSummary(typingUsers: Set<String>, me: String?): ChatSummary {
        val peer = peer(me.orEmpty())
        return ChatSummary(
            id = id,
            title = displayTitle(me.orEmpty()).ifBlank { "Chat" },
            subtitle = lastMessage?.let(::previewText),
            shade = peer?.shade ?: 0,
            unread = unread,
            muted = muted,
            isGroup = kind == "group",
            peerId = peer?.id,
            lastMessageAt = IsoTime.parse(lastMessage?.createdAt ?: createdAt),
            lastMessageMine = lastMessage?.senderId != null && lastMessage.senderId == me,
            typing = typingUsers.isNotEmpty(),
        )
    }

    private fun previewText(message: MessageDto): String =
        message.body.text?.takeIf { it.isNotBlank() } ?: "Message from an older version"

    /**
     * Rows sorted oldest first, reactions last. Pending rows have seq 0, so
     * they always sit at the end.
     */
    private fun sortMessages(rows: List<ChatMessage>): List<ChatMessage> =
        rows.sortedWith(
            compareBy<ChatMessage>(
                { if (it.seq == 0L) Long.MAX_VALUE else it.seq },
                { it.createdAt },
            ),
        )

    private fun mergeMessages(existing: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> {
        val byId = LinkedHashMap<String, ChatMessage>(existing.size + incoming.size)
        existing.forEach { byId[it.id] = it }
        incoming.forEach { message ->
            val old = byId[message.id]
            byId[message.id] = if (old != null && old.seq > 0 && message.seq == 0L) old else message
        }
        return sortMessages(byId.values.toList())
    }

    private fun render(rows: List<ChatMessage>): List<ChatMessage> {
        if (rows.isEmpty()) return emptyList()
        val visible = rows.filter { it.kind != KIND_REACTION }
        if (visible.isEmpty()) return emptyList()
        val visibleIds = visible.mapTo(HashSet(visible.size)) { it.id }

        // target -> sender -> emoji (insertion order keeps chips stable).
        val latest = LinkedHashMap<String, LinkedHashMap<String, String>>()
        rows.forEach { row ->
            if (row.kind != KIND_REACTION) return@forEach
            val (target, emoji) = parseReaction(row.text) ?: return@forEach
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
                if (sender == sessions.current?.userId) tally[1] = 1
            }
            reactionsByTarget[target] = byEmoji.map { (emoji, tally) ->
                Reaction(emoji = emoji, count = tally[0], mine = tally[1] == 1)
            }
        }

        return visible.map { row -> row.copy(reactions = reactionsByTarget[row.id].orEmpty()) }
    }

    /** `react|<targetMessageId>|<emoji>`; empty emoji removes the reaction. */
    private fun parseReaction(payload: String): Pair<String, String>? {
        if (!payload.startsWith("$REACTION_PREFIX|")) return null
        val parts = payload.split('|', limit = 3)
        if (parts.size != 3) return null
        return parts[1] to parts[2]
    }

    private fun ChatMessage.previewDto(): MessageDto = MessageDto(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        seq = seq,
        kind = kind,
        body = MessageBodyDto(text),
        createdAt = IsoTime.format(createdAt),
    )

    private fun putConversation(dto: ConversationDto) {
        conversationState.update { state ->
            state.copy(dtos = state.dtos + (dto.id to dto))
        }
    }

    private fun putMessages(conversationId: String, rows: List<ChatMessage>) {
        messageState.update { it + (conversationId to sortMessages(rows)) }
    }

    private fun addMessage(message: ChatMessage) {
        messageState.update { state ->
            val rows = (state[message.conversationId].orEmpty().filterNot { it.id == message.id } + message)
            state + (message.conversationId to sortMessages(rows))
        }
    }

    private fun upsertMessage(message: ChatMessage) {
        addMessage(message)
    }

    private fun updateMessage(messageId: String, transform: (ChatMessage) -> ChatMessage) {
        messageState.update { state ->
            var changed = false
            val next = state.mapValues { (_, rows) ->
                rows.map { row ->
                    if (row.id == messageId) {
                        changed = true
                        transform(row)
                    } else {
                        row
                    }
                }
            }
            if (changed) next else state
        }
    }

    private fun findMessage(messageId: String): ChatMessage? =
        messageState.value.values.firstNotNullOfOrNull { rows -> rows.firstOrNull { it.id == messageId } }

    /** Puts the optimistic bubble at the top of the chat list immediately. */
    private fun previewSent(message: ChatMessage) {
        conversationState.update { state ->
            val dto = state.dtos[message.conversationId] ?: return@update state
            state.copy(
                dtos = state.dtos + (
                    message.conversationId to dto.copy(lastMessage = message.previewDto())
                    ),
            )
        }
    }

    private fun toContact(row: ContactEntity) = Contact(
        id = row.id,
        username = row.username,
        displayName = row.displayName,
        about = row.about,
        shade = row.shade,
        relationship = Relationship.entries.firstOrNull { it.name == row.relationship } ?: Relationship.None,
        requestId = row.requestId,
    )

    private fun DirectoryPersonDto.toContact() = Contact(
        id = user.id,
        username = user.username,
        displayName = user.displayName,
        about = user.about,
        shade = user.shade,
        relationship = when (relationship) {
            "friends" -> Relationship.Friends
            "pending_outgoing" -> Relationship.PendingOutgoing
            "pending_incoming" -> Relationship.PendingIncoming
            else -> Relationship.None
        },
        requestId = requestId,
    )

    private companion object {
        const val TYPING_TTL_MS = 5_000L

        /**
         * Messages fetched when a conversation is opened - always the newest
         * ones, with the view at the bottom - and per "load earlier" tap:
         * 30, then 60, then 90...
         */
        const val INITIAL_WINDOW = 30
        const val WINDOW_STEP = 30

        /** How long a socket send waits for the relay's ack before queuing. */
        const val SEND_ACK_TIMEOUT_MS = 12_000L

        /** Background retry beat for messages waiting on a connection. */
        const val QUEUE_RETRY_MS = 20_000L

        const val LEGACY_MESSAGE = "This message was sent by an older version of the app"

        /** Rows with this kind are reactions, never bubbles. */
        const val KIND_REACTION = "reaction"
        const val REACTION_PREFIX = "react"
    }
}
