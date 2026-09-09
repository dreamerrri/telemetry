package com.example.lanptt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
val TalkAmber = Color(0xFFE9C46A)
val TalkRose = Color(0xFFFF6584)

private val TalkDarkScheme = darkColorScheme(
    primary = TalkMint,
    onPrimary = TalkBg,
    primaryContainer = TalkMint.copy(alpha = 0.22f),
    onPrimaryContainer = TalkMintBright,
    secondary = TalkTextDim,
    onSecondary = TalkBg,
    secondaryContainer = TalkCard2,
    onSecondaryContainer = TalkText,
    tertiary = TalkAmber,
    onTertiary = TalkBg,
    tertiaryContainer = TalkAmber.copy(alpha = 0.18f),
    onTertiaryContainer = TalkAmber,
    error = TalkRose,
    onError = TalkBg,
    background = TalkBg,
    onBackground = TalkText,
    surface = TalkSurface,
    onSurface = TalkText,
    surfaceVariant = TalkCard,
    onSurfaceVariant = TalkTextDim,
    surfaceContainerLowest = TalkBg,
    surfaceContainerLow = TalkSurface,
    surfaceContainer = TalkCard,
    surfaceContainerHigh = TalkCard2,
    surfaceContainerHighest = TalkBorder,
    outline = TalkBorder,
    outlineVariant = TalkBorder
)

/**
 * M3 Expressive theme for the TalkNet design: expressive motion scheme (springy
 * emphasized spatial/effects specs), expressive rounded shapes, and the full
 * TalkNet dark color scheme. Custom brand colors stay untouched so the app keeps
 * its identity; components just pick up expressive motion + shapes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LanPttTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // TalkNet design is dark-only; fall back to default light M3 otherwise.
    if (darkTheme) {
        MaterialTheme(
            colorScheme = TalkDarkScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = MaterialTheme.shapes.copy(
                extraSmall = RoundedCornerShape(10.dp),
                small = RoundedCornerShape(14.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(28.dp),
                extraLarge = RoundedCornerShape(36.dp)
            ),
            content = content
        )
    } else {
        MaterialTheme(colorScheme = lightColorScheme(), content = content)
    }
}
