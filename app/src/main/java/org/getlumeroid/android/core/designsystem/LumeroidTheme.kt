package org.getlumeroid.android.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

val LumeroidBackground = Color(0xFF0A0A0C)
val LumeroidSurface = Color(0xFF141418)
val LumeroidSurfaceElevated = Color(0xFF1C1C22)
val LumeroidSurfaceHighlight = Color(0xFF202028)
val LumeroidBorder = Color(0x2EFFFFFF)
val LumeroidSurfaceBorder = LumeroidBorder
val LumeroidAccentBlue = Color(0xFF0A84FF)
val LumeroidPrimary = LumeroidAccentBlue
val LumeroidCyan = Color(0xFF30B0C7)
val LumeroidLiveRed = Color(0xFFFF3B30)
val LumeroidWarningOrange = Color(0xFFFF9500)
val LumeroidTextPrimary = Color(0xFFFFFFFF)
val LumeroidTextSecondary = Color(0xFF8E8E93)
val LumeroidTextTertiary = Color(0xFF636366)

private val LumeroidColorScheme = darkColorScheme(
    primary = LumeroidAccentBlue,
    secondary = LumeroidCyan,
    background = LumeroidBackground,
    surface = LumeroidSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = LumeroidTextPrimary,
    onSurface = LumeroidTextPrimary,
    surfaceVariant = LumeroidSurfaceHighlight,
    onSurfaceVariant = LumeroidTextSecondary,
    outline = LumeroidBorder
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LumeroidTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides remember { TvSmoothBringIntoViewSpec() }
    ) {
        MaterialTheme(
            colorScheme = LumeroidColorScheme,
            content = content
        )
    }
}
