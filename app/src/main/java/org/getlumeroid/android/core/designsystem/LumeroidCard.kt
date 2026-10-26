package org.getlumeroid.android.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LumeroidCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    focusedScale: Float = 1.05f,
    elevation: Dp = 4.dp,
    containerColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) focusedScale else 1.0f, label = "cardScale")

    val border = if (isFocused) {
        BorderStroke(2.5.dp, LumeroidAccentBlue)
    } else {
        BorderStroke(1.dp, LumeroidBorder)
    }

    val surfaceColor = containerColor ?: if (isFocused) LumeroidSurfaceHighlight else LumeroidSurface

    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Surface(
        modifier = modifier
            .then(focusModifier)
            .scale(scale)
            .shadow(if (isFocused) elevation * 2 else elevation, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = shape,
        color = surfaceColor,
        border = border
    ) {
        Box(content = content)
    }
}
