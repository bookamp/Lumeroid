package org.getlumeroid.android.core.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * TV-optimized BringIntoViewSpec that completely eliminates jerky, jumpy row/column movements.
 *
 * In standard Android Leanback Compose, Compose automatically applies PivotBringIntoViewSpec
 * (parentFraction = 0.3f), which forcefully jolts LazyRow and LazyColumn on every single D-pad
 * press to lock the focused element at 30% of the screen (X=576px, Y=324px).
 *
 * TvSmoothBringIntoViewSpec ensures that:
 * 1. If the item is already comfortably visible within safe TV viewport margins,
 *    calculateScrollDistance returns 0f, keeping the row/column completely steady and motionless.
 * 2. Only when the user navigates past the safe margins (reaching the screen edges) does it
 *    smoothly and gently scroll to reveal the new item.
 */
@OptIn(ExperimentalFoundationApi::class)
class TvSmoothBringIntoViewSpec(
    private val horizontalMarginPx: Float = 72f,
    private val verticalTopMarginPx: Float = 140f,
    private val verticalBottomMarginPx: Float = 80f
) : BringIntoViewSpec {

    override val scrollAnimationSpec: AnimationSpec<Float> = spring(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = Spring.DampingRatioNoBouncy
    )

    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float
    ): Float {
        val isHorizontal = containerSize > 1200f
        val leadingMargin = if (isHorizontal) {
            horizontalMarginPx.coerceAtMost(containerSize * 0.15f)
        } else {
            verticalTopMarginPx.coerceAtMost(containerSize * 0.25f)
        }
        val trailingMargin = if (isHorizontal) {
            horizontalMarginPx.coerceAtMost(containerSize * 0.15f)
        } else {
            verticalBottomMarginPx.coerceAtMost(containerSize * 0.15f)
        }

        val leading = offset
        val trailing = offset + size

        // 1. If child is already fully and comfortably visible within safe TV margins, DO NOT SCROLL
        if (leading >= leadingMargin && trailing <= containerSize - trailingMargin) {
            return 0f
        }

        // 2. If child is larger than the safe viewport, align its leading edge with safe margin
        if (size >= containerSize - leadingMargin - trailingMargin) {
            return leading - leadingMargin
        }

        // 3. If trailing edge is past the right/bottom margin, scroll forward just enough
        if (trailing > containerSize - trailingMargin) {
            return trailing - (containerSize - trailingMargin)
        }

        // 4. If leading edge is past the left/top margin, scroll backward just enough
        if (leading < leadingMargin) {
            return leading - leadingMargin
        }

        return 0f
    }
}
