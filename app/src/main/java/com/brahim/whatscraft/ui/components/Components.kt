package com.brahim.whatscraft.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brahim.whatscraft.data.SocketClient
import com.brahim.whatscraft.ui.theme.WhatscraftColors

/**
 * The small pieces every screen is built from. Everything stays within the
 * monochrome palette: shape, weight and motion carry the meaning.
 */

@Composable
fun Avatar(
    name: String,
    shade: Int,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    val tones = WhatscraftColors.avatarTones
    val tone = tones[((shade % tones.size) + tones.size) % tones.size]
    val initials = remember(name) { initialsOf(name) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tone),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(' ', '.', '_', '-').filter { it.isNotBlank() }
    val letters = when {
        parts.isEmpty() -> name.take(1)
        parts.size == 1 -> parts[0].take(2)
        else -> parts.take(2).map { it.first() }.joinToString("")
    }
    return letters.uppercase().ifBlank { "?" }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (destructive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Three dots that breathe while the other side is typing. */
@Composable
fun TypingDots(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.5.dp)
                    .size(5.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
fun UnreadBadge(count: Int, muted: Boolean) {
    if (count <= 0) return
    val background by animateColorAsState(
        if (muted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
        label = "badge",
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp),
        )
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Quiet reassurance line used in empty chats and on the auth screen. */
@Composable
fun CloudNote(modifier: Modifier = Modifier, text: String = "Messages are stored in your relay") {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Cloud,
            contentDescription = null,
            tint = LocalContentColor.current.copy(alpha = 0.6f),
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

/** A quiet dot + label describing the websocket. */
@Composable
fun ConnectionPill(state: SocketClient.State, modifier: Modifier = Modifier) {
    val (label, tint) = when (state) {
        is SocketClient.State.Connected -> "connected" to MaterialTheme.colorScheme.primary
        is SocketClient.State.Connecting -> "connecting" to MaterialTheme.colorScheme.onSurfaceVariant
        is SocketClient.State.Reconnecting -> "reconnecting" to MaterialTheme.colorScheme.onSurfaceVariant
        is SocketClient.State.Degraded -> "offline" to MaterialTheme.colorScheme.onSurfaceVariant
        is SocketClient.State.Idle -> "offline" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun VerticalGap(height: Dp) = Spacer(Modifier.height(height))
