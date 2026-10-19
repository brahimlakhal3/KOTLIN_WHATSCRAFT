package com.brahim.whatscraft.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brahim.whatscraft.BuildConfig
import com.brahim.whatscraft.data.ApiException
import com.brahim.whatscraft.data.model.AuthResponse
import com.brahim.whatscraft.data.model.DeviceRegistration
import com.brahim.whatscraft.data.model.LoginRequest
import com.brahim.whatscraft.data.model.RecoverRequest
import com.brahim.whatscraft.data.model.RegisterRequest
import com.brahim.whatscraft.di.Container
import com.brahim.whatscraft.ui.components.EncryptionNote
import kotlinx.coroutines.launch

private enum class AuthMode(val label: String) {
    SignIn("Sign in"),
    Register("Create account"),
    Recover("Recover account"),
}

/**
 * Everything before the app proper: sign in, create an account, recover one
 * with a recovery code, and the one-time display of that code.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
    container: Container,
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(AuthMode.SignIn) }
    var freshAccount by remember { mutableStateOf<AuthResponse?>(null) }

    // The sign-in calls go straight to the relay, so the address has to be
    // right before the user types anything.
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    LaunchedEffect(settings.serverUrl) {
        container.api.baseUrl = settings.serverUrl.ifBlank { BuildConfig.DEFAULT_SERVER_URL }
    }

    val pending = freshAccount
    if (pending != null) {
        RecoveryCodeStep(
            container = container,
            auth = pending,
            onDone = onAuthenticated,
            modifier = modifier,
        )
        return
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            // The page is a single, non-scrolling column: everything fits. Only
            // while the keyboard is up does it become scrollable, so no field
            // can ever hide behind the IME.
            val imeVisible = WindowInsets.isImeVisible
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .then(if (imeVisible) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 26.dp),
            ) {
                Spacer(Modifier.weight(1f))

                if (mode == AuthMode.SignIn) {
                    BrandHeader(container = container)
                    Spacer(Modifier.height(30.dp))
                }

                AnimatedContent(
                    targetState = mode,
                    transitionSpec = {
                        (fadeIn(tween(220))).togetherWith(fadeOut(tween(120)))
                    },
                    label = "auth-mode",
                ) { current ->
                    when (current) {
                        AuthMode.SignIn -> SignInForm(
                            container = container,
                            onAuthenticated = onAuthenticated,
                            onRegister = { mode = AuthMode.Register },
                            onRecover = { mode = AuthMode.Recover },
                        )

                        AuthMode.Register -> RegisterForm(
                            container = container,
                            onCreated = { freshAccount = it },
                            onBack = { mode = AuthMode.SignIn },
                        )

                        AuthMode.Recover -> RecoverForm(
                            container = container,
                            onRecovered = onAuthenticated,
                            onBack = { mode = AuthMode.SignIn },
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(18.dp))
                EncryptionNote(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Your keys never leave this phone",
                )
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun BrandHeader(container: Container) {
    Column {
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text("Whatscraft", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Text only, end-to-end encrypted, just your friends.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        ServerRow(container = container)
    }
}

// ---------------------------------------------------------------- sign in ---

@Composable
private fun SignInForm(
    container: Container,
    onAuthenticated: () -> Unit,
    onRegister: () -> Unit,
    onRecover: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy) return
        error = null
        busy = true
        scope.launch {
            try {
                val auth = container.api.login(
                    LoginRequest(
                        username = username.trim().lowercase(),
                        password = password,
                        device = container.deviceRegistration(),
                    ),
                )
                container.sessions.save(auth, awaitingRecoveryCode = false)
                container.onSignedIn()
                onAuthenticated()
            } catch (failure: Exception) {
                error = failure.friendly()
            } finally {
                busy = false
            }
        }
    }

    Column {
        AuthField(
            value = username,
            onValueChange = { username = it.filter { char -> !char.isWhitespace() } },
            label = "Username",
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(14.dp))
        PasswordField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            onDone = ::submit,
        )
        ErrorLine(error)
        Spacer(Modifier.height(22.dp))
        PrimaryButton(text = "Sign in", busy = busy, onClick = ::submit)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onRegister) { Text("Create account") }
            TextButton(onClick = onRecover) { Text("Forgot password") }
        }
    }
}

// --------------------------------------------------------------- register ---

@Composable
private fun RegisterForm(
    container: Container,
    onCreated: (AuthResponse) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var friendsCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy) return
        val clean = username.trim().lowercase()
        error = when {
            !clean.matches(Regex("^[a-z0-9][a-z0-9_.]{2,23}$")) ->
                "Usernames use 3-24 characters: a-z, 0-9, dot or underscore."
            password.length < MIN_PASSWORD -> "Passwords need at least $MIN_PASSWORD characters."
            password != confirm -> "The two passwords do not match."
            else -> null
        }
        if (error != null) return

        busy = true
        scope.launch {
            try {
                val auth = container.api.register(
                    RegisterRequest(
                        username = clean,
                        displayName = displayName.trim().ifBlank { clean },
                        password = password,
                        device = container.deviceRegistration(),
                        registerCode = friendsCode.trim().ifBlank { null },
                    ),
                )
                onCreated(auth)
            } catch (failure: Exception) {
                error = failure.friendly()
            } finally {
                busy = false
            }
        }
    }

    Column {
        BackRow(onBack)
        AuthField(
            value = username,
            onValueChange = { username = it.filter { char -> !char.isWhitespace() } },
            label = "Username",
            placeholder = "your friends find you by this",
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(12.dp))
        AuthField(
            value = displayName,
            onValueChange = { displayName = it },
            label = "Display name",
            placeholder = "optional",
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(12.dp))
        AuthField(
            value = friendsCode,
            onValueChange = { friendsCode = it.filter { char -> !char.isWhitespace() } },
            label = "Friends code",
            placeholder = "only if invite-only",
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(12.dp))
        PasswordField(password, { password = it }, "Password", ImeAction.Next)
        Spacer(Modifier.height(12.dp))
        PasswordField(confirm, { confirm = it }, "Repeat password", ImeAction.Done, ::submit)
        ErrorLine(error)
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = "Create account", busy = busy, onClick = ::submit)
    }
}

// ---------------------------------------------------------------- recover ---

@Composable
private fun RecoverForm(
    container: Container,
    onRecovered: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy) return
        error = when {
            code.filter { it.isLetterOrDigit() }.length < 8 -> "Enter the recovery code you saved."
            password.length < MIN_PASSWORD -> "Passwords need at least $MIN_PASSWORD characters."
            else -> null
        }
        if (error != null) return

        busy = true
        scope.launch {
            try {
                container.api.recover(
                    RecoverRequest(
                        username = username.trim().lowercase(),
                        recoveryCode = code.trim(),
                        newPassword = password,
                    ),
                )
                val auth = container.api.login(
                    LoginRequest(
                        username = username.trim().lowercase(),
                        password = password,
                        device = container.deviceRegistration(),
                    ),
                )
                container.sessions.save(auth, awaitingRecoveryCode = false)
                container.onSignedIn()
                onRecovered()
            } catch (failure: Exception) {
                error = failure.friendly()
            } finally {
                busy = false
            }
        }
    }

    Column {
        BackRow(onBack)
        Text(
            "Use the one-time code you were given when you created the account. " +
                "Signing back in with it also gives you a fresh code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        AuthField(username, { username = it.filter { char -> !char.isWhitespace() } }, "Username", imeAction = ImeAction.Next)
        Spacer(Modifier.height(14.dp))
        AuthField(
            value = code,
            onValueChange = { code = it },
            label = "Recovery code",
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(14.dp))
        PasswordField(password, { password = it }, "New password", ImeAction.Done, ::submit)
        ErrorLine(error)
        Spacer(Modifier.height(22.dp))
        PrimaryButton(text = "Recover account", busy = busy, onClick = ::submit)
    }
}

// ----------------------------------------------------------- recovery code --

@Composable
private fun RecoveryCodeStep(
    container: Container,
    auth: AuthResponse,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var acknowledged by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val code = auth.recoveryCode.orEmpty()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
        ) {
            Spacer(Modifier.height(56.dp))
            Text("Save your recovery code", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
            Text(
                "It is the only way back into your account if you forget your password. " +
                    "Write it down, or paste it into your password manager. It will not be shown again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(code))
                            acknowledged = true
                        },
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy code")
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Your username is @${auth.user.username}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))
            PrimaryButton(
                text = "I have saved it",
                busy = busy,
                enabled = acknowledged,
                onClick = {
                    busy = true
                    scope.launch {
                        container.sessions.save(auth, awaitingRecoveryCode = false)
                        container.onSignedIn()
                        busy = false
                        onDone()
                    }
                },
            )
            if (!acknowledged) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Copy the code to continue.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ----------------------------------------------------------------- shared ---

private const val MIN_PASSWORD = 8

/**
 * Where the app looks for its relay. Shown before sign-in because a fresh
 * install has nothing else to go on.
 */
