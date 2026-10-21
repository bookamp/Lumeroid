package org.getlume.android.ui.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.getlume.android.ui.navigation.Screen

import androidx.compose.ui.input.key.*

import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

import androidx.compose.ui.focus.focusProperties

/**
 * Authentic Apple TV floating top pill navigation bar.
 * Exact 1:1 match to tvos-home.webp from original Lume.
 * Items: [ 🔍  Home  Movies  Series  Live TV  Sports  ⚙ ]
 */
@Composable
fun TvTopBar(
    currentScreen: Screen,
    isTopBarFocused: Boolean = false,
    onSelectScreen: (Screen) -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onMoveDown: (() -> Boolean)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val onBackFromTopBar: (() -> Boolean)? = if (currentScreen != Screen.Home) {
        {
            onSelectScreen(Screen.Home)
            true
        }
    } else null

    LaunchedEffect(isTopBarFocused) {
        if (isTopBarFocused) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier
                .onFocusChanged { focusState ->
                    onFocusChanged?.invoke(focusState.hasFocus)
                }
                .clip(RoundedCornerShape(32.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(32.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Search Icon Tab
            TvTopBarIconItem(
                icon = Icons.Default.Search,
                contentDescription = "Search",
                isSelected = currentScreen == Screen.Search,
                focusRequester = if (currentScreen == Screen.Search) focusRequester else null,
                canFocus = isTopBarFocused,
                onClick = onOpenSearch,
                onMoveDown = onMoveDown,
                onBack = onBackFromTopBar
            )

            // Text Tabs
            TvTopBarTextItem(
                title = "Home",
                isSelected = currentScreen == Screen.Home,
                focusRequester = if (currentScreen == Screen.Home) focusRequester else null,
                canFocus = isTopBarFocused,
                onClick = { onSelectScreen(Screen.Home) },
                onMoveDown = onMoveDown,
                onBack = onBackFromTopBar
            )

            TvTopBarTextItem(
                title = "Movies",
                isSelected = currentScreen == Screen.Movies,
                focusRequester = if (currentScreen == Screen.Movies) focusRequester else null,
                canFocus = isTopBarFocused,
                onClick = { onSelectScreen(Screen.Movies) },
                onMoveDown = onMoveDown,
                onBack = onBackFromTopBar
            )

            TvTopBarTextItem(
                title = "Series",
                isSelected = currentScreen == Screen.Series,
                focusRequester = if (currentScreen == Screen.Series) focusRequester else null,
                canFocus = isTopBarFocused,
                onClick = { onSelectScreen(Screen.Series) },
                onMoveDown = onMoveDown,
                onBack = onBackFromTopBar
            )

            TvTopBarTextItem(
                title = "Live TV",
                isSelected = currentScreen == Screen.LiveTv,
                focusRequester = if (currentScreen == Screen.LiveTv) focusRequester else null,
                canFocus = isTopBarFocused,
                onClick = { onSelectScreen(Screen.LiveTv) },
                onMoveDown = onMoveDown,
                onBack = onBackFromTopBar
            )

            TvTopBarTextItem(
                title = "Sports",
                isSelected = currentScreen == Screen.Sports,
                focusRequester = if (currentScreen == Screen.Sports) focusRequester else null,
                canFocus = isTopBarFocused,
                onClick = { onSelectScreen(Screen.Sports) },
                onMoveDown = onMoveDown,
                onBack = onBackFromTopBar
            )

            // Settings Gear Icon Tab
            TvTopBarIconItem(
                icon = Icons.Default.Settings,
                contentDescription = "Settings",
                isSelected = currentScreen == Screen.Settings,
                focusRequester = if (currentScreen == Screen.Settings) focusRequester else null,
                canFocus = isTopBarFocused,
                onClick = onOpenSettings,
                onMoveDown = onMoveDown,
                onBack = onBackFromTopBar
            )
        }
    }
}

@Composable
private fun TvTopBarTextItem(
    title: String,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    canFocus: Boolean = true,
    onClick: () -> Unit,
    onMoveDown: (() -> Boolean)? = null,
    onBack: (() -> Boolean)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "tabScale")

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isSelected -> Color.White.copy(alpha = 0.22f)
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "tabBg"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.Black
            isSelected -> Color.White
            else -> Color.White.copy(alpha = 0.75f)
        },
        animationSpec = tween(150),
        label = "tabText"
    )

    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Box(
        modifier = Modifier
            .focusProperties { this.canFocus = canFocus }
            .then(focusModifier)
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_ENTER,
                        android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            onClick()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            onMoveDown?.invoke() ?: false
                        }
                        android.view.KeyEvent.KEYCODE_BACK -> {
                            onBack?.invoke() ?: false
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun TvTopBarIconItem(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    canFocus: Boolean = true,
    onClick: () -> Unit,
    onMoveDown: (() -> Boolean)? = null,
    onBack: (() -> Boolean)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.10f else 1.0f, label = "iconScale")

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White
            isSelected -> Color.White.copy(alpha = 0.22f)
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "iconBg"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.Black
            isSelected -> Color.White
            else -> Color.White.copy(alpha = 0.75f)
        },
        animationSpec = tween(150),
        label = "iconColor"
    )

    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Box(
        modifier = Modifier
            .focusProperties { this.canFocus = canFocus }
            .then(focusModifier)
            .scale(scale)
            .size(36.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_ENTER,
                        android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            onClick()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            onMoveDown?.invoke() ?: false
                        }
                        android.view.KeyEvent.KEYCODE_BACK -> {
                            onBack?.invoke() ?: false
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
    }
}
