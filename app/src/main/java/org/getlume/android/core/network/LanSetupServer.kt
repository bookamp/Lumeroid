package org.getlume.android.core.network

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.getlume.android.core.data.LumeDatabase
import org.getlume.android.core.data.entities.PlaylistEntity
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.TimeUnit

class LanSetupServer(
    private val database: LumeDatabase,
    private val onPlaylistAdded: () -> Unit = {}
) {
    private var server: ApplicationEngine? = null
    val port = 8095

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        return "127.0.0.1"
    }

    fun getSetupUrl(): String {
        return "http://${getLocalIpAddress()}:$port"
    }

    fun generateQrBitmap(size: Int = 512): Bitmap? {
        return generateQrBitmap(getSetupUrl(), size)
    }

    fun start() {
        if (server != null) return
        server = embeddedServer(CIO, port = port) {
            routing {
                get("/") {
                    call.respondText(
                        HTML_PAGE,
                        ContentType.Text.Html
                    )
                }
                post("/api/add-playlist") {
                    val params = call.receiveParameters()
                    val type = params["type"] ?: "xtream"
                    val name = params["name"] ?: "My Playlist"
                    val serverUrl = params["serverUrl"] ?: ""
                    val username = params["username"] ?: ""
                    val password = params["password"] ?: ""
                    val epgUrl = params["epgUrl"] ?: ""

                    if (serverUrl.isNotEmpty()) {
                        val playlistId = UUID.randomUUID().toString()
                        val playlist = PlaylistEntity(
                            id = playlistId,
                            name = name,
                            type = type,
                            serverUrl = serverUrl,
                            username = username,
                            password = password,
                            epgUrl = epgUrl,
                            isActive = true
                        )

                        CoroutineScope(Dispatchers.IO).launch {
                            database.playlistDao().insertPlaylist(playlist)
                            database.playlistDao().setActivePlaylist(playlistId)

                            // Initial sync
                            if (type == "xtream") {
                                val client = XtreamCodesClient(serverUrl, username, password)
                                val cats = client.fetchLiveCategories(playlistId)
                                val streams = client.fetchLiveStreams(playlistId)
                                database.categoryDao().insertCategories(cats)
                                database.channelDao().insertChannels(streams)
                            }
                            onPlaylistAdded()
                        }

                        call.respondText(
                            "<h3>Success!</h3><p>Playlist '$name' added to your TV. You can close this page.</p>",
                            ContentType.Text.Html
                        )
                    } else {
                        call.respondText("Missing server URL", status = HttpStatusCode.BadRequest)
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000L, 2000L)
        server = null
    }

    companion object {
        fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
            return try {
                val writer = QRCodeWriter()
                val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
                for (x in 0 until size) {
                    for (y in 0 until size) {
                        bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                    }
                }
                bitmap
            } catch (e: Exception) {
                null
            }
        }

        private const val HTML_PAGE = """
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Lume TV Setup</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0A0A0C; color: #FFF; margin: 0; padding: 20px; }
        .container { max-width: 460px; margin: 40px auto; background: #141418; padding: 24px; border-radius: 16px; border: 1px solid #202028; }
        h1 { font-size: 24px; margin-bottom: 8px; color: #0A84FF; }
        p { color: #8E8E93; font-size: 14px; margin-bottom: 24px; }
        label { display: block; margin-top: 16px; font-weight: 600; font-size: 14px; }
        input, select { width: 100%; box-sizing: border-box; padding: 12px; margin-top: 6px; border-radius: 8px; border: 1px solid #2C2C34; background: #1C1C22; color: #FFF; font-size: 15px; }
        input:focus { border-color: #0A84FF; outline: none; }
        button { width: 100%; margin-top: 24px; padding: 14px; background: #0A84FF; border: none; border-radius: 10px; color: #FFF; font-weight: 700; font-size: 16px; cursor: pointer; }
        button:hover { background: #0070E0; }
    </style>
</head>
<body>
<div class="container">
    <h1>Lume TV Setup</h1>
    <p>Paste your IPTV credentials below to instantly connect your TV.</p>
    <form action="/api/add-playlist" method="POST">
        <label>Playlist Name</label>
        <input type="text" name="name" value="My IPTV" required>

        <label>Type</label>
        <select name="type">
            <option value="xtream">Xtream Codes API</option>
            <option value="m3u">M3U / M3U8 Playlist URL</option>
        </select>

        <label>Server URL / M3U URL</label>
        <input type="url" name="serverUrl" placeholder="http://provider.example.com:8080" required>

        <label>Username (Xtream)</label>
        <input type="text" name="username">

        <label>Password (Xtream)</label>
        <input type="password" name="password">

        <label>EPG URL (Optional)</label>
        <input type="url" name="epgUrl" placeholder="http://epg.example.com/epg.xml.gz">

        <button type="submit">Save to TV</button>
    </form>
</div>
</body>
</html>
        """
    }
}
