package com.brahim.whatscraft.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Whatscraft ships with two colour stories:
 *
 * - **Monochrome** (default): no hue anywhere, only contrast and tone.
 *   Light = paper white lists, a soft gray conversation canvas.
 *   Dark = graphite by default, with an optional OLED true-black canvas.
 * - **WhatsApp**: the familiar green accents, teal chat header and beige
 *   conversation wallpaper, with the WhatsApp dark scheme at night.
 */

// ------------------------------------------------------------------ light ---
val Ink = Color(0xFF0B0B0B)
val InkSoft = Color(0xFF3D3D3D)
val InkMuted = Color(0xFF6E6E6E)
val Paper = Color(0xFFFFFFFF)
val PaperDim = Color(0xFFF4F4F4)
val PaperPressed = Color(0xFFEAEAEA)
val LineLight = Color(0xFFC9C9C9)
val LineFaintLight = Color(0xFFE6E6E6)

/** Conversation canvas, light monochrome: gentle gray, never stark white. */
val CanvasLight = Color(0xFFEFEFEF)

// ------------------------------------------------------------------- dark ---
val Chalk = Color(0xFFF5F5F5)
val ChalkSoft = Color(0xFFC4C4C4)
val ChalkMuted = Color(0xFF8A8A8A)

/** Opt-in OLED canvas: true black, for people who want maximum contrast. */
val Void = Color(0xFF000000)
val VoidRaised = Color(0xFF101010)
val VoidPressed = Color(0xFF1C1C1C)

/** Default canvas: neutral graphite. Softer than pure black, still monochrome. */
val Graphite = Color(0xFF171717)
val GraphiteRaised = Color(0xFF212121)
val GraphitePressed = Color(0xFF2E2E2E)

val LineDark = Color(0xFF3F3F3F)
val LineFaintDark = Color(0xFF2A2A2A)

// --------------------------------------------------------------- whatsapp ---
val WaTealDark = Color(0xFF075E54)
val WaTeal = Color(0xFF128C7E)
val WaGreen = Color(0xFF25D366)
val WaGreenDeep = Color(0xFF008069)
val WaCanvasLight = Color(0xFFECE5DD)
val WaRaisedLight = Color(0xFFF0F2F5)
val WaFaintLight = Color(0xFFE9EDEF)
val WaTextSoftLight = Color(0xFF54656F)
val WaInk = Color(0xFF111B21)
val WaBubbleInLight = Color(0xFFFFFFFF)
val WaBubbleOutLight = Color(0xFFDCF8C6)
val WaTextOnOutLight = Color(0xFF111B21)

val WaCanvasDark = Color(0xFF0B141A)
val WaSurfaceDark = Color(0xFF111B21)
val WaRaisedDark = Color(0xFF202C33)
val WaBubbleOutDark = Color(0xFF005C4B)
val WaAccentDark = Color(0xFF00A884)
val WaTextDark = Color(0xFFE9EDEF)
val WaTextSoftDark = Color(0xFF8696A0)
val WaLineDark = Color(0xFF222D34)
val WaTickDark = Color(0xFF53BDEB)

// --------------------------------------------------------- accent stories ---
// Ocean (blue), Berry (violet) and Sunset (orange) reuse the monochrome
// structure and only swap the accents, the bubbles and the wallpaper tint.

val OceanHeader = Color(0xFF14508F)
val OceanPrimary = Color(0xFF1E6FD9)
val OceanPale = Color(0xFFD8E7FB)
val OceanCanvasLight = Color(0xFFEDF3FB)
val OceanCanvasDark = Color(0xFF0D1420)
val OceanBubbleDark = Color(0xFF1B4F9E)

val BerryHeader = Color(0xFF4F2B96)
val BerryPrimary = Color(0xFF6D48D0)
val BerryPale = Color(0xFFE6DEF9)
val BerryCanvasLight = Color(0xFFF3F0FB)
val BerryCanvasDark = Color(0xFF120F1D)
val BerryBubbleDark = Color(0xFF4A2F97)

val SunsetHeader = Color(0xFF9A3F0E)
val SunsetPrimary = Color(0xFFC55A0F)
val SunsetPale = Color(0xFFFBE3D1)
val SunsetCanvasLight = Color(0xFFFCF0E8)
val SunsetCanvasDark = Color(0xFF1B1310)
val SunsetBubbleDark = Color(0xFF8A3C0A)

val ForestHeader = Color(0xFF14532D)
val ForestPrimary = Color(0xFF1E7A45)
val ForestPale = Color(0xFFD7F0DF)
val ForestCanvasLight = Color(0xFFEDF7F0)
val ForestCanvasDark = Color(0xFF0C1711)
val ForestBubbleDark = Color(0xFF1B5E38)

val RoseHeader = Color(0xFF9D174D)
val RosePrimary = Color(0xFFDB2777)
val RosePale = Color(0xFFFCE0EC)
val RoseCanvasLight = Color(0xFFFDF0F5)
val RoseCanvasDark = Color(0xFF1C0F15)
val RoseBubbleDark = Color(0xFF9D174D)

val LavenderHeader = Color(0xFF6D28D9)
val LavenderPrimary = Color(0xFF8B5CF6)
val LavenderPale = Color(0xFFEDE6FD)
val LavenderCanvasLight = Color(0xFFF6F2FD)
val LavenderCanvasDark = Color(0xFF141020)
val LavenderBubbleDark = Color(0xFF5B21B6)

val SandHeader = Color(0xFF8A5A16)
val SandPrimary = Color(0xFFB07A2A)
val SandPale = Color(0xFFF6E9D2)
val SandCanvasLight = Color(0xFFFBF5EA)
val SandCanvasDark = Color(0xFF1A150E)
val SandBubbleDark = Color(0xFF7A4E12)

val MidnightHeader = Color(0xFF1E1B4B)
val MidnightPrimary = Color(0xFF4338CA)
val MidnightPale = Color(0xFFE0DEFB)
val MidnightCanvasLight = Color(0xFFEFF0FB)
val MidnightCanvasDark = Color(0xFF0B0B16)
val MidnightBubbleDark = Color(0xFF312E81)

// ----------------------------------------------------------------- bubble ---
/** Bubble tints: the outgoing side is always the opposite tone of the surface. */
val BubbleOutLight = Color(0xFF0B0B0B)
val BubbleOutDark = Color(0xFFF5F5F5)
val BubbleInLight = Color(0xFFFFFFFF)
val BubbleInDark = Color(0xFF242424)

/** Eight monochrome avatar tones, used as a deterministic identity shade. */
val AvatarTonesLight = listOf(
    Color(0xFFE8E8E8), Color(0xFFDCDCDC), Color(0xFFCFCFCF), Color(0xFFC2C2C2),
    Color(0xFFB5B5B5), Color(0xFFA8A8A8), Color(0xFF9B9B9B), Color(0xFF8E8E8E),
)
val AvatarTonesDark = listOf(
    Color(0xFF232323), Color(0xFF2A2A2A), Color(0xFF313131), Color(0xFF383838),
    Color(0xFF3F3F3F), Color(0xFF464646), Color(0xFF4D4D4D), Color(0xFF545454),
)
