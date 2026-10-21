package org.getlume.android.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val LumeTvBaseBackground = Color(0xFF090B10)

/**
 * Direct port of Lume's TVAmbientBackground.swift.
 * A near-black #090B10 backdrop lit from the top right by a soft glow
 * in the active accent color, darkened towards the bottom so content stays legible.
 */
@Composable
fun TvAmbientBackground(
    modifier: Modifier = Modifier,
    glowTint: Color = LumeAccentBlue,
    content: @Composable () -> Unit
) {
    val animatedTint by animateColorAsState(
        targetValue = glowTint,
        animationSpec = tween(600),
        label = "ambientGlowTint"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LumeTvBaseBackground)
            .drawBehind {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // Top-right soft radial glow
                val glowCenter = Offset(canvasWidth * 0.85f, canvasHeight * 0.15f)
                val glowRadius = canvasWidth * 0.65f

                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to animatedTint.copy(alpha = 0.28f),
                            0.25f to animatedTint.copy(alpha = 0.22f),
                            0.45f to animatedTint.copy(alpha = 0.14f),
                            0.65f to animatedTint.copy(alpha = 0.06f),
                            0.85f to animatedTint.copy(alpha = 0.02f),
                            1.0f to Color.Transparent
                        ),
                        center = glowCenter,
                        radius = glowRadius
                    ),
                    size = size
                )

                // Bottom darkening gradient
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.45f to LumeTvBaseBackground.copy(alpha = 0.40f),
                            0.75f to LumeTvBaseBackground.copy(alpha = 0.85f),
                            1.0f to LumeTvBaseBackground
                        ),
                        startY = 0f,
                        endY = canvasHeight
                    ),
                    size = size
                )
            }
    ) {
        content()
    }
}
