package com.brahim.whatscraft.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.brahim.whatscraft.data.BubbleStyle
import com.brahim.whatscraft.data.Palette

private val MonoLight = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = PaperPressed,
    onPrimaryContainer = Ink,
    inversePrimary = Chalk,

    secondary = InkSoft,
    onSecondary = Paper,
    secondaryContainer = PaperDim,
    onSecondaryContainer = Ink,

    tertiary = InkMuted,
    onTertiary = Paper,

    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = InkSoft,
    surfaceTint = Ink,

    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = PaperDim,
    surfaceContainerHigh = PaperPressed,
    surfaceContainerHighest = PaperPressed,

    inverseSurface = Ink,
    inverseOnSurface = Chalk,

    outline = LineLight,
    outlineVariant = LineFaintLight,

    // Kept monochrome on purpose; destructive actions are signalled with
    // weight, icons and copy rather than colour.
    error = Ink,
    onError = Paper,
    errorContainer = PaperPressed,
    onErrorContainer = Ink,

    scrim = Color.Black,
)

/** The default dark monochrome theme: a neutral graphite canvas. */
private val MonoDarkGray = darkColorScheme(
    primary = Chalk,
    onPrimary = Ink,
    primaryContainer = GraphitePressed,
    onPrimaryContainer = Chalk,
    inversePrimary = Ink,

    secondary = ChalkSoft,
    onSecondary = Ink,
    secondaryContainer = GraphiteRaised,
    onSecondaryContainer = Chalk,

    tertiary = ChalkMuted,
    onTertiary = Ink,

    background = Graphite,
    onBackground = Chalk,
    surface = Graphite,
    onSurface = Chalk,
    surfaceVariant = GraphiteRaised,
    onSurfaceVariant = ChalkSoft,
    surfaceTint = Chalk,

    surfaceContainerLowest = Graphite,
    surfaceContainerLow = Color(0xFF1C1C1C),
    surfaceContainer = GraphiteRaised,
    surfaceContainerHigh = Color(0xFF2A2A2A),
    surfaceContainerHighest = GraphitePressed,

    inverseSurface = Chalk,
    inverseOnSurface = Ink,

    outline = LineDark,
    outlineVariant = LineFaintDark,

    error = Chalk,
    onError = Ink,
    errorContainer = GraphitePressed,
    onErrorContainer = Chalk,

    scrim = Color.Black,
)

/** Opt-in OLED variant: true-black canvas for dark mode. */
private val MonoDarkOled = darkColorScheme(
    primary = Chalk,
    onPrimary = Ink,
    primaryContainer = VoidPressed,
    onPrimaryContainer = Chalk,
    inversePrimary = Ink,

    secondary = ChalkSoft,
    onSecondary = Ink,
    secondaryContainer = VoidRaised,
    onSecondaryContainer = Chalk,

    tertiary = ChalkMuted,
    onTertiary = Ink,

    background = Void,
    onBackground = Chalk,
    surface = Void,
    onSurface = Chalk,
    surfaceVariant = VoidRaised,
    onSurfaceVariant = ChalkSoft,
    surfaceTint = Chalk,

    surfaceContainerLowest = Void,
    surfaceContainerLow = Color(0xFF080808),
    surfaceContainer = VoidRaised,
    surfaceContainerHigh = Color(0xFF161616),
    surfaceContainerHighest = VoidPressed,

    inverseSurface = Chalk,
    inverseOnSurface = Ink,

    outline = LineDark,
    outlineVariant = LineFaintDark,

    error = Chalk,
    onError = Ink,
    errorContainer = VoidPressed,
    onErrorContainer = Chalk,

    scrim = Color.Black,
)

