package com.brahim.whatscraft.di

import android.content.Context
import com.brahim.whatscraft.crypto.SecretVault
import com.brahim.whatscraft.data.ApiClient
import com.brahim.whatscraft.data.AuthProvider
import com.brahim.whatscraft.data.ChatRepository
import com.brahim.whatscraft.data.DeviceStore
import com.brahim.whatscraft.data.SessionManager
import com.brahim.whatscraft.data.SessionStore
import com.brahim.whatscraft.data.SettingsStore
import com.brahim.whatscraft.data.SocketClient
import com.brahim.whatscraft.data.local.AppDatabase
import com.brahim.whatscraft.notify.Notifier
import com.brahim.whatscraft.notify.RelayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Manual dependency injection: one object graph, built once by the Application.
 *
 * No annotation processor and no reflection - everything is readable top to
 * bottom, and the singletons are simply the properties below.
 */
class Container(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Process-wide scope for work that outlives any screen. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val vault = SecretVault(appContext)
    val deviceStore = DeviceStore(appContext)
    val settings = SettingsStore(appContext)
    val sessions = SessionStore(appContext, vault)
    val notifier = Notifier(appContext)
    val database: AppDatabase = AppDatabase.build(appContext)

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        // Keeps the WebSocket warm through carrier NAT timeouts.
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val socket = SocketClient(http)

    /** One token authority for both REST calls and the websocket. */
    val auth: AuthProvider = object : AuthProvider {
        private val manager by lazy { SessionManager(api, sessions) }

        override suspend fun accessToken(): String? = manager.accessToken()

        override suspend fun refresh(usedToken: String?): Boolean = manager.refresh(usedToken)

        override suspend fun freshAccessToken(): String? = manager.freshAccessToken()
    }

    val api: ApiClient = ApiClient(http, auth = auth)

    val repository: ChatRepository = ChatRepository(
        api = api,
        auth = auth,
        socket = socket,
        database = database,
        deviceStore = deviceStore,
        sessions = sessions,
        settings = settings,
        notifier = notifier,
        scope = scope,
    )

    val isSignedIn: Boolean get() = sessions.isSignedIn

    /**
     * Brings the app online: opens the websocket, syncs, and (when the user
     * asked for it) starts the foreground service that keeps messages arriving
     * with the app closed.
     */
    fun start() {
        if (!sessions.isSignedIn) return
        repository.start()
        if (settings.current.backgroundRelay) RelayService.start(appContext)
    }

    /** Called after a successful sign-in so everything starts in the right order. */
    fun onSignedIn() {
        scope.launch {
            repository.publishDevice()
            repository.refreshAll()
        }
        repository.start()
        if (settings.current.backgroundRelay) RelayService.start(appContext)
    }

    fun onSignedOut() {
        RelayService.stop(appContext)
        notifier.cancelAll()
    }

    /** Mirrors the "keep it connected in the background" preference. */
    fun applyBackgroundPreference(enabled: Boolean) {
        if (enabled && sessions.isSignedIn) RelayService.start(appContext) else RelayService.stop(appContext)
    }
}