@Composable
private fun ServerRow(container: Container) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }

    val address = settings.serverUrl.ifBlank { BuildConfig.DEFAULT_SERVER_URL }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { editing = true },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(
                Icons.Rounded.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Server", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("Change", style = MaterialTheme.typography.labelLarge)
        }
    }

    if (editing) ServerDialog(container = container, onDismiss = { editing = false })
}

@Composable
private fun ServerDialog(container: Container, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember {
        mutableStateOf(container.settings.current.serverUrl.ifBlank { BuildConfig.DEFAULT_SERVER_URL })
    }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Relay address") },
        text = {
            Column {
                Text(
                    "The address of your Whatscraft server, for example " +
                        "https://whatscraft-relay.fly.dev. Ask whoever set it up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Address") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                status?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val clean = url.trim().trimEnd('/')
                        container.settings.setServerUrl(clean)
                        container.api.baseUrl = clean
                        val reachable = runCatching { container.api.health() }.getOrDefault(false)
                        status = if (reachable) "Reachable - you can sign in now" else "No answer from that address"
                        busy = false
                        if (reachable) onDismiss()
                    }
                },
            ) {
                Text(if (busy) "Checking..." else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BackRow(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction = ImeAction.Done,
    onDone: (() -> Unit)? = null,
) {
    var revealed by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    imageVector = if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (revealed) "Hide password" else "Show password",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorLine(error: String?) {
    if (error.isNullOrBlank()) return
    Spacer(Modifier.height(14.dp))
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    busy: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun Container.deviceRegistration(): DeviceRegistration {
    val identity = identityStore.load()
    return DeviceRegistration(
        id = identity.deviceId,
        name = identity.deviceName,
        platform = "android",
        identityKey = identity.identityPublic,
        signingKey = identity.signingPublic,
    )
}

private fun Throwable.friendly(): String = when (this) {
    is ApiException -> message
    else -> message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
}