/** WhatsApp palette, light: white lists, green accents, beige conversation. */
private val WhatsAppLightScheme = lightColorScheme(
    primary = WaTeal,
    onPrimary = Color.White,
    primaryContainer = WaBubbleOutLight,
    onPrimaryContainer = WaInk,
    inversePrimary = WaTealDark,

    secondary = WaGreenDeep,
    onSecondary = Color.White,
    secondaryContainer = WaRaisedLight,
    onSecondaryContainer = WaInk,

    tertiary = WaGreen,
    onTertiary = Color.White,

    background = Color.White,
    onBackground = WaInk,
    surface = Color.White,
    onSurface = WaInk,
    surfaceVariant = WaRaisedLight,
    onSurfaceVariant = WaTextSoftLight,
    surfaceTint = WaTeal,

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = WaRaisedLight,
    surfaceContainerHigh = WaFaintLight,
    surfaceContainerHighest = WaFaintLight,

    inverseSurface = WaInk,
    inverseOnSurface = WaTextDark,

    outline = Color(0xFFD1D7DB),
    outlineVariant = WaFaintLight,

    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),

    scrim = Color.Black,
)

/** WhatsApp palette, dark: their night colors, teal-free and easy on the eyes. */
private val WhatsAppDarkScheme = darkColorScheme(
    primary = WaAccentDark,
    onPrimary = WaInk,
    primaryContainer = Color(0xFF005C4B),
    onPrimaryContainer = WaTextDark,
    inversePrimary = WaTeal,

    secondary = WaTextSoftDark,
    onSecondary = WaSurfaceDark,
    secondaryContainer = WaRaisedDark,
    onSecondaryContainer = WaTextDark,

    tertiary = WaTickDark,
    onTertiary = WaSurfaceDark,

    background = WaCanvasDark,
    onBackground = WaTextDark,
    surface = WaSurfaceDark,
    onSurface = WaTextDark,
    surfaceVariant = WaRaisedDark,
    onSurfaceVariant = WaTextSoftDark,
    surfaceTint = WaAccentDark,

    surfaceContainerLowest = WaCanvasDark,
    surfaceContainerLow = WaSurfaceDark,
    surfaceContainer = WaSurfaceDark,
    surfaceContainerHigh = WaRaisedDark,
    surfaceContainerHighest = Color(0xFF2A3942),

    inverseSurface = WaTextDark,
    inverseOnSurface = WaSurfaceDark,

    outline = Color(0xFF2A3942),
    outlineVariant = WaLineDark,

    error = Color(0xFFF15C6D),
    onError = Color(0xFF2B0A0F),
    errorContainer = Color(0xFF3B0F16),
    onErrorContainer = Color(0xFFF9DEDC),

    scrim = Color.Black,
)

/** True on dark schemes; drives the bubble and avatar tints. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** The active colour story. */
val LocalPalette = staticCompositionLocalOf { Palette.Monochrome }

/** Corner language for message bubbles. */
val LocalBubbleStyle = staticCompositionLocalOf { BubbleStyle.Rounded }

/** When true, entrance/pop animations are skipped. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** One accent tint: header, buttons, outgoing bubble and wallpaper. */
private class Accent(
    val header: Color,
    val primary: Color,
    val pale: Color,
    val canvasLight: Color,
    val canvasDark: Color,
    val bubbleDark: Color,
)

private val OceanColors = Accent(
    header = OceanHeader,
    primary = OceanPrimary,
    pale = OceanPale,
    canvasLight = OceanCanvasLight,
    canvasDark = OceanCanvasDark,
    bubbleDark = OceanBubbleDark,
)

private val BerryColors = Accent(
    header = BerryHeader,
    primary = BerryPrimary,
    pale = BerryPale,
    canvasLight = BerryCanvasLight,
    canvasDark = BerryCanvasDark,
    bubbleDark = BerryBubbleDark,
)

private val SunsetColors = Accent(
    header = SunsetHeader,
    primary = SunsetPrimary,
    pale = SunsetPale,
    canvasLight = SunsetCanvasLight,
    canvasDark = SunsetCanvasDark,
    bubbleDark = SunsetBubbleDark,
)

