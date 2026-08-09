package com.evfastroute.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B4D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA4F2CC),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF466554),
    secondaryContainer = Color(0xFFC8EBD5),
    surface = Color(0xFFF7FBF8),
    surfaceVariant = Color(0xFFDCE5DE),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF88D6B1),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFA4F2CC),
    secondary = Color(0xFFACCFBA),
    secondaryContainer = Color(0xFF2F4D3D),
    surface = Color(0xFF101512),
    surfaceVariant = Color(0xFF404943),
    error = Color(0xFFFFB4AB),
)

@Composable
fun EvFastRouteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
