package com.brahim.whatscraft.data

import android.content.Context
import androidx.datastore.preferences.core.edit
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
        dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = auth.user.id
            prefs[KEY_USERNAME] = auth.user.username
            prefs[KEY_DISPLAY_NAME] = auth.user.displayName
            prefs[KEY_ABOUT] = auth.user.about
            prefs[KEY_SHADE] = auth.user.shade.toString()
            prefs[KEY_ACCESS] = vault.sealString(auth.accessToken)
            prefs[KEY_REFRESH] = vault.sealString(auth.refreshToken)
            prefs[KEY_AWAITING_RECOVERY] = awaitingRecoveryCode.toString()
        }
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS] = vault.sealString(accessToken)
            prefs[KEY_REFRESH] = vault.sealString(refreshToken)
        }
    }

    suspend fun updateUser(user: UserDto) {
        dataStore.edit { prefs ->
            prefs[KEY_USERNAME] = user.username
            prefs[KEY_DISPLAY_NAME] = user.displayName
            prefs[KEY_ABOUT] = user.about
            prefs[KEY_SHADE] = user.shade.toString()
        }
    }

    suspend fun setAwaitingRecoveryCode(awaiting: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AWAITING_RECOVERY] = awaiting.toString() }
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.clear() }
    }

    private companion object {
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_USERNAME = stringPreferencesKey("username")
        val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        val KEY_ABOUT = stringPreferencesKey("about")
        val KEY_SHADE = stringPreferencesKey("shade")
        val KEY_ACCESS = stringPreferencesKey("access_token")
        val KEY_REFRESH = stringPreferencesKey("refresh_token")
        val KEY_AWAITING_RECOVERY = stringPreferencesKey("awaiting_recovery_code")
    }
}

/**
 * Owns the access/refresh token pair: hands out the current access token and
 * renews it (once at a time) when the relay answers 401.
 */
class SessionManager(
    private val api: ApiClient,
    private val sessions: SessionStore,
) : AuthProvider {

    private val refreshMutex = Mutex()

    override suspend fun accessToken(): String? = sessions.current?.accessToken

    override suspend fun refresh(): Boolean = refreshMutex.withLock {
        val current = sessions.current ?: return false
        return try {
            val auth = api.refreshSession(RefreshRequest(current.refreshToken))
            sessions.updateTokens(auth.accessToken, auth.refreshToken)
            true
        } catch (_: ApiException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
