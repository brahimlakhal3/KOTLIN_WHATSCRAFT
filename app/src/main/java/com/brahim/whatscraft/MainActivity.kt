package com.brahim.whatscraft

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brahim.whatscraft.notify.Notifications
import com.brahim.whatscraft.ui.WhatscraftRoot
import com.brahim.whatscraft.ui.theme.WhatscraftTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Set when the app is opened from a message notification. */
    private val pendingChat = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        val container = (application as WhatscraftApp).container

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            val openChat by pendingChat.collectAsStateWithLifecycle()

            WhatscraftTheme(
                darkTheme = settings.themeMode.isDark(isSystemInDarkTheme()),
                pureBlack = settings.pureBlack,
                palette = settings.palette,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WhatscraftRoot(
                        container = container,
                        pendingChatId = openChat,
                        onPendingChatHandled = { pendingChat.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val conversationId = intent?.getStringExtra(Notifications.KEY_CONVERSATION_ID) ?: return
        pendingChat.value = conversationId
    }
}
