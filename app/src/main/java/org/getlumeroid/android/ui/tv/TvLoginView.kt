package org.getlumeroid.android.ui.tv

import androidx.activity.compose.BackHandler
import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.getlumeroid.android.LumeroidApp
import org.getlumeroid.android.core.data.entities.PlaylistEntity
import org.getlumeroid.android.core.designsystem.*
import org.getlumeroid.android.core.sync.PlaylistSyncManager
import org.getlumeroid.android.core.network.LanSetupServer
import org.getlumeroid.android.core.network.XtreamCodesClient
import java.util.UUID

enum class LoginSourceType(val title: String) {
    XTREAM("Xtream"),
    M3U("M3U"),
    STALKER("Stalker"),
    SERVER("Server")
}

@Composable
fun TvLoginView(
    onPlaylistAdded: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    val db = LumeroidApp.instance.database
    val server = remember { LanSetupServer(database = db, onPlaylistAdded = onPlaylistAdded) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var selectedSource by remember { mutableStateOf(LoginSourceType.XTREAM) }
    var playlistName by remember { mutableStateOf("My IPTV") }
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    if (onDismiss != null) {
        BackHandler {
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        server.start()
        qrBitmap = server.generateQrBitmap(size = 512)
    }

    DisposableEffect(Unit) {
        onDispose { server.stop() }
    }

    TvAmbientBackground(glowTint = LumeroidAccentBlue) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 64.dp, end = 64.dp, top = 88.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(56.dp)
        ) {
            // Left Column: Apple TV Styled Form
            Column(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Add Playlist",
                            color = Color.White,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Connect to your IPTV provider",
                            color = LumeroidTextSecondary,
                            fontSize = 18.sp
                        )
                    }

                    if (onDismiss != null) {
                        LumeTvActionButton(
                            text = "Cancel",
                            icon = Icons.Default.Close,
                            onClick = onDismiss
                        )
                    }
                }

                // Apple-style Segmented Picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LoginSourceType.values().forEach { type ->
                        val isSelected = selectedSource == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable { selectedSource = type }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = type.title,
                                color = if (isSelected) Color.White else LumeroidTextSecondary,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Form Fields (Apple TV settings field styling)
                TvSettingsInputRow(
                    label = "NAME",
                    value = playlistName,
                    placeholder = "e.g. My IPTV",
                    onValueChange = { playlistName = it }
                )

                when (selectedSource) {
                    LoginSourceType.XTREAM -> {
                        TvSettingsInputRow(
                            label = "SERVER URL",
                            value = serverUrl,
                            placeholder = "e.g. http://example.com:8080",
                            onValueChange = { serverUrl = it }
                        )
                        TvSettingsInputRow(
                            label = "USERNAME",
                            value = username,
                            placeholder = "Username",
                            onValueChange = { username = it }
                        )
                        TvSettingsInputRow(
                            label = "PASSWORD",
                            value = password,
                            placeholder = "Password",
                            isSecure = true,
                            onValueChange = { password = it }
                        )
                    }
                    LoginSourceType.M3U -> {
                        TvSettingsInputRow(
                            label = "PLAYLIST URL",
                            value = m3uUrl,
                            placeholder = "e.g. http://example.com/playlist.m3u",
                            onValueChange = { m3uUrl = it }
                        )
                        TvSettingsInputRow(
                            label = "EPG URL (OPTIONAL)",
                            value = epgUrl,
                            placeholder = "e.g. http://example.com/guide.xml",
                            onValueChange = { epgUrl = it }
                        )
                    }
                    else -> {
                        TvSettingsInputRow(
                            label = "PORTAL URL",
                            value = serverUrl,
                            placeholder = "http://example.com:8080/c/",
                            onValueChange = { serverUrl = it }
                        )
                    }
                }

                if (errorMessage != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = LumeroidLiveRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = errorMessage ?: "",
                            color = LumeroidLiveRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Add Playlist Button
                LumeTvActionButton(
                    text = "Add Playlist",
                    icon = Icons.Default.Add,
                    isLoading = isLoading,
                    onClick = {
                        if (isLoading) return@LumeTvActionButton
                        isLoading = true
                        errorMessage = null

                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val targetUrl = if (selectedSource == LoginSourceType.XTREAM) serverUrl else m3uUrl
                                if (targetUrl.isBlank()) {
                                    withContext(Dispatchers.Main) {
                                        errorMessage = "Please enter a valid Server URL"
                                        isLoading = false
                                    }
                                    return@launch
                                }

                                val playlistId = UUID.randomUUID().toString()
                                val playlist = PlaylistEntity(
                                    id = playlistId,
                                    name = playlistName.ifBlank { "My IPTV" },
                                    type = if (selectedSource == LoginSourceType.XTREAM) "xtream" else "m3u",
                                    serverUrl = targetUrl,
                                    username = username,
                                    password = password,
                                    epgUrl = epgUrl,
                                    isActive = true
                                )

                                db.playlistDao().insertPlaylist(playlist)
                                db.playlistDao().setActivePlaylist(playlistId)

                                // Initial playlist sync: populates Live TV, Movies (with tmdbId), and Series (with tmdbId)
                                PlaylistSyncManager.syncPlaylist(playlist)

                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    onPlaylistAdded()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    errorMessage = e.localizedMessage ?: "Connection failed. Please check credentials."
                                }
                            }
                        }
                    }
                )
            }

            // Right Column: Apple-faithful Fast Phone Setup Card
            Column(
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(LumeroidCyan.copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(LumeroidCyan)
                            )
                            Text(
                                "FAST PHONE SETUP",
                                color = LumeroidCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            text = "Scan to Connect",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Avoid typing on the TV remote. Scan with your phone's camera to paste your credentials:",
                            color = LumeroidTextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        // QR Code Surface
                        qrBitmap?.let { bitmap ->
                            Box(
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .padding(12.dp)
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Setup QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Or visit in your browser:",
                                color = LumeroidTextTertiary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = server.getSetupUrl(),
                                color = LumeroidCyan,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSettingsInputRow(
    label: String,
    value: String,
    placeholder: String,
    isSecure: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            color = LumeroidTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isFocused) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f))
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) LumeroidAccentBlue else Color.White.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(12.dp)
                )
                .onFocusChanged { isFocused = it.isFocused }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 17.sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(LumeroidAccentBlue),
                visualTransformation = if (isSecure) PasswordVisualTransformation() else VisualTransformation.None,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LumeTvActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.04f else 1.0f, label = "buttonScale")

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFocused) LumeroidAccentBlue else Color.White.copy(alpha = 0.14f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.20f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
