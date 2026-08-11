package com.evfastroute.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val EvMint = Color(0xFF5BE3DC)
internal val EvCyan = Color(0xFF59C9F3)
internal val EvIndigo = Color(0xFF5969D8)
internal val EvSuccess = Color(0xFF75E68A)
internal val EvBackgroundTop = Color(0xFF050A14)
// These two values are the exact 8-bit sRGB equivalents of AppBackground on iOS
// (0.02, 0.10, 0.13) and (0.00, 0.02, 0.04), respectively.
internal val EvBackgroundMiddle = Color(0xFF051A21)
internal val EvBackgroundBottom = Color(0xFF00050A)
// Android does not have SwiftUI's ultraThinMaterial, so these blue-black surfaces are blended
// over the shared gradient. Keeping them translucent preserves the same visual depth instead of
// turning every card and the tab bar into an opaque grey slab.
internal val EvSurface = Color(0xFF0B151C)
internal val EvSurfaceRaised = Color(0xFF13222B)
internal val EvGlass = Color(0xFF10202A)
internal val EvChrome = Color(0xFF071119)
internal val EvTextPrimary = Color(0xFFF4F8F9)
internal val EvMuted = Color(0xFFB2BEC2)
internal val EvDivider = Color(0x26FFFFFF)

private val DarkColors = darkColorScheme(
    primary = EvMint,
    onPrimary = Color(0xFF001F1E),
    primaryContainer = Color(0xFF124A4A),
    onPrimaryContainer = Color(0xFFA7F4EF),
    secondary = EvCyan,
    onSecondary = Color(0xFF001F29),
    secondaryContainer = Color(0xFF123F50),
    onSecondaryContainer = Color(0xFFC5ECFF),
    tertiary = EvIndigo,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF29346F),
    onTertiaryContainer = Color(0xFFE0E3FF),
    background = EvBackgroundTop,
    onBackground = EvTextPrimary,
    surface = EvSurface,
    onSurface = EvTextPrimary,
    surfaceVariant = EvSurfaceRaised,
    onSurfaceVariant = EvMuted,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1A1E),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF7D9296),
    outlineVariant = Color(0xFF3C4D52),
    inverseSurface = EvTextPrimary,
    inverseOnSurface = Color(0xFF152126),
    inversePrimary = Color(0xFF006A67),
    surfaceTint = EvMint,
    scrim = Color.Black,
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)

@Composable
fun EvFastRouteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content,
    )
}