private val ForestColors = Accent(
    header = ForestHeader,
    primary = ForestPrimary,
    pale = ForestPale,
    canvasLight = ForestCanvasLight,
    canvasDark = ForestCanvasDark,
    bubbleDark = ForestBubbleDark,
)

private val RoseColors = Accent(
    header = RoseHeader,
    primary = RosePrimary,
    pale = RosePale,
    canvasLight = RoseCanvasLight,
    canvasDark = RoseCanvasDark,
    bubbleDark = RoseBubbleDark,
)

private val LavenderColors = Accent(
    header = LavenderHeader,
    primary = LavenderPrimary,
    pale = LavenderPale,
    canvasLight = LavenderCanvasLight,
    canvasDark = LavenderCanvasDark,
    bubbleDark = LavenderBubbleDark,
)

private val SandColors = Accent(
    header = SandHeader,
    primary = SandPrimary,
    pale = SandPale,
    canvasLight = SandCanvasLight,
    canvasDark = SandCanvasDark,
    bubbleDark = SandBubbleDark,
)

private val MidnightColors = Accent(
    header = MidnightHeader,
    primary = MidnightPrimary,
    pale = MidnightPale,
    canvasLight = MidnightCanvasLight,
    canvasDark = MidnightCanvasDark,
    bubbleDark = MidnightBubbleDark,
)

private val Accents = mapOf(
    Palette.Ocean to OceanColors,
    Palette.Berry to BerryColors,
    Palette.Sunset to SunsetColors,
    Palette.Forest to ForestColors,
    Palette.Rose to RoseColors,
    Palette.Lavender to LavenderColors,
    Palette.Sand to SandColors,
    Palette.Midnight to MidnightColors,
)

/** The dot shown in Settings for each palette. */
fun paletteSwatch(palette: Palette, dark: Boolean): Color = when (palette) {
    Palette.Monochrome -> if (dark) Chalk else Ink
    Palette.WhatsApp -> WaGreen
    Palette.Ocean -> OceanPrimary
    Palette.Berry -> BerryPrimary
    Palette.Sunset -> SunsetPrimary
    Palette.Forest -> ForestPrimary
    Palette.Rose -> RosePrimary
    Palette.Lavender -> LavenderPrimary
    Palette.Sand -> SandPrimary
    Palette.Midnight -> MidnightPrimary
}

private fun accentLightScheme(accent: Accent) = MonoLight.copy(
    primary = accent.primary,
    onPrimary = Color.White,
    primaryContainer = accent.pale,
    onPrimaryContainer = accent.header,
    inversePrimary = accent.header,
    secondary = accent.header,
    onSecondary = Color.White,
    secondaryContainer = accent.pale,
    onSecondaryContainer = accent.header,
    tertiary = accent.primary,
    onTertiary = Color.White,
    surfaceTint = accent.primary,
    // The whole app picks up a wash of the chosen colour: lists, settings and
    // the space around cards. Surfaces stay white so content keeps contrast.
    background = accent.canvasLight,
    surfaceContainerLowest = accent.canvasLight,
)

private fun accentDarkScheme(accent: Accent): ColorScheme {
    // On a dark canvas the accent must be light enough to read as a button.
    val bright = lerp(accent.primary, Color.White, 0.42f)
    return MonoDarkGray.copy(
        primary = bright,
        onPrimary = Color(0xFF0E1216),
        primaryContainer = accent.header,
        onPrimaryContainer = Color.White,
        inversePrimary = bright,
        secondary = lerp(accent.primary, Color.White, 0.28f),
        onSecondary = Color(0xFF0E1216),
        secondaryContainer = accent.bubbleDark,
        onSecondaryContainer = Color.White,
        tertiary = bright,
        onTertiary = Color(0xFF0E1216),
        surfaceTint = bright,
        background = accent.canvasDark,
        surfaceContainerLowest = accent.canvasDark,
    )
}

