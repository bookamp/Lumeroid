package org.getlume.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LumeBackground = Color(0xFF0A0A0C)
val LumeSurface = Color(0xFF141418)
val LumeSurfaceElevated = Color(0xFF1C1C22)
val LumeSurfaceHighlight = Color(0xFF202028)
val LumeBorder = Color(0x2EFFFFFF)
val LumeSurfaceBorder = LumeBorder
val LumeAccentBlue = Color(0xFF0A84FF)
val LumePrimary = LumeAccentBlue
val LumeCyan = Color(0xFF30B0C7)
val LumeLiveRed = Color(0xFFFF3B30)
val LumeWarningOrange = Color(0xFFFF9500)
val LumeTextPrimary = Color(0xFFFFFFFF)
val LumeTextSecondary = Color(0xFF8E8E93)
val LumeTextTertiary = Color(0xFF636366)

private val LumeColorScheme = darkColorScheme(
    primary = LumeAccentBlue,
    secondary = LumeCyan,
    background = LumeBackground,
    surface = LumeSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = LumeTextPrimary,
    onSurface = LumeTextPrimary,
    surfaceVariant = LumeSurfaceHighlight,
    onSurfaceVariant = LumeTextSecondary,
    outline = LumeBorder
)

@Composable
fun LumeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LumeColorScheme,
        content = content
    )
}
