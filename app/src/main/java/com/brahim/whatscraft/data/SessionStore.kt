package com.brahim.whatscraft.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.brahim.whatscraft.crypto.SecretVault
import com.brahim.whatscraft.data.model.AuthResponse
import com.brahim.whatscraft.data.model.RefreshRequest
import com.brahim.whatscraft.data.model.UserDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.sessionDataStore by preferencesDataStore(name = "whatscraft.session")

data class Session(
    val userId: String,
    val username: String,
    val displayName: String,
    val about: String,
    val shade: Int,
    val accessToken: String,
    val refreshToken: String,
    /** Wall-clock time (millis) when the access token stops working. */
    val accessExpiresAt: Long = 0,
    /** True until the user has written down their recovery code. */
    val awaitingRecoveryCode: Boolean = false,
)

/**
 * Who is signed in on this device.
 *
 * Tokens and the recovery-code flag are stored sealed by the Keystore-backed
 * [SecretVault], so a copied preferences file is useless on another device.
 */
class SessionStore(context: Context, private val vault: SecretVault) {

    private val dataStore = context.sessionDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    /** Resolves once the first read from disk has happened. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        scope.launch {
            dataStore.data.collect { prefs ->
                _session.value = runCatching {
                    val userId = prefs[KEY_USER_ID] ?: return@runCatching null
                    val access = prefs[KEY_ACCESS]?.let { vault.openString(it) } ?: return@runCatching null
                    val refresh = prefs[KEY_REFRESH]?.let { vault.openString(it) } ?: return@runCatching null
                    Session(
                        userId = userId,
                        username = prefs[KEY_USERNAME].orEmpty(),
                        displayName = prefs[KEY_DISPLAY_NAME].orEmpty(),
                        about = prefs[KEY_ABOUT].orEmpty(),
                        shade = prefs[KEY_SHADE]?.toIntOrNull() ?: 0,
                        accessToken = access,
                        refreshToken = refresh,
                        accessExpiresAt = prefs[KEY_ACCESS_EXPIRES] ?: 0,
                        awaitingRecoveryCode = prefs[KEY_AWAITING_RECOVERY] == "true",
                    )
                }.getOrNull()
                _loaded.value = true
            }
        }
    }

    val current: Session? get() = _session.value

    val isSignedIn: Boolean get() = _session.value != null

    suspend fun save(auth: AuthResponse, awaitingRecoveryCode: Boolean) {
        val expiresAt = System.currentTimeMillis() + auth.accessExpiresIn * 1_000L
        dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = auth.user.id
            prefs[KEY_USERNAME] = auth.user.username
            prefs[KEY_DISPLAY_NAME] = auth.user.displayName
            prefs[KEY_ABOUT] = auth.user.about
            prefs[KEY_SHADE] = auth.user.shade.toString()
            prefs[KEY_ACCESS] = vault.sealString(auth.accessToken)
            prefs[KEY_REFRESH] = vault.sealString(auth.refreshToken)
            prefs[KEY_ACCESS_EXPIRES] = expiresAt
            prefs[KEY_AWAITING_RECOVERY] = awaitingRecoveryCode.toString()
        }
        // The disk collector catches up in a moment; until it does, callers
        // must already see the new session and tokens.
        _session.value = Session(
            userId = auth.user.id,
            username = auth.user.username,
            displayName = auth.user.displayName,
            about = auth.user.about,
            shade = auth.user.shade,
            accessToken = auth.accessToken,
            refreshToken = auth.refreshToken,
            accessExpiresAt = expiresAt,
            awaitingRecoveryCode = awaitingRecoveryCode,
        )
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String, expiresInSeconds: Int) {
        val expiresAt = System.currentTimeMillis() + expiresInSeconds * 1_000L
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS] = vault.sealString(accessToken)
            prefs[KEY_REFRESH] = vault.sealString(refreshToken)
            prefs[KEY_ACCESS_EXPIRES] = expiresAt
        }
        // Publish the new pair before the DataStore collector wakes up: a
        // retrying request must never pick up the token it just replaced.
        _session.value = _session.value?.copy(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessExpiresAt = expiresAt,
        )
    }

    suspend fun updateUser(user: UserDto) {
        dataStore.edit { prefs ->
            prefs[KEY_USERNAME] = user.username
            prefs[KEY_DISPLAY_NAME] = user.displayName
            prefs[KEY_ABOUT] = user.about
            prefs[KEY_SHADE] = user.shade.toString()
        }
        _session.value = _session.value?.copy(
            username = user.username,
            displayName = user.displayName,
            about = user.about,
            shade = user.shade,
        )
    }

    suspend fun setAwaitingRecoveryCode(awaiting: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AWAITING_RECOVERY] = awaiting.toString() }
        _session.value = _session.value?.copy(awaitingRecoveryCode = awaiting)
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.clear() }
        _session.value = null
    }

    private companion object {
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        val KEY_ABOUT = stringPreferencesKey("about")
        val KEY_SHADE = stringPreferencesKey("shade")
        val KEY_ACCESS = stringPreferencesKey("access_token")
        val KEY_REFRESH = stringPreferencesKey("refresh_token")
        val KEY_ACCESS_EXPIRES = longPreferencesKey("access_expires_at")
        val KEY_AWAITING_RECOVERY = stringPreferencesKey("awaiting_recovery_code")
    }
}

/**
 * Owns the access/refresh token pair: hands out the current access token and
 * renews it (once at a time) when the relay answers 401.
 *
 * A missing session, a flaky network or a relay that is waking up must never
 * look like a signed-out user: only an explicit rejection of the refresh token
 * ends the session.
 */
class SessionManager(
    private val api: ApiClient,
    private val sessions: SessionStore,
) : AuthProvider {

    private val refreshMutex = Mutex()

    override suspend fun accessToken(): String? = sessions.current?.accessToken

    override suspend fun freshAccessToken(): String? {
        val session = sessions.current ?: return null
        val expiring = session.accessExpiresAt > 0 &&
            session.accessExpiresAt - System.currentTimeMillis() < FRESH_MARGIN_MS
        if (!expiring) return session.accessToken
        return if (refresh(usedToken = session.accessToken)) accessToken() else session.accessToken
    }

    override suspend fun refresh(usedToken: String?): Boolean = refreshMutex.withLock {
        val current = sessions.current ?: return false
        // Another caller already renewed the pair while this one waited: there
        // is nothing left to do.
        if (usedToken != null && current.accessToken != usedToken) return true

        val auth = try {
            api.refreshSession(RefreshRequest(current.refreshToken))
        } catch (rejected: ApiException) {
            // A 401 here means the refresh token itself is dead (revoked,
            // expired, account deleted). Every other failure is transient.
            if (rejected.status == 401) sessions.clear()
            return false
        } catch (_: Exception) {
            return false
        }

        sessions.updateTokens(auth.accessToken, auth.refreshToken, auth.accessExpiresIn)
        true
    }

    private companion object {
        /** Renew a little before the relay would reject the token. */
        const val FRESH_MARGIN_MS = 90_000L
    }
}