/**
 * Surfaces that sit outside the Material roles: the conversation wallpaper, the
 * chat header, and the tints of the bubbles themselves.
 */
object WhatscraftColors {

    private val dark: Boolean @Composable get() = LocalDarkTheme.current
    private val palette: Palette @Composable get() = LocalPalette.current

    /** Tint for the accent palettes; null for Monochrome and WhatsApp. */
    private val accent: Accent? @Composable get() = Accents[palette]

    val avatarTones: List<Color>
        @Composable get() = if (dark) AvatarTonesDark else AvatarTonesLight

    val bubbleOutgoing: Color
        @Composable get() = when {
            palette == Palette.WhatsApp -> if (dark) WaBubbleOutDark else WaBubbleOutLight
            accent != null -> if (dark) accent!!.bubbleDark else accent!!.primary
            dark -> BubbleOutDark
            else -> BubbleOutLight
        }

    val onBubbleOutgoing: Color
        @Composable get() = when {
            palette == Palette.WhatsApp -> if (dark) WaTextDark else WaTextOnOutLight
            accent != null -> Color.White
            dark -> Ink
            else -> Paper
        }

    val bubbleIncoming: Color
        @Composable get() = when {
            palette == Palette.WhatsApp -> if (dark) WaRaisedDark else WaBubbleInLight
            accent != null -> if (dark) GraphiteRaised else Color.White
            dark -> BubbleInDark
            else -> BubbleInLight
        }

    /** The conversation wallpaper behind the bubbles. */
    val threadBackground: Color
        @Composable get() = when {
            palette == Palette.WhatsApp -> if (dark) WaCanvasDark else WaCanvasLight
            accent != null -> if (dark) accent!!.canvasDark else accent!!.canvasLight
            dark -> MaterialTheme.colorScheme.background
            else -> CanvasLight
        }

    /** Chip behind "Today" / "12 March" in the conversation. */
    val threadLabel: Color
        @Composable get() = if (!dark) Color.White else MaterialTheme.colorScheme.surfaceVariant

    /** The bar at the top of a screen (and the status bar area above it). */
    val header: Color
        @Composable get() = when {
            palette == Palette.WhatsApp -> if (dark) WaRaisedDark else WaTealDark
            accent != null -> accent!!.header
            else -> MaterialTheme.colorScheme.background
        }

    val onHeader: Color
        @Composable get() = when {
            palette == Palette.WhatsApp -> if (dark) WaTextDark else Color.White
            accent != null -> Color.White
            else -> MaterialTheme.colorScheme.onBackground
        }

}

@Composable
fun WhatscraftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** OLED true-black canvas. Monochrome only; WhatsApp keeps its own night palette. */
    pureBlack: Boolean = false,
    palette: Palette = Palette.Monochrome,
    textScale: Float = 1f,
    bubbleStyle: BubbleStyle = BubbleStyle.Rounded,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = when (palette) {
        Palette.Monochrome -> when {
            !darkTheme -> MonoLight
            pureBlack -> MonoDarkOled
            else -> MonoDarkGray
        }

        Palette.WhatsApp -> if (darkTheme) WhatsAppDarkScheme else WhatsAppLightScheme

        Palette.Ocean,
        Palette.Berry,
        Palette.Sunset,
        Palette.Forest,
        Palette.Rose,
        Palette.Lavender,
        Palette.Sand,
        Palette.Midnight,
        -> {
            val accent = Accents.getValue(palette)
            if (darkTheme) accentDarkScheme(accent) else accentLightScheme(accent)
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalPalette provides palette,
        LocalBubbleStyle provides bubbleStyle,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typographyFor(textScale),
            shapes = WhatscraftShapes,
        ) {
            // The system bars follow the surface behind them; a chat screen
            // overrides this while its coloured header is on screen.
            SystemBarIcons(top = scheme.background, bottom = scheme.background)
            content()
        }
    }
}
