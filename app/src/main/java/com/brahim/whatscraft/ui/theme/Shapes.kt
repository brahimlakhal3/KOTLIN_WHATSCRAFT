package com.brahim.whatscraft.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import com.brahim.whatscraft.data.BubbleStyle

/** Rounded but not bubbly: 28dp on cards, 20dp on fields, 14dp on chips. */
val WhatscraftShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Message bubble corners: the tail corner is always tighter. */
object BubbleShapes {
    val outgoing = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    val incoming = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
}

/** The selected bubble language, with the tail on the sender's side. */
fun bubbleShape(style: BubbleStyle, mine: Boolean): RoundedCornerShape = when (style) {
    BubbleStyle.Rounded -> if (mine) BubbleShapes.outgoing else BubbleShapes.incoming
    BubbleStyle.Soft -> RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = if (mine) 24.dp else 14.dp,
        bottomEnd = if (mine) 14.dp else 24.dp,
    )
    BubbleStyle.Square -> RoundedCornerShape(
        topStart = 10.dp,
        topEnd = 10.dp,
        bottomStart = if (mine) 10.dp else 3.dp,
        bottomEnd = if (mine) 3.dp else 10.dp,
    )
}
