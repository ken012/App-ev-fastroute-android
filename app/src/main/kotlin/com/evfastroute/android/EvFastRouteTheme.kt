package com.evfastroute.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val EvMint = Color(0xFF5BE3DC)
internal val EvCyan = Color(0xFF59C9F3)
internal val EvBackgroundTop = Color(0xFF050A14)
internal val EvBackgroundMiddle = Color(0xFF061C24)
internal val EvBackgroundBottom = Color(0xFF000509)
internal val EvGlass = Color(0xFF1C2225)
internal val EvGlassStrong = Color(0xFF23292C)
internal val EvMuted = Color(0xFF9AA2AA)
internal val EvDivider = Color(0x337D9296)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B68),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8FF3ED),
    onPrimaryContainer = Color(0xFF00201F),
    secondary = Color(0xFF006783),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBCE9FF),
    onSecondaryContainer = Color(0xFF001F2A),
    background = Color(0xFFF4FAFA),
    onBackground = Color(0xFF101718),
    surface = Color(0xFFF7FBFB),
    onSurface = Color(0xFF101718),
    surfaceVariant = Color(0xFFE4ECEC),
    onSurfaceVariant = Color(0xFF526063),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = EvMint,
    onPrimary = Color(0xFF001F1E),
    primaryContainer = Color(0xFF124A4A),
    onPrimaryContainer = Color(0xFFA7F4EF),
    secondary = EvCyan,
    onSecondary = Color(0xFF001F29),
    secondaryContainer = Color(0xFF123F50),
    onSecondaryContainer = Color(0xFFC5ECFF),
    background = EvBackgroundTop,
    onBackground = Color(0xFFF5F7F8),
    surface = Color(0xFF101512),
    onSurface = Color(0xFFF5F7F8),
    surfaceVariant = EvGlassStrong,
    onSurfaceVariant = EvMuted,
    error = Color(0xFFFFB4AB),
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
fun EvFastRouteTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
