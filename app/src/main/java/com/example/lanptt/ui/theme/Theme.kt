package com.example.lanptt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// TalkNet palette (ported from telemetry-ui).
val TalkBg = Color(0xFF0D0F13)
val TalkSurface = Color(0xFF181B22)
val TalkCard = Color(0xFF1E2230)
val TalkCard2 = Color(0xFF252A3A)
val TalkBorder = Color(0xFF2A2E3D)
val TalkMuted = Color(0xFF4A5068)
val TalkTextDim = Color(0xFF8B90A8)
val TalkText = Color(0xFFE8EAF0)
val TalkMint = Color(0xFF3EB489)
val TalkMintBright = Color(0xFF4ECF9E)

private val TalkDarkScheme = darkColorScheme(
    primary = TalkMint,
    onPrimary = TalkBg,
    background = TalkBg,
    onBackground = TalkText,
    surface = TalkSurface,
    onSurface = TalkText,
    surfaceVariant = TalkCard,
    onSurfaceVariant = TalkTextDim,
    outline = TalkBorder,
    outlineVariant = TalkBorder
)

@Composable
fun LanPttTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // TalkNet design is dark-only; fall back to default light M3 otherwise.
    if (darkTheme) {
        MaterialTheme(colorScheme = TalkDarkScheme, content = content)
    } else {
        MaterialTheme(colorScheme = lightColorScheme(), content = content)
    }
}
